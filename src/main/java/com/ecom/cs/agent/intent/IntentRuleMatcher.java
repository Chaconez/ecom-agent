package com.ecom.cs.agent.intent;

import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Pattern;

/**
 * L1 规则/关键词匹配器。
 *
 * <p>针对意图清晰、表达高度模板化的高频场景（寒暄、售后、订单查询、投诉等），
 * 在内存中做关键词/正则匹配，命中后立即返回 {@link IntentRecognitionResult}，
 * 目标延迟 &lt; 50ms。</p>
 *
 * <p>求值策略：对全部规则求值后再裁决（而非首中即返），以便识别"多类命中"——
 * 当命中的规则类别横跨 <b>两个及以上目标子智能体</b> 时，说明输入大概率是多意图复合句
 * （如"查一下订单，顺便问下能不能退款"），L1 保守放行（{@link Verdict#AMBIGUOUS}），
 * 交给 L3 LLM 判定 {@code multi_intent}；同一目标子智能体内的多类命中仍按规则优先级
 * （List 顺序）取首个，因为组内误判不影响路由，且规则顺序本身编码了消歧语义。</p>
 *
 * <p>注意：L1 应当保守，宁可漏判也不要误判；不确定时直接放行让 L2/L3 处理。</p>
 *
 * @author Hollis
 */
@Component
public class IntentRuleMatcher {

    /**
     * L1 匹配裁决：单类命中 / 多类歧义（疑似多意图）/ 未命中。
     */
    public enum Verdict {
        /**
         * 单类命中（或多类命中但同属一个目标子智能体），可安全短路。
         */
        HIT,
        /**
         * 子句级多类命中且横跨多个目标子智能体，疑似多意图复合句，应放行 L3（并跳过 L2）。
         */
        AMBIGUOUS,
        /**
         * 未命中任何规则，交给 L2/L3。
         */
        MISS
    }

    /**
     * L1 匹配结局。{@code result} 仅在 {@link Verdict#HIT} 时非空；
     * {@code ambiguousCategories} 仅在 {@link Verdict#AMBIGUOUS} 时非空，用于日志排障。
     */
    public record Outcome(Verdict verdict, IntentRecognitionResult result, List<IntentCategory> ambiguousCategories) {

        static Outcome hit(IntentRecognitionResult result) {
            return new Outcome(Verdict.HIT, result, List.of());
        }

        static Outcome ambiguous(List<IntentCategory> categories) {
            return new Outcome(Verdict.AMBIGUOUS, null, categories);
        }

        static Outcome miss() {
            return new Outcome(Verdict.MISS, null, List.of());
        }
    }

    /**
     * 多意图信号较强的并列/顺承连词（多字、歧义小）。
     * 与 {@link IntentRecognitionRouter} 的 L0 结构启发共用同一张表，避免两处维护。
     */
    static final String STRONG_CONJUNCTIONS =
            "然后|接着|顺便|顺带|以及|并且|另外|同时|完了再|之后再|再帮我|再给我|外加";

    /**
     * 子句切分符：标点 + 强连词 + 弱连接词。用于多意图守卫时把复合句拆成独立子句分别匹配。
     * 弱连接词（还要/再查/和/跟等）只用于切分、不作为 L0 信号——单独出现歧义大，
     * 误切分的碎片子句通常匹配不到规则，代价可控（"与"易误伤"参与"等词，不收录）。
     */
    private static final Pattern CLAUSE_SPLITTER = Pattern.compile(
            "[，。；！？!?;,、]|" + STRONG_CONJUNCTIONS + "|还要|还想|再帮|再给|再查|再订|再看|和|跟");

    /**
     * 单条规则：keyword 任一命中且不含 negativeKeyword 即视为命中。
     */
    private static final class Rule {
        final IntentCategory category;
        final Pattern keyword;
        final List<Pattern> negativeKeywords;

        Rule(IntentCategory category, String keyword, List<String> negativeKeywords) {
            this.category = category;
            this.keyword = compile(keyword);
            this.negativeKeywords = negativeKeywords == null
                    ? List.of()
                    : negativeKeywords.stream().map(IntentRuleMatcher::compile).toList();
        }

        boolean matches(String text) {
            if (!keyword.matcher(text).find()) {
                return false;
            }
            for (Pattern negative : negativeKeywords) {
                if (negative.matcher(text).find()) {
                    return false;
                }
            }
            return true;
        }
    }

    private final List<Rule> rules = new ArrayList<>();

