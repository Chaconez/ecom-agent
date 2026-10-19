package com.ecom.cs.agent.intent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link IntentRuleMatcher} L1 规则命中率与精度测试。
 *
 * <p>用例全部取自电商客服真实口语化表达，并对齐
 * {@code IntentRouterKnowledgeConfig} 的 L2 种子语料。测试分三类：</p>
 * <ul>
 *   <li>expectMatch：清晰模板化表达，L1 应命中且分到正确意图类别；</li>
 *   <li>expectAgent：只关心路由结果的表达（如指代式下单"帮我订这个"），只校验落到正确的
 *       目标子智能体即可，具体分到组内哪个意图类别不影响路由；</li>
 *   <li>expectEmpty：信息不足/开放式问法，L1 应保守放行（返回 empty），交给 L2/L3。</li>
 * </ul>
 *
 * <p>测试收集全部失败用例后一次性断言，便于查看整体命中情况。</p>
 *
 * @author Hollis
 */
@DisplayName("IntentRuleMatcher L1 规则命中率测试")
class IntentRuleMatcherTest {

    private final IntentRuleMatcher matcher = new IntentRuleMatcher();

    @Test
    @DisplayName("清晰表达应命中正确意图类别")
    void shouldHitExpectedCategory() {
        List<Case> cases = new ArrayList<>();

        // —— 寒暄 GREETING ——
        expect(cases, IntentCategory.GREETING, "你好", "您好", "在吗？", "hi", "hello",
                "早上好", "下午好，请问在吗？", "嗨~");

        // —— 售后/退换货/发票 AFTER_SALE ——
        expect(cases, IntentCategory.AFTER_SALE,
                "我要退货", "帮我退款", "这个能换货吗", "发票怎么开",
                "东西坏了想保修", "帮我申请售后");

        // —— 工单查询 TICKET_QUERY ——
        expect(cases, IntentCategory.TICKET_QUERY,
                "工单进度怎么样", "我的投诉有结果了吗", "查一下工单状态",
                "投诉单什么状态", "工单到哪一步了");

        // —— 工单取消 TICKET_CANCEL ——
        expect(cases, IntentCategory.TICKET_CANCEL,
                "取消工单", "撤回投诉", "把那个投诉撤了", "这个工单作废吧");

        // —— 工单修改 TICKET_MODIFY ——
        expect(cases, IntentCategory.TICKET_MODIFY,
                "修改工单内容", "补充一下投诉信息", "改一下工单描述");

        // —— 提交工单/投诉 TICKET_SUBMIT ——
        expect(cases, IntentCategory.TICKET_SUBMIT,
                "我要投诉", "转人工", "找人工客服", "举报这家店", "客服态度太差了");

        // —— 订单查询 ORDER_QUERY ——
        expect(cases, IntentCategory.ORDER_QUERY,
                "我的订单呢", "查一下订单状态", "物流到哪了",
                "发货了吗", "我买过的东西", "待收货的有几个");

        // —— 取消订单 ORDER_CANCEL ——
        expect(cases, IntentCategory.ORDER_CANCEL,
                "取消订单", "这个订单不要了", "帮我把订单退了", "把这个退掉");

        // —— 下单 ORDER_CREATE ——
        expect(cases, IntentCategory.ORDER_CREATE,
                "下单", "我要买这个", "来一件", "加入购物车", "帮我买一下");

        // —— 商品咨询 PRODUCT_CONSULT ——
        expect(cases, IntentCategory.PRODUCT_CONSULT,
                "这个多少钱", "有优惠吗", "什么材质的", "包邮吗",
                "尺码怎么选", "值得买吗");

        // —— 跨类边界陷阱（分错组会导致路由到错误子智能体）——
        // "值得买吗"是咨询不是下单，不得被下单规则抢占
        expect(cases, IntentCategory.PRODUCT_CONSULT, "这款值得买吗");
        // 已发货要退的走售后，不得被"取消订单"抢占
        expect(cases, IntentCategory.AFTER_SALE, "退款", "我要退货退款");

        runCategoryCases(cases);
    }

    @Test
    @DisplayName("分组内歧义只需命中正确目标子智能体")
    void shouldHitExpectedTargetAgent() {
        List<AgentCase> cases = new ArrayList<>();

        // 指代式下单等表达统一归 ORDER_CREATE → BookingAgent
        expectAgent(cases, "BookingAgent",
                "帮我订这个", "就这个，帮我买下来", "来两件", "查下我的订单");

        // 各类工单操作同属 ItineraryManageAgent
        expectAgent(cases, "ItineraryManageAgent", "我要投诉你们", "帮我查下工单");

        runAgentCases(cases);
    }

    @Test
    @DisplayName("信息不足的开放式问法 L1 应保守放行")
    void shouldDeferAmbiguousToLowerLayers() {
        List<String> shouldBeEmpty = List.of(
                "随便问问", "你能做什么", "都能干些啥", "帮助", "这个怎么说",
                "帮我处理一下", "这个怎么用",
                // 政策/规则类问法不属于售后办理，放行给 L3 结合上下文裁定
                "帮我看看这个商品能不能退",
                "退款规则是什么",
                "多久能退到账");

        List<String> failures = new ArrayList<>();
        for (String text : shouldBeEmpty) {
            Optional<IntentRecognitionResult> result = matcher.match(text);
            if (result.isPresent()) {
                failures.add(String.format("「%s」期望 L1 放行(empty)，实际命中=%s",
                        text, result.get().getPrimary()));
            }
        }
        assertTrue(failures.isEmpty(), buildReport("过度命中(应放行却命中)", failures));
    }

