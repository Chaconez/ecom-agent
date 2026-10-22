package com.ecom.cs.agent.tools;

import com.alibaba.fastjson2.JSON;
import com.ecom.cs.agent.context.AgentSessionContext;
import com.ecom.cs.business.policy.entity.PolicyCheckResult;
import com.ecom.cs.business.policy.entity.AfterSalePolicy;
import com.ecom.cs.business.policy.service.AfterSalePolicyService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 售后政策工具集：查询政策标准 + 合规校验，共享 AfterSalePolicyService。
 *
 * @author Hollis
 */
@Component
public class PolicyTools {

    private static final Logger logger = LoggerFactory.getLogger(PolicyTools.class);

    @Autowired
    private AfterSalePolicyService policyService;

    @Tool(name = "query_after_sale_policy",
            description = "查询指定用户和目的城市的售后政策标准（餐标、酒店标准、交通补贴等）")
    public String queryTravelPolicy(
            AgentSessionContext sessionCtx,
            @ToolParam(name = "city", description = "目的城市") String city) {
        return resolvePolicyJson(sessionCtx, city, "query_after_sale_policy");
    }

    @Tool(name = "check_after_sale_policy",
            description = "校验行程或订单是否符合售后政策，返回合规结果和超标说明")
    public String checkTravelPolicy(
            AgentSessionContext sessionCtx,
            @ToolParam(name = "city", description = "目的城市") String city,
            @ToolParam(name = "order_summary",
                    description = "订单摘要JSON，例如 {\"type\":\"HOTEL\",\"amount\":450}") String orderSummary) {

        logger.info("[TOOL][check_after_sale_policy] city={}, orderSummary={}", city, orderSummary);
        AfterSalePolicy policy = JSON.parseObject(
                resolvePolicyJson(sessionCtx, city, "check_after_sale_policy"), AfterSalePolicy.class);
        PolicyCheckResult result = policyService.checkCompliance(orderSummary, policy);
        String json = JSON.toJSONString(result);
        logger.info("[TOOL][check_after_sale_policy] done");
        return json;
    }

    /**
     * 获取指定城市的售后政策 JSON：优先读会话级缓存，miss 时查 service 并回填缓存，
     * 供查询 / 合规校验两个工具共用，避免同一会话内重复查库。
     */
    private String resolvePolicyJson(AgentSessionContext sessionCtx, String city, String toolName) {
        String cached = sessionCtx.getPolicy(city);
        if (cached != null) {
            logger.info("[TOOL][{}] hit cache, city={}", toolName, city);
            return cached;
        }
        String userId = sessionCtx.getUserId();
        logger.info("[TOOL][{}] miss cache, userId={}, city={}", toolName, userId, city);
        AfterSalePolicy policy = policyService.getPolicy(userId, city);
        String result = JSON.toJSONString(policy);
        logger.info("[TOOL][{}] result={}", toolName, result);
        sessionCtx.setPolicy(city, result);
        return result;
    }
}
