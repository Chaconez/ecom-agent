package com.ecom.cs.agent.intent;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link IntentRecognitionRouter} L0 多意图结构启发测试。
 *
 * <p>L0 只做纯文本判定（连词 + 句长 + 连词位置），不依赖 Spring 容器，
 * 通过包级静态方法 {@code hasMultiIntentSignal} 直接验证。
 * 判定为 true 的代价只是放弃 L1/L2 快路径走 L3，不会导致路由错误，
 * 因此用例侧重"该拦的拦住、明显单意图的别误伤"。</p>
 *
 * @author Hollis
 */
@DisplayName("IntentRecognitionRouter L0 多意图结构启发测试")
class IntentRecognitionRouterTest {

    @Test
    @DisplayName("句中含并列/顺承连词的长复合句应被识别为多意图信号")
    void shouldDetectMultiIntentSignal() {
        List<String> positives = List.of(
                "帮我查下我的订单，然后再帮我退款",
                "查下这款的口碑，顺便看看有没有优惠",
                "帮我提交个投诉，另外查一下工单进度",
                "先帮我查订单，接着把发票也开了",
                "我把这个下完单之后再帮我看看物流");

        List<String> failures = new ArrayList<>();
        for (String text : positives) {
            if (!IntentRecognitionRouter.hasMultiIntentSignal(text)) {
                failures.add(String.format("「%s」期望=命中多意图信号，实际=未命中", text));
            }
        }
        assertTrue(failures.isEmpty(), buildReport("漏判(应拦截却放行)", failures));
    }

    @Test
    @DisplayName("单意图表达不应被误判为多意图信号")
    void shouldNotFalsePositiveOnSingleIntent() {
        List<String> negatives = new ArrayList<>(List.of(
                // 连词在句首：口语衔接词，不是多意图信号
                "顺便问一下平台规则是什么",
                "然后呢",
                // 短句：即使含连词也大概率单意图
                "顺便查下优惠",
                // 无连词的普通单意图长句
                "帮我查一下我的订单详情",
                "我想了解一下售后政策"));
        negatives.add(null);

        List<String> failures = new ArrayList<>();
        for (String text : negatives) {
            if (IntentRecognitionRouter.hasMultiIntentSignal(text)) {
                failures.add(String.format("「%s」期望=放行，实际=误判为多意图信号", text));
            }
        }
        assertTrue(failures.isEmpty(), buildReport("误伤(单意图被拦截)", failures));
    }

    private static String buildReport(String title, List<String> failures) {
        StringBuilder sb = new StringBuilder();
        sb.append(title).append("，共 ").append(failures.size()).append(" 条失败：\n");
        for (String f : failures) {
            sb.append("  - ").append(f).append('\n');
        }
        return sb.toString();
    }
}