    @Test
    @DisplayName("跨子智能体的多意图复合句应判为 AMBIGUOUS 放行")
    void shouldFlagCrossAgentCompoundAsAmbiguous() {
        // 各子句分别命中不同目标子智能体的意图，L1 不应短路成单意图
        List<String> compound = List.of(
                // ORDER_QUERY(Booking) + AFTER_SALE(Reimbursement)
                "查一下我的订单，顺便问下能不能退款",
                // ORDER_CANCEL(Booking) + TICKET_SUBMIT(Manage)
                "帮我取消订单，再帮我投诉一下",
                // TICKET_QUERY(Manage) + ORDER_QUERY(Booking)
                "查下工单进度，顺便看看订单到哪了");

        List<String> failures = new ArrayList<>();
        for (String text : compound) {
            IntentRuleMatcher.Outcome outcome = matcher.evaluate(text);
            if (outcome.verdict() != IntentRuleMatcher.Verdict.AMBIGUOUS) {
                failures.add(String.format("「%s」期望=AMBIGUOUS，实际=%s(%s)",
                        text, outcome.verdict(),
                        outcome.result() == null ? outcome.ambiguousCategories() : outcome.result().getPrimary()));
            }
        }
        assertTrue(failures.isEmpty(), buildReport("复合句未被多意图守卫拦截", failures));
    }

    @Test
    @DisplayName("同一子智能体内的复合句仍可短路（组内由同一 Agent 处理）")
    void shouldStillHitWhenCompoundStaysInSameAgent() {
        // 订单 + 物流同属 BookingAgent，短路后子智能体可在一次会话内处理两件事
        IntentRuleMatcher.Outcome outcome = matcher.evaluate("查下订单和物流");
        assertTrue(outcome.verdict() == IntentRuleMatcher.Verdict.HIT,
                "「查下订单和物流」期望 HIT，实际=" + outcome.verdict());
        assertTrue("BookingAgent".equals(outcome.result().getPrimary().getDefaultTargetAgent()),
                "目标子智能体应为 BookingAgent，实际=" + outcome.result().getPrimary().getDefaultTargetAgent());

        // 寒暄 + 单任务是典型单意图，寒暄不参与歧义判定
        IntentRuleMatcher.Outcome greeting = matcher.evaluate("你好，帮我查下订单");
        assertTrue(greeting.verdict() == IntentRuleMatcher.Verdict.HIT
                        && greeting.result().getPrimary() == IntentCategory.ORDER_QUERY,
                "「你好，帮我查下订单」期望 HIT(ORDER_QUERY)，实际=" + greeting.verdict());
    }

    // ----------------------------------------------------------------------
    // helpers
    // ----------------------------------------------------------------------

    private void runCategoryCases(List<Case> cases) {
        List<String> failures = new ArrayList<>();
        for (Case c : cases) {
            Optional<IntentRecognitionResult> result = matcher.match(c.text);
            if (result.isEmpty()) {
                failures.add(String.format("「%s」期望=%s，实际=未命中(L1 miss)", c.text, c.expected));
            } else if (result.get().getPrimary() != c.expected) {
                failures.add(String.format("「%s」期望=%s，实际=%s",
                        c.text, c.expected, result.get().getPrimary()));
            }
        }
        assertTrue(failures.isEmpty(), buildReport("意图类别不匹配", failures));
    }

    private void runAgentCases(List<AgentCase> cases) {
        List<String> failures = new ArrayList<>();
        for (AgentCase c : cases) {
            Optional<IntentRecognitionResult> result = matcher.match(c.text);
            if (result.isEmpty()) {
                failures.add(String.format("「%s」期望目标=%s，实际=未命中(L1 miss)", c.text, c.expectedAgent));
            } else {
                String actual = result.get().getPrimary().getDefaultTargetAgent();
                if (!c.expectedAgent.equals(actual)) {
                    failures.add(String.format("「%s」期望目标=%s，实际=%s(%s)",
                            c.text, c.expectedAgent, actual, result.get().getPrimary()));
                }
            }
        }
        assertTrue(failures.isEmpty(), buildReport("目标子智能体不匹配", failures));
    }

    private static void expect(List<Case> cases, IntentCategory expected, String... texts) {
        for (String text : texts) {
            cases.add(new Case(text, expected));
        }
    }

    private static void expectAgent(List<AgentCase> cases, String expectedAgent, String... texts) {
        for (String text : texts) {
            cases.add(new AgentCase(text, expectedAgent));
        }
    }

    private static String buildReport(String title, List<String> failures) {
        StringBuilder sb = new StringBuilder(title + "，共 " + failures.size() + " 条失败：\n");
        for (String f : failures) {
            sb.append("  - ").append(f).append('\n');
        }
        return sb.toString();
    }

    private record Case(String text, IntentCategory expected) {
    }

    private record AgentCase(String text, String expectedAgent) {
    }
}
