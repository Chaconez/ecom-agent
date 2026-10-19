package com.ecom.cs.agent;

import com.ecom.cs.agent.common.PromptLoader;
import com.ecom.cs.agent.context.AgentSessionContext;
import com.ecom.cs.agent.context.AgentSessionContextHolder;
import com.ecom.cs.agent.hook.ActiveAgentPersistenceHook;
import com.ecom.cs.agent.hook.AgentExecutionLoggerHook;
import com.ecom.cs.agent.hook.ProgressNotifierHook;
import com.ecom.cs.agent.hook.SessionPersistenceHook;
import com.ecom.cs.agent.memory.AgentMemoryFactory;
import com.ecom.cs.agent.memory.TravelPreferenceLongTermMemoryFactory;
import com.ecom.cs.agent.tools.UserInteractionTools;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.hook.PendingToolRecoveryHook;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.memory.autocontext.AutoContextHook;
import io.agentscope.core.model.ExecutionConfig;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.tool.ToolExecutionContext;
import io.agentscope.core.tool.Toolkit;
import io.agentscope.core.tool.subagent.SubAgentConfig;
import io.agentscope.core.tool.subagent.SubAgentProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * @author Hollis
 */
@Configuration("masterAgentConfiguration")
public class MasterAgent {

    /** 主 Agent 工具执行超时（分钟） */
    private static final int TOOL_TIMEOUT_MINUTES = 15;
    /** 主 Agent 最大推理迭代次数 */
    private static final int MAX_ITERATIONS = 15;

    @Autowired
    @Qualifier("strongModel")
    private Model strongModel;
    @Autowired
    @Qualifier("stableModel")
    private Model stableModel;
    @Autowired
    private ApplicationContext context;
    @Autowired
    private AgentExecutionLoggerHook executionLoggerHook;
    @Autowired
    private ProgressNotifierHook progressNotifierHook;
    @Autowired
    private SessionPersistenceHook sessionPersistenceHook;
    @Autowired
    private ActiveAgentPersistenceHook activeAgentPersistenceHook;
    @Autowired
    private TravelPreferenceLongTermMemoryFactory memoryFactory;

    @Bean(name = "masterAgent")
    @Scope("prototype")
    public ReActAgent build() {
        // 从 ThreadLocal 读取当前会话上下文（由 AgentPipelineService 在创建本 bean 之前投影到当前线程）
        AgentSessionContext sessionCtx = AgentSessionContextHolder.get();
        String userId = sessionCtx != null ? sessionCtx.getUserId() : null;
        LongTermMemory longTermMemory =
                memoryFactory != null ? memoryFactory.create(userId,20) : null;

        ToolExecutionContext masterToolCtx = sessionCtx != null
                ? ToolExecutionContext.builder().register(sessionCtx).build()
                : ToolExecutionContext.empty();

        Toolkit toolkit = new Toolkit();
        // 支持主动向用户提问（Human-in-the-Loop）
        toolkit.registration().tool(new UserInteractionTools()).apply();

        Supplier<ReActAgent> manageAgentProvider = AgentSessionContextHolder.bind(
                () -> context.getBean("itineraryManageAgent", ReActAgent.class));

        toolkit.registration()
                .subAgent((SubAgentProvider<ReActAgent>) manageAgentProvider::get,
                        SubAgentConfig.builder()
                                .toolName("itinerary_manage_agent")
                                .description("服务工单管理：收集信息、提交服务工单、查询工单/处理进度、撤回工单、修改工单。参数：message（任务描述），可选 session_id（继续会话）。")
                                .forwardEvents(false)
                                .build())
                .apply();

        Supplier<ReActAgent> infoAgentProvider = AgentSessionContextHolder.bind(
                () -> context.getBean("infoAgent", ReActAgent.class));
        toolkit.registration()
                .subAgent((SubAgentProvider<ReActAgent>) infoAgentProvider::get,
                        SubAgentConfig.builder()
                                .toolName("info_agent")
                                .description("信息咨询服务：商品信息、价格活动、平台规则（售后政策、配送规则、优惠玩法）及通用公共信息查询。参数：message（任务描述），可选 session_id（继续会话）。")
                                .forwardEvents(false)
                                .build())
                .apply();

        Supplier<ReActAgent> bookingAgentProvider = AgentSessionContextHolder.bind(
                () -> context.getBean("bookingAgent", ReActAgent.class));
        toolkit.registration()
                .subAgent((SubAgentProvider<ReActAgent>) bookingAgentProvider::get,
                        SubAgentConfig.builder()
                                .toolName("booking_agent")
                                .description("订单管理服务：查询订单状态/物流进度/历史订单、取消订单、确认购买意向后执行下单。参数：message（任务描述，含用户要查询/取消/购买的订单或商品），可选 session_id（继续会话）。")
                                .forwardEvents(false)
                                .build())
                .apply();

        // 注 1：itineraryPlanAgent（行程规划）暂不注册给 MasterAgent——
        // 电商客服场景下无对应业务路由，P3 商品搜索/选购方案模块上线后重新接入。
        // 注 2：reimbursementAgent（售后与发票）当前为占位 Bean（build 返回 null），
        // 售后/发票诉求统一由 itinerary_manage_agent 以服务工单形式受理，
        // 待 ReimbursementAgent 真正实现后再拆分独立路由。

        ReActAgent agent = ReActAgent.builder()
                .name("MasterAgent")
                .description("电商智能客服主智能体，负责协调工单管理、订单管理、售后发票、信息咨询等服务完成完整的电商客服会话")
                .model(strongModel)
                .toolkit(toolkit)
                .toolExecutionContext(masterToolCtx)
                .toolExecutionConfig(ExecutionConfig.builder()
                        .timeout(Duration.ofMinutes(TOOL_TIMEOUT_MINUTES))
                        .maxAttempts(3)
                        .build())
                // 自动压缩/卸载大工具结果与历史，抑制多轮输入 token 膨胀（需搭配 AutoContextHook）
                .memory(AgentMemoryFactory.create(stableModel))
                .longTermMemory(longTermMemory)
                .longTermMemoryMode(LongTermMemoryMode.AGENT_CONTROL)
                .hooks(List.of(new AutoContextHook(), executionLoggerHook, progressNotifierHook, sessionPersistenceHook, new PendingToolRecoveryHook()))
                .sysPrompt(PromptLoader.load("master-agent-system.md"))
                .maxIters(MAX_ITERATIONS)
                .generateOptions(GenerateOptions.builder()
                        // 开启缓存，框架会自动在每次请求时对 system messages 和 最后一条消息 加上 cache_control （https://bailian.console.aliyun.com/cn-beijing?tab=doc#/doc/?type=model&url=2862577）
                        .cacheControl(true)
                        .build())
                .build();

        return agent;
    }
}
