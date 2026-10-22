package com.ecom.cs.agent.config;

import io.agentscope.core.formatter.openai.dto.OpenAIRequest;
import io.agentscope.core.model.GenerateOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DeepSeekThinkingFormatter 测试：验证 thinking 开关被正确注入请求体扩展位。
 *
 * <p>只验证请求体字段，不发起网络调用。</p>
 *
 * @author Hollis
 */
class DeepSeekThinkingFormatterTest {

    private static OpenAIRequest newRequest() {
        return OpenAIRequest.builder().model("deepseek-flash").messages(List.of()).stream(true).build();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> thinkingOf(OpenAIRequest request) {
        return (Map<String, Object>) request.getExtraParams().get("thinking");
    }

    @Test
    @DisplayName("thinking 关闭时注入 thinking={type:disabled} 且不带 reasoning_effort")
    void thinkingDisabled_injectsDisabledFlag() {
        OpenAIRequest request = newRequest();
        new DeepSeekThinkingFormatter(false, "high").applyOptions(request, GenerateOptions.builder().build(), null);

        Map<String, Object> thinking = thinkingOf(request);
        assertNotNull(thinking, "应注入 thinking 字段");
        assertEquals("disabled", thinking.get("type"));
        assertFalse(request.getExtraParams().containsKey("reasoning_effort"),
                "关闭思考时不应传 reasoning_effort");
    }

    @Test
    @DisplayName("thinking 开启时注入 enabled 与 effort")
    void thinkingEnabled_injectsEffort() {
        OpenAIRequest request = newRequest();
        new DeepSeekThinkingFormatter(true, "high").applyOptions(request, GenerateOptions.builder().build(), null);

        Map<String, Object> thinking = thinkingOf(request);
        assertNotNull(thinking);
        assertEquals("enabled", thinking.get("type"));
        assertEquals("high", request.getExtraParams().get("reasoning_effort"));
    }

    @Test
    @DisplayName("官方 DeepSeek 约束仍在：formatter 不支持 tool 的 strict 参数")
    void deepSeekConstraintsPreserved() {
        DeepSeekThinkingFormatter formatter = new DeepSeekThinkingFormatter(false, null);
        OpenAIRequest request = newRequest();
        formatter.applyOptions(request, GenerateOptions.builder().build(), null);
        assertTrue(request.getExtraParams().containsKey("thinking"));
    }
}
