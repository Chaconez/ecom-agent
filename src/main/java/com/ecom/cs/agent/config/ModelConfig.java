package com.ecom.cs.agent.config;

import io.agentscope.core.model.Model;
import io.agentscope.core.model.OllamaChatModel;
import io.agentscope.core.model.ollama.OllamaOptions;
import io.agentscope.core.model.ollama.ThinkOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 模型装配。四个模型 Bean 全部走本地 Ollama（2026-10-18 起，DashScope 账号欠费 + 全离线诉求）：
 *
 * <p>fastModel（会话标题 / 偏好抽取）、stableModel（意图 L3 / 改写 / InfoAgent / 记忆摘要）
 * 用 qwen2.5:7b（无 thinking、加载快）；strongModel / strongModelWithThinking（行程规划主链路）
 * 用 qwen3.8:27b-mlx（27B、支持 tools 与 thinking），numCtx 调到 16K 应对多工具长对话。</p>
 *
 * <p>回滚方式：把对应 Bean 换回各自的 DashScopeChatModel 注释块即可。</p>
 *
 * @author Hollis
 */
@Configuration
public class ModelConfig {

    @Value("${agentscope.dashscope.api-key:${DASHSCOPE_API_KEY:}}")
    private String apiKey;

    @Value("${ollama.base-url:${OLLAMA_BASE_URL:http://192.168.3.15:11434}}")
    private String ollamaBaseUrl;

    @Value("${ollama.fast-model:${OLLAMA_FAST_MODEL:qwen2.5:7b}}")
    private String ollamaFastModel;

    @Value("${ollama.stable-model:${OLLAMA_STABLE_MODEL:qwen2.5:7b}}")
    private String ollamaStableModel;

    @Value("${ollama.strong-model:${OLLAMA_STRONG_MODEL:qwen3.8:27b-mlx}}")
    private String ollamaStrongModel;

    /**
     * strong 模型的上下文窗口。Ollama 默认 num_ctx 只有 4K，
     * 行程规划是多工具长链路（系统提示 + 知识检索 + 工具往返）容易爆，
     * 默认 16K；显存紧张可降到 8K。
     */
    @Value("${ollama.strong-num-ctx:${OLLAMA_STRONG_NUM_CTX:16384}}")
    private Integer ollamaStrongNumCtx;

    /**
     * 轻任务模型：本地 Ollama。
     * 选用 qwen2.5:7b 而非 qwen3.8:27b-mlx 的原因：
     * 1. 无 thinking 输出，标题/抽取类任务不需要，还能省 first-token 延迟；
     * 2. 7B 加载快、显存占用小，可与 27B 常驻并存；
     * 3. 支持 tools（当前用法不涉及工具，留余量）。
     */
    @Bean("fastModel")
    public Model fastModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaFastModel)
                .build();
    }

    // 回滚用：云端 fastModel（qwen3.6-flash，1.2 元/百万 token）
    // @Bean("fastModel")
    // public Model fastModelDashScope() {
    //     return DashScopeChatModel.builder()
    //             .apiKey(apiKey)
    //             .modelName("qwen3.6-flash")
    //             .stream(true)
    //             .enableThinking(false)
    //             .build();
    // }

    /**
     * 规划主模型：本地 Ollama qwen3.8:27b-mlx（27B，支持 tools）。
     * numCtx 16K 应对多工具长链路；keep-alive 30m 避免规划期间反复加载模型。
     */
    @Bean("strongModel")
    @Primary
    public Model strongModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaStrongModel)
                .defaultOptions(OllamaOptions.builder()
                        .numCtx(ollamaStrongNumCtx)
                        .keepAlive("30m")
                        .thinkOption(ThinkOption.ThinkBoolean.DISABLED)
                        .build())
                .build();
    }

    // 回滚用：云端 strongModel（qwen3.7-max，12元/百万token；DashScope 账号欠费时不可用）
    // @Bean("strongModel")
    // @Primary
    // public Model strongModelDashScope() {
    //     return DashScopeChatModel.builder()
    //             .apiKey(apiKey)
    //             .modelName("qwen3.7-max")
    //             .stream(true)
    //             .enableThinking(false)
    //             .build();
    // }

    /**
     * 深度思考版规划模型：同一 27B 模型，开启 thinking。
     */
    @Bean("strongModelWithThinking")
    public Model strongModelWithThinking() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaStrongModel)
                .defaultOptions(OllamaOptions.builder()
                        .numCtx(ollamaStrongNumCtx)
                        .keepAlive("30m")
                        .thinkOption(ThinkOption.ThinkBoolean.ENABLED)
                        .build())
                .build();
    }

    // 回滚用：云端 strongModelWithThinking
    // @Bean("strongModelWithThinking")
    // public Model strongModelWithThinkingDashScope() {
    //     return DashScopeChatModel.builder()
    //             .apiKey(apiKey)
    //             .modelName("qwen3.7-max")
    //             .stream(true)
    //             .enableThinking(true)
    //             .build();
    // }

    /**
     * 稳定任务模型：本地 Ollama。
     * 承担意图识别 L3 / 查询改写 / InfoAgent / 记忆摘要等确定性任务，
     * 与 fastModel 同选 qwen2.5:7b（无 thinking、JSON 输出稳定）。
     * 若希望更好的语义理解质量，可通过 OLLAMA_STABLE_MODEL=qwen3.8:27b-mlx 切到 27B。
     */
    @Bean("stableModel")
    public Model stableModel() {
        return OllamaChatModel.builder()
                .baseUrl(ollamaBaseUrl)
                .modelName(ollamaStableModel)
                .build();
    }

    // 回滚用：云端 stableModel（glm-5.1，6元/百万token）
    // @Bean("stableModel")
    // public Model stableModelDashScope() {
    //     return DashScopeChatModel.builder()
    //             .apiKey(apiKey)
    //             .modelName("glm-5.1")
    //             .stream(true)
    //             .enableThinking(false)
    //             .build();
    // }
}
