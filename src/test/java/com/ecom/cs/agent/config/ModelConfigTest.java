package com.ecom.cs.agent.config;

import io.agentscope.core.model.OllamaChatModel;
import io.agentscope.core.model.OpenAIChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ModelConfig 提供方切换测试：
 * 默认 Ollama、切到 DeepSeek 后四个 Bean 全部为 OpenAIChatModel、缺 API Key 时启动即失败。
 *
 * <p>只验证装配结果，不发起任何网络调用。</p>
 *
 * @author Hollis
 */
class ModelConfigTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withUserConfiguration(ModelConfig.class);

    @Test
    @DisplayName("默认 provider=ollama，四个模型 Bean 均为 OllamaChatModel")
    void defaultProvider_isOllama() {
        runner.run(ctx -> {
            assertNull(ctx.getStartupFailure(), "默认配置应能正常装配");
            assertInstanceOf(OllamaChatModel.class, ctx.getBean("fastModel"));
            assertInstanceOf(OllamaChatModel.class, ctx.getBean("stableModel"));
            assertInstanceOf(OllamaChatModel.class, ctx.getBean("strongModel"));
            assertInstanceOf(OllamaChatModel.class, ctx.getBean("strongModelWithThinking"));
        });
    }

    @Test
    @DisplayName("provider=deepseek 时四个模型 Bean 均切换为 OpenAIChatModel")
    void deepseekProvider_switchesAllBeans() {
        runner.withPropertyValues(
                        "ecom.model.provider=deepseek",
                        "ecom.model.deepseek.api-key=sk-unit-test",
                        "ecom.model.deepseek.base-url=https://api.deepseek.com")
                .run(ctx -> {
                    assertNull(ctx.getStartupFailure(), "配置了 API Key 时应能正常装配");
                    assertInstanceOf(OpenAIChatModel.class, ctx.getBean("fastModel"));
                    assertInstanceOf(OpenAIChatModel.class, ctx.getBean("stableModel"));
                    assertInstanceOf(OpenAIChatModel.class, ctx.getBean("strongModel"));
                    assertInstanceOf(OpenAIChatModel.class, ctx.getBean("strongModelWithThinking"));
                });
    }

    @Test
    @DisplayName("provider=deepseek 但缺 API Key 时启动即失败并提示 DEEPSEEK_API_KEY")
    void deepseekWithoutApiKey_failsFast() {
        runner.withPropertyValues("ecom.model.provider=deepseek").run(ctx -> {
            Throwable failure = ctx.getStartupFailure();
            assertNotNull(failure, "缺少 API Key 时不应装配成功");
            Throwable root = failure;
            while (root.getCause() != null) {
                root = root.getCause();
            }
            assertTrue(root.getMessage() != null && root.getMessage().contains("DEEPSEEK_API_KEY"),
                    "错误信息应指引配置 DEEPSEEK_API_KEY，实际：" + root.getMessage());
        });
    }
}