    public IntentRuleMatcher() {
        // 优先级从高到低（List 顺序即优先级，命中即短路）。
        // 设计原则：把「强动作、高特异」的规则（售后/工单/订单操作）排在「泛化咨询」之前，
        // 避免"这个商品能退款吗"被商品咨询抢占；跨类干扰用 negativeKeywords 排除。

        // 寒暄：整句只由问候/礼貌用语（可多段叠加、可含标点）构成才命中，
        // 既支持“下午好，请问在吗？”这类多段问候，又能避免“你好，帮我查下订单”被误判为寒暄。
        String greet = "你好|您好|哈喽|哈啰|嗨|hi|hello|hey|早上好|早安|上午好|中午好|下午好|晚上好"
                       + "|在吗|在不在|在么|在不|有人吗|有人在吗|你在吗|请问|请教一下|打扰一下|打扰了|方便吗";
        String sep = "[\\s,，。.!！?？～~、]*";
        addRule(IntentCategory.GREETING,
                "^(?:" + greet + ")(?:" + sep + "(?:" + greet + "))*" + sep + "$");

        // 售后/退换货/发票：动作性极强，优先级最高；
        // 排除“退款规则/怎么退/能不能退”等规则类问法（那属于平台政策咨询）。
        addRule(IntentCategory.AFTER_SALE,
                "(售后|退换货|退货|换货|退款|维修|保修|索赔|维权|发票|开票|电子发票|纸质发票|发票抬头|退货运费|极速退款|仅退款)",
                "政策", "规则", "规定", "制度", "怎么退", "如何退", "能退吗", "可以退", "退吗", "条件", "流程");

        // 工单进度查询（服务单/投诉单的处理状态）。
        addRule(IntentCategory.TICKET_QUERY,
                "(工单|投诉单|服务单|客诉).*(进度|状态|结果|怎么样|如何|到哪)"
                        + "|(进度|状态|结果|到哪).*(工单|投诉|服务单)"
                        + "|我的(工单|投诉)|查.*(工单|投诉单)");

        // 取消/撤回工单。
        addRule(IntentCategory.TICKET_CANCEL,
                "(取消|撤回|撤销|撤了|作废).*(工单|投诉|客诉|服务单)"
                        + "|(工单|投诉|客诉|服务单).*(取消|撤回|撤销|撤了|作废)");

        // 修改工单内容；排除“取消/撤回”避免与取消类重叠。
        addRule(IntentCategory.TICKET_MODIFY,
                "((修改|变更|补充|完善|改一下).*(工单|投诉|客诉|服务单)"
                        + "|(工单|投诉|客诉|服务单).*(改一下|修改|补充))",
                "取消", "撤回", "撤销");

        // 提交工单/投诉/转人工（新建服务单，区别于具体售后办理）。
        addRule(IntentCategory.TICKET_SUBMIT,
                "(投诉|举报|人工客服|转人工|真人客服|客服升级|找客服|客服介入|客服态度|态度差|态度问题|反馈问题|提个?工单|建个?工单|开工单|提交工单|客诉|建议反馈|服务态度|骂|坑人|欺骗|虚假宣传)");

        // 订单查询（状态/物流/历史订单）；排除售后动作与工单语境。
        addRule(IntentCategory.ORDER_QUERY,
                "(我的订单|查.*订单|订单(状态|详情|列表|记录|号)|物流|快递|到哪了|发货了吗|什么时候发货|多久发货|待收货|待付款|待发货|已买|买过|历史订单|下单记录|运单|签收|派送)",
                "取消", "退", "下单", "买这个", "结算", "工单", "投诉", "服务单", "客诉");

        // 取消订单（未付款/未发货的直接取消）；已发货要退的走售后，故排除退款/退货/换货。
        addRule(IntentCategory.ORDER_CANCEL,
                "(取消订单|把(这)?订单(取消|退了?|退掉)|订单不要了|不要了|退掉|撤单|关闭订单)",
                "退款", "退货", "换货", "售后", "发票", "赔偿");

        // 下单购买（明确购买动作）；排除“值得买吗/好不好”等咨询式问法。
        addRule(IntentCategory.ORDER_CREATE,
                "(下单|购买|买这个|买它|买一件|来[0-9一二两三四五]?[件份个]|加入购物车|结算|我要买|我想买|帮我订|帮我买|拍一件|拍了|提交订单|立即购买|立即抢购)",
                "值得", "好不好", "怎么样", "如何", "推荐", "区别", "比一下", "对比", "吗");

        // 商品/活动/平台规则咨询（泛化咨询放最后，避免抢占上面的强动作意图）。
        addRule(IntentCategory.PRODUCT_CONSULT,
                "(商品|产品|多少钱|什么价|多少米|价格|几折|折扣|优惠|活动|优惠券|满减|秒杀|促销|会员|积分|爆款|新品|上架|库存|有货|缺货|规格|参数|材质|尺码|颜色|款式|保质期|正品|假货|包邮|运费|自营|旗舰店|专卖店|品牌|型号|配置|功能|效果|好用|评测|搭配|攻略|怎么选|如何选|购买建议|值得|怎么样|好不好)",
                "订单", "物流", "工单", "投诉", "售后", "退", "发票");

        // 天气/交通等通用信息；排除电商业务词，避免抢占更具体的意图。
        addRule(IntentCategory.GENERAL_INFO,
                "(天气|气温|多少度|冷不冷|热不热|下雨|下雪|限行|路况|堵不堵|怎么去|怎么走|地铁|公交|打车|时差|汇率)",
                "订单", "商品", "退款", "退货", "券", "优惠");
    }

