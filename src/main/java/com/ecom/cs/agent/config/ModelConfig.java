package com.ecom.cs.agent.config;

import io.agentscope.core.model.Model;
import io.agentscope.core.model.OllamaChatModel;
import io.agentscope.core.model.OpenAIChatModel;
import io.agentscope.core.model.ollama.OllamaOptions;
import io.agentscope.core.model.ollama.ThinkOption;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 模型装配：四个模型 Bean 支持按 {@code ecom.model.provider} 在两家提供方之间切换。
 *
 * <table border="1">
 *   <caption>模型分工</caption>
 *   <tr><th>Bean</th><th>职责</th><th>Ollama 默认</th><th>DeepSeek 默认</th></tr>
 *   <tr><td>fastModel</td><td>会话标题、偏好抽取</td><td>qwen2.5:7b</td><td>deepseek-flash</td></tr>
 *   <tr><td>stableModel</td><td>意图 L3、查询改写、InfoAgent、记忆摘要</td><td>qwen2.5:7b</td><td>deepseek-flash</td></tr>
 *   <tr><td>strongModel</td><td>主链路（Master / 工单 / 订单 / 商品导购）</td><td>qwen3.8:27b-mlx</td><td>deepseek-flash</td></tr>
 *   <tr><td>strongModelWithThinking</td><td>需要深度思考的场景</td><td>同上 + thinking</td><td>deepseek-v4-pro（thinking）</td></tr>
 * </table>
 *
 * <p>切换方式（无需改代码）：{@code ECOM_MODEL_PROVIDER=deepseek} + {@code DEEPSEEK_API_KEY=sk-xxx}。</p>
 *
 * <p>DeepSeek 走 {@link OpenAIChatModel}（OpenAI 兼容协议），formatter 用
 * {@link DeepSeekThinkingFormatter}：它在官方 {@code DeepSeekFormatter} 之上补 thinking 开关，
 * 官方实现负责 DeepSeek 的消息级约束（消息不带 name 字段、system 转 user、
 * 不支持 tool 的 strict 参数、reasoning_content 仅保留本轮回传）。</p>
 *
 * <p>模型名注意：{@code deepseek-chat} / {@code deepseek-reasoner} 已于 2026-07-24 退役，
 * 现行名称为 {@code deepseek-flash}（V4.1-Flash，1M 上下文、支持 tool calls）
 * 与 {@code deepseek-v4-pro}（V4-Pro，更强推理）。默认值均可通过环境变量覆盖。</p>
 *
 * <p>embedding 不在此处：意图 L2 向量与政策知识库仍用 Ollama 的 bge-m3
 * （DeepSeek 没有 embedding 接口）。</p>
 *
 * @author Hollis
 */
@Configuration
public class ModelConfig {

    private static final Logger logger = LoggerFactory.getLogger(ModelConfig.class);

    /** 提供方：ollama（本地，默认）/ deepseek（云端） */
    @Value("${ecom.model.provider:ollama}")
    private String provider;

    // ── Ollama ────────────────────────────────────────────────────────────────

    @Value("${ollama.base-url:${OLLAMA_BASE_URL:http://127.0.0.1:11434}}")
    private String ollamaBaseUrl;

    @Value("${ollama.fast-model:${OLLAMA_FAST_MODEL:qwen3.8:27b-mlx}}")
    private String ollamaFastModel;

    @Value("${ollama.stable-model:${OLLAMA_STABLE_MODEL:qwen3.8:27b-mlx}}")
    private String ollamaStableModel;

    @Value("${ollama.strong-model:${OLLAMA_STRONG_MODEL:qwen3.8:27b-mlx}}")
    private String ollamaStrongModel;

    /**
     * <b>四个 Bean 共用的上下文窗口</b>。Ollama 默认只给 4K（0.40 对大内存机器会放宽，
     * 实测行为不确定），这里统一显式指定。
     *
     * <p>⚠️ <b>必须四个 Bean 用同一个值</b>：它们默认都是同一个 27B 模型，
     * num_ctx 不同会让 Ollama 把同一份权重加载成两个实例，来回切换就是反复冷加载。</p>
     */
    @Value("${ollama.num-ctx:${OLLAMA_NUM_CTX:${OLLAMA_STRONG_NUM_CTX:16384}}}")
    private Integer ollamaNumCtx;

    /**
     * 模型常驻时长，四个 Bean 必须一致 —— 轻任务若用默认 5m，会把实例的存活时间刷短，
     * 主链路下次请求又要重载。
     */
    @Value("${ollama.keep-alive:${OLLAMA_KEEP_ALIVE:30m}}")
    private String ollamaKeepAlive;

    // ── DeepSeek ──────────────────────────────────────────────────────────────

    @Value("${ecom.model.deepseek.api-key:${DEEPSEEK_API_KEY:}}")
    private String deepseekApiKey;

    @Value("${ecom.model.deepseek.base-url:${DEEPSEEK_BASE_URL:https://api.deepseek.com}}")
    private String deepseekBaseUrl;

    @Value("${ecom.model.deepseek.fast-model:${DEEPSEEK_FAST_MODEL:deepseek-flash}}")
    private String deepseekFastModel;

    @Value("${ecom.model.deepseek.stable-model:${DEEPSEEK_STABLE_MODEL:deepseek-flash}}")
    private String deepseekStableModel;

