package com.ecom.cs.agent.context;

import com.ecom.cs.business.order.entity.AfterSaleTicket;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 封装每次 Agent 调用的会话上下文（userId + sessionId）。
 *
 * <p>通过 {@link AgentSessionContextHolder} 在 prototype bean 构建时写入，
 * 由 AgentScope 的 {@code ToolExecutionContext} 机制自动注入到 Tool 方法参数中
 * （参数无需标注 {@code @ToolParam}，对 LLM 完全透明）。
 *
 * @author Hollis
 */
public class AgentSessionContext {

    private final String userId;
    private final String sessionId;
    /**
     * 售后政策缓存：政策按城市分层（一线/新一线/其他）而不同，必须以城市为键；工具可能并发执行，用 ConcurrentHashMap
     */
    private final Map<String, String> policyByCity = new ConcurrentHashMap<>();
    /**
     * 用户联系/乘机人信息缓存（JSON）：会话内准静态，但 update_user_contact_info 成功后必须调用 {@link #invalidateUserContactInfo()} 失效
     */
    private volatile String userContactInfo;
    /**
     * 用户常驻/办公城市缓存（JSON）：会话内准静态，但 update_user_base_location 成功后必须调用 {@link #invalidateUserBaseLocation()} 失效
     */
    private volatile String userBaseLocation;
    /**
     * 当前操作关联的售后工单ID（在查询/创建售后工单时自动设置，供 OrderPersistenceHook 关联预订记录）。
     * 并行 Toolkit（parallel=true）下工具可能跨线程写、Hook 跨线程读，需 volatile 保证可见性
     */
    private volatile String ticketId;

    private volatile AfterSaleTicket cachedOrder;

    public AgentSessionContext(String userId, String sessionId) {
        Assert.notNull(userId, "userId must not be null");
        Assert.notNull(sessionId, "sessionId must not be null");
        this.userId = userId;
        this.sessionId = sessionId;
    }

    /**
     * 获取指定城市的售后政策缓存。
     *
     * @param city 目的城市；为 null 时返回 null（不走缓存）
     */
    public String getPolicy(String city) {
        return city != null ? policyByCity.get(city.trim()) : null;
    }

    public void setPolicy(String city, String policy) {
        if (city != null && policy != null) {
            policyByCity.put(city.trim(), policy);
        }
    }

    public String getUserId() {
        return userId;
    }

    public String getSessionId() {
        return sessionId;
    }


    public String getUserContactInfo() {
        return userContactInfo;
    }

    public void setUserContactInfo(String userContactInfo) {
        this.userContactInfo = userContactInfo;
    }

    /**
     * 用户联系信息被更新后失效缓存，下次查询回源数据库
     */
    public void invalidateUserContactInfo() {
        this.userContactInfo = null;
    }

    public String getUserBaseLocation() {
        return userBaseLocation;
    }

    public void setUserBaseLocation(String userBaseLocation) {
        this.userBaseLocation = userBaseLocation;
    }

    /**
     * 常驻城市被更新后失效缓存，下次查询回源数据库
     */
    public void invalidateUserBaseLocation() {
        this.userBaseLocation = null;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public AfterSaleTicket getCachedOrder() {
        return cachedOrder;
    }

    public void setCachedOrder(AfterSaleTicket cachedOrder) {
        this.cachedOrder = cachedOrder;
        this.ticketId = cachedOrder.getOrderId();
    }

    @Override
    public String toString() {
        return "AgentSessionContext{userId='" + userId + "', sessionId='" + sessionId + "'}";
    }
}