    private void addRule(IntentCategory category, String keyword) {
        rules.add(new Rule(category, keyword, null));
    }

    /**
     * 注册带排除词的规则：keyword 命中且不含任一 negativeKeyword 时才判定命中。
     * negativeKeyword 同样按正则编译，便于排除跨类干扰、提升 L1 精度。
     */
    private void addRule(IntentCategory category, String keyword, String... negativeKeywords) {
        rules.add(new Rule(category, keyword, List.of(negativeKeywords)));
    }

    private static Pattern compile(String regex) {
        return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    /**
     * 对输入文本执行 L1 规则匹配（兼容入口：仅暴露单类命中结果）。
     *
     * @param text 改写后的问题文本（已 trim）
     * @return {@link Verdict#HIT} 时返回结果；歧义或未命中均返回 {@link Optional#empty()}
     */
    public Optional<IntentRecognitionResult> match(String text) {
        Outcome outcome = evaluate(text);
        return outcome.verdict() == Verdict.HIT ? Optional.of(outcome.result()) : Optional.empty();
    }

    /**
     * 对输入文本执行 L1 规则匹配，返回完整裁决（供 {@link IntentRecognitionRouter} 区分
     * "未命中（可继续 L2）"与"多意图歧义（应连 L2 一起跳过）"）。
     */
    public Outcome evaluate(String text) {
        // 空输入无从判定，直接放行给 L2/L3
        if (text == null || text.isBlank()) {
            return Outcome.miss();
        }
        String normalized = text.trim();

        // 步骤 1：多意图守卫——按标点/连词拆成子句，逐句匹配
        List<IntentCategory> clauseCategories = matchClauseCategories(normalized);

        // 如果没有子句命中，则返回 MISS
        if (clauseCategories.isEmpty()) {
            return Outcome.miss();
        }
        // 如果有多个子句命中且命中类别横跨 ≥2 个目标子智能体，则返回 AMBIGUOUS
        long distinctAgents = clauseCategories.stream()
                .map(IntentCategory::getDefaultTargetAgent)
                .distinct()
                .count();
        if (distinctAgents >= 2) {
            return Outcome.ambiguous(clauseCategories);
        }

        // 步骤 2：全文按优先级取首个命中规则
        Rule hit = firstMatch(normalized);
        if (hit == null) {
            return Outcome.miss();
        }
        // 如果有规则命中，则返回 HIT
        return Outcome.hit(IntentRecognitionResult.single(
                IntentRecognitionResult.Source.RULE,
                hit.category,
                IntentRecognitionResult.Confidence.HIGH,
                "L1 规则命中：关键词匹配到「" + hit.category.getDescription() + "」",
                null));
    }

    /**
     * 按优先级（List 顺序）返回首个命中的规则，未命中返回 {@code null}。
     * 子句匹配与全文匹配共用同一求值逻辑，两者只是传入的文本范围不同。
     */
    private Rule firstMatch(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        for (Rule rule : rules) {
            if (rule.matches(text)) {
                return rule;
            }
        }
        return null;
    }

    /**
     * 把文本拆成子句后逐句做首中匹配，返回各子句命中的意图类别（去重、保持出现顺序）。
     * 单子句输入（拆不出第二个子句）时，唯一的"子句"就是全文，等价于一次全文关键词匹配。
     *
     * <p>负向词作用于子句而非全句：这是有意为之——复合句里另一半句的词不应干扰本子句的判定
     * （如"查下平台规则，顺便把退款办了"中，"规则"不应挡掉后半句的售后意图）。</p>
     *
     * <p>寒暄的特殊处理：寒暄子句不计入类别集合，避免"你好，帮我查下订单"这类
     * "问候 + 单任务"被误判为跨智能体多意图；仅当整句只有寒暄命中时才返回
     * {@code [GREETING]}，保证纯寒暄（"下午好，请问在吗？"）仍能走 L1 快路径。</p>
     */
    private List<IntentCategory> matchClauseCategories(String text) {
        String[] clauses = CLAUSE_SPLITTER.split(text);
        Set<IntentCategory> categories = new LinkedHashSet<>();
        boolean hitGreeting = false;
        for (String clause : clauses) {
            Rule hit = firstMatch(clause.trim());
            // 寒暄不参与歧义判定："你好，帮我查下订单"仍是单意图
            if (hit != null) {
                if (hit.category == IntentCategory.GREETING) {
                    hitGreeting = true;
                } else {
                    categories.add(hit.category);
                }
            }
        }

        // 如果有寒暄词且无其他意图，则返回寒暄意图
        if (hitGreeting && categories.isEmpty()) {
            return List.of(IntentCategory.GREETING);
        }

        return List.copyOf(categories);
    }

    /**
     * 仅用于单元测试 / 调试：返回当前注册的规则类别。
     */
    Map<IntentCategory, Integer> ruleStats() {
        Map<IntentCategory, Integer> stats = new LinkedHashMap<>();
        for (Rule rule : rules) {
            stats.merge(rule.category, 1, Integer::sum);
        }
        return stats;
    }
}