    @Value("${ecom.model.deepseek.strong-model:${DEEPSEEK_STRONG_MODEL:deepseek-flash}}")
    private String deepseekStrongModel;

    @Value("${ecom.model.deepseek.thinking-model:${DEEPSEEK_THINKING_MODEL:deepseek-v4-pro}}")
    private String deepseekThinkingModel;

    /** 深度思考版的思考强度：high / medium / low */
    @Value("${ecom.model.deepseek.thinking-effort:${DEEPSEEK_THINKING_EFFORT:high}}")
    private String deepseekThinkingEffort;

    /**
     * 启动即校验：选了 deepseek 却没给 key 直接失败，避免运行到第一次对话才暴露。
     */
    @PostConstruct
    void validateProviderConfig() {
        logger.info("[ModelConfig] provider={}（ollama: {} / deepseek: {}）",
                provider, ollamaBaseUrl, deepseekBaseUrl);
        if (isDeepSeek() && (deepseekApiKey == null || deepseekApiKey.isBlank())) {
            throw new IllegalStateException(
                    "ecom.model.provider=deepseek 但未配置 API Key："
                            + "请设置环境变量 DEEPSEEK_API_KEY（或 ecom.model.deepseek.api-key）");
        }
    }

    private boolean isDeepSeek() {
        return "deepseek".equalsIgnoreCase(provider == null ? "" : provider.trim());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 四个模型 Bean
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 轻任务模型：会话标题、偏好抽取。
     * <p>与主链路同模型同参数（见 {@link #lightOptions()}），保证落到同一个 Ollama 实例。</p>
     */
    @Bean("fastModel")
    public Model fastModel() {
        if (isDeepSeek()) {
            return deepSeekChat(deepseekFastModel, false, null);
        }
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaFastModel)
                .defaultOptions(lightOptions())
                .build();
    }

    /**
     * 主模型：MasterAgent 及各子智能体的推理主链路。
     * <p>Ollama 下用 27B 支持 tools 的模型，numCtx 16K、keep-alive 30m 避免规划期间反复加载；
     * DeepSeek 下用 deepseek-chat（支持 function calling）。</p>
     */
    @Bean("strongModel")
    @Primary
    public Model strongModel() {
        if (isDeepSeek()) {
            return deepSeekChat(deepseekStrongModel, false, null);
        }
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaStrongModel)
                .defaultOptions(OllamaOptions.builder()
                        .numCtx(ollamaNumCtx)
                        .keepAlive(ollamaKeepAlive)
                        .thinkOption(ThinkOption.ThinkBoolean.DISABLED)
                        .build())
                .build();
    }

    /**
     * 深度思考版主模型：Ollama 同模型开 thinking；DeepSeek 用 deepseek-reasoner。
     */
    @Bean("strongModelWithThinking")
    public Model strongModelWithThinking() {
        if (isDeepSeek()) {
            return deepSeekChat(deepseekThinkingModel, true, deepseekThinkingEffort);
        }
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaStrongModel)
                .defaultOptions(OllamaOptions.builder()
                        .numCtx(ollamaNumCtx)
                        .keepAlive(ollamaKeepAlive)
                        .thinkOption(ThinkOption.ThinkBoolean.ENABLED)
                        .build())
                .build();
    }

    /**
     * 稳定任务模型：意图 L3 / 查询改写 / InfoAgent / 记忆摘要等确定性任务，
     * 要求 JSON 输出稳定，不需要 thinking。
     * <p>与 fastModel 共用 {@link #lightOptions()}，保证落到同一个 Ollama 实例。</p>
     */
    @Bean("stableModel")
    public Model stableModel() {
        if (isDeepSeek()) {
            return deepSeekChat(deepseekStableModel, false, null);
        }
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaStableModel)
                .defaultOptions(lightOptions())
                .build();
    }

    /**
     * 轻任务 Bean 的公共 options：关闭 thinking + 与主链路一致的 numCtx / keepAlive。
     * 抽出来是为了让 fastModel 和 stableModel 不可能写出两套参数 ——
     * num_ctx 不同会让 Ollama 把同一份权重加载成两个实例。
     */
    private OllamaOptions lightOptions() {
        return OllamaOptions.builder()
                .numCtx(ollamaNumCtx)
                .keepAlive(ollamaKeepAlive)
                .thinkOption(ThinkOption.ThinkBoolean.DISABLED)
                .build();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DeepSeek 装配
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 构建 DeepSeek 模型（OpenAI 兼容协议）。
     *
     * @param modelName       deepseek-flash（通用 + tool calls）/ deepseek-v4-pro（更强推理）
     * @param thinkingEnabled 是否开启思考链；轻任务与主链路关闭以降低延迟
     * @param reasoningEffort 思考强度（仅 thinkingEnabled=true 时生效）
     */
    private Model deepSeekChat(String modelName, boolean thinkingEnabled, String reasoningEffort) {
        return OpenAIChatModel.builder()
                .apiKey(deepseekApiKey.trim())
                .baseUrl(deepseekBaseUrl)
                .modelName(modelName)
                .stream(true)
                .formatter(new DeepSeekThinkingFormatter(thinkingEnabled, reasoningEffort))
                .build();
    }
}
