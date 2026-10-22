package com.ecom.cs.agent.config;

import io.agentscope.core.formatter.openai.DeepSeekFormatter;
import io.agentscope.core.formatter.openai.dto.OpenAIRequest;
import io.agentscope.core.model.GenerateOptions;

import java.util.HashMap;
import java.util.Map;

/**
 * DeepSeek V4 专用 Formatter：在官方 {@link DeepSeekFormatter} 之上补 thinking 开关。
 *
 * <p>继承官方实现的原因：官方 formatter 已处理 DeepSeek 的消息级约束
 * （不带 name 字段、system 转 user、不支持 tool 的 strict、reasoning_content 只保留本轮）。
 * 但 DeepSeek V4 系列**默认开启 thinking**，而本项目的轻任务
 * （会话标题、偏好抽取、意图识别 JSON 输出）不需要思考链，开着的代价是延迟与成本双增。</p>
 *
 * <p>thinking 通过请求体的 {@code thinking: {type: enabled|disabled}} 控制
 * （见 DeepSeek 官方 API 文档），该字段不在 OpenAI 标准 schema 里，
 * 借 {@code OpenAIRequest} 的 {@code @JsonAnyGetter} 扩展位注入。</p>
 *
 * @author Hollis
 */
class DeepSeekThinkingFormatter extends DeepSeekFormatter {

    /** 是否开启思考链 */
    private final boolean thinkingEnabled;

    /** 思考强度：high / medium / low，仅在开启时生效 */
    private final String reasoningEffort;

    DeepSeekThinkingFormatter(boolean thinkingEnabled, String reasoningEffort) {
        this.thinkingEnabled = thinkingEnabled;
        this.reasoningEffort = reasoningEffort;
    }

    @Override
    public void applyOptions(OpenAIRequest request, GenerateOptions options, GenerateOptions defaultOptions) {
        super.applyOptions(request, options, defaultOptions);

        Map<String, Object> thinking = new HashMap<>(2);
        thinking.put("type", thinkingEnabled ? "enabled" : "disabled");
        request.addExtraParam("thinking", thinking);

        if (thinkingEnabled && reasoningEffort != null && !reasoningEffort.isBlank()) {
            request.addExtraParam("reasoning_effort", reasoningEffort);
        }
    }
}
