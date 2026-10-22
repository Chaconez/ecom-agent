package com.ecom.cs.agent;

import com.ecom.cs.agent.common.PromptLoader;
import com.ecom.cs.agent.hook.*;
import com.ecom.cs.agent.memory.AgentMemoryFactory;
import com.ecom.cs.agent.tools.ApiKeyTools;
import com.ecom.cs.agent.tools.OrderWriteTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.PendingToolRecoveryHook;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.memory.autocontext.AutoContextHook;
import io.agentscope.core.memory.autocontext.AutoContextMemory;
import io.agentscope.core.skill.SkillBox;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.ToolkitConfig;
import io.agentscope.core.tool.coding.ShellCommandTool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.util.List;
import java.util.Set;

/**
 * 预订执行智能体。
 *
 * <p>负责用户确认行程方案后的实际预订执行（出票/订房/订火车票）及已有预订的取消。
 * 由 MasterAgent 根据 booking 意图路由调用，与 ProductShoppingAgent 平级。
 *
 * <p>核心能力：
 * <ul>
 *   <li>通过 tuniu-cli 技能执行下单命令</li>
 *   <li>通过 OrderPersistenceHook 自动将下单结果落库</li>
 *   <li>通过 cancel_order 工具取消已有预订</li>
 *   <li>查询预订记录供用户核查</li>
 * </ul>
 *
 * @author Hollis
 */
@Configuration("orderAgentConfiguration")
public class OrderAgent extends BaseSubAgent {

    /**
     * 预订 Agent 最大推理迭代次数
     */
    private static final int MAX_ITERATIONS = 10;

    @Autowired
    protected OrderPersistenceHook orderPersistenceHook;

    /**
     * 预订记录写工具集（cancel_order）。
     */
    @Autowired
    protected OrderWriteTools orderWriteTools;

    @Bean(name = "orderAgent")
    @Scope("prototype")
    public ReActAgent build() {
        Toolkit toolkit = new Toolkit(ToolkitConfig.builder()
                .parallel(true)
                .build());
        // 查询已审批售后工单（获取预订上下文）
        toolkit.registration().tool(ticketReadTools).apply();
        // 查询外部预订记录（商品/服务等）
        toolkit.registration().tool(orderReadTools).apply();
        // 取消预订
        toolkit.registration().tool(orderWriteTools).apply();
        // 用户信息查询
        toolkit.registration().tool(userInfoReadTools).apply();
        toolkit.registration().tool(userInfoWriteTools).apply();
        // API Key 管理
        toolkit.registration().tool(apiKeyTools).apply();
        toolkit.registration().tool(productShoppingReadTools).apply();

        SkillBox skillBox = buildOrderSkillBox(toolkit);

        LongTermMemory longTermMemory = createLongTermMemory(memoryFactory);

        AutoContextMemory memory = AgentMemoryFactory.create(stableModel);

        ReActAgent agent = ReActAgent.builder()
                .name("OrderAgent")
                .description("订单管理专家，负责查询订单状态/物流进度/历史订单、取消订单、确认购买意向后执行下单")
                .model(strongModelWithThinking)
                .memory(memory)
                .longTermMemory(longTermMemory)
                .longTermMemoryMode(LongTermMemoryMode.AGENT_CONTROL)
                .toolkit(toolkit)
                .toolExecutionContext(createToolCtx())
                .skillBox(skillBox)
                .hooks(List.of(dynamicTimeInjectionHook, new AutoContextHook(), cliResultCompressHook,
                        flightApiKeyHook, tuniuApiKeyHook, rghUserIsolationHook,
                        skillContentCollapseHook, sessionPersistenceHook, executionLoggerHook,
                        progressNotifierHook, activeAgentPersistenceHook, orderPersistenceHook,
                        new PendingToolRecoveryHook()))
                .toolExecutionConfig(getToolConfig())
                .sysPrompt(PromptLoader.loadStatic("order-agent-system.md"))
                .maxIters(MAX_ITERATIONS)
                .generateOptions(getEnableThinkingGenerateOptions(2048))
                .build();

        return agent;
    }

    private SkillBox buildOrderSkillBox(Toolkit toolkit) {
        SkillBox skillBox = new SkillBox(toolkit);
        skillBox.registerSkill(skillRepository.getSkill("tuniu-cli"));
        ShellCommandTool shellTool = new ShellCommandTool(
                null,
                Set.of("tuniu", "env", "bash", "cat", "grep", "date", "which", "ls"),
                null);
        skillBox.codeExecution().withShell(shellTool).withRead().withWrite().enable();
        return skillBox;
    }
}
