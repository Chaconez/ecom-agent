package com.ecom.cs.agent.intent;

/**
 * 电商客服对话意图分类枚举。
 *
 * <p>枚举值与 {@code prompts/intent-recognition-agent-system.md} 中定义的意图类别
 * 保持一一对应。每种意图都标注了默认的目标子智能体（用于 MasterAgent 路由）。</p>
 *
 * <p>命名说明：底层子智能体 Bean 仍沿用售后时代的类名（TicketManageAgent /
 * OrderAgent / ReimbursementAgent / InfoAgent），P3 业务模块改造时统一重命名，
 * 当前阶段只保证「意图语义电商化 + 路由目标不变」。</p>
 *
 * @author Hollis
 */
public enum IntentCategory {

    /** 用户要查询订单状态、物流进度、历史订单。 */
    ORDER_QUERY("order_query", "OrderAgent", "用户查询订单状态/物流进度/历史订单"),
    /** 用户要取消订单、退款、退掉已下单的商品。 */
    ORDER_CANCEL("order_cancel", "OrderAgent", "用户取消订单/退款"),
    /** 用户要下单购买商品。 */
    ORDER_CREATE("order_create", "OrderAgent", "用户下单购买商品"),
    /** 用户要提交新的服务工单（售后申请、投诉、咨询升级等）。 */
    TICKET_SUBMIT("ticket_submit", "TicketManageAgent", "用户提交服务工单/售后申请"),
    /** 用户要查询工单处理进度、状态、结果。 */
    TICKET_QUERY("ticket_query", "TicketManageAgent", "用户查询工单处理进度/状态/结果"),
    /** 用户要取消/撤回已提交的工单。 */
    TICKET_CANCEL("ticket_cancel", "TicketManageAgent", "用户取消/撤回工单"),
    /** 用户要修改工单内容。 */
    TICKET_MODIFY("ticket_modify", "TicketManageAgent", "用户修改工单内容"),
    /** 用户要售后、退换货、识别发票、申请开票（以服务工单形式受理）。 */
    AFTER_SALE("after_sale", "TicketManageAgent", "用户售后/退换货/发票/开票（以服务工单受理）"),
    /** 用户咨询商品信息、活动规则、平台政策。 */
    PRODUCT_CONSULT("product_consult", "InfoAgent", "用户咨询商品信息/活动规则/平台政策"),
    /** 天气、交通等通用信息查询（兜底）。 */
    GENERAL_INFO("general_info", "InfoAgent", "天气/交通等通用信息查询"),
    /** 用户打招呼、寒暄。 */
    GREETING("greeting", "MasterAgent", "用户打招呼、寒暄"),
    /** 用户明确要求转人工客服/真人服务（确定性动作，L1 规则直接命中，不走 LLM）。 */
    TRANSFER_HUMAN("transfer_human", "MasterAgent", "用户要求转人工客服/真人服务"),
    /** 无法明确分类或信息严重不足。 */
    UNKNOWN("unknown", "MasterAgent", "无法明确分类或信息严重不足");

    /** 系统 Prompt / 路由 JSON 中使用的意图标识。 */
    private final String code;

    /** 默认的目标子智能体名称（与 Spring bean 名称保持一致）。 */
    private final String defaultTargetAgent;

    /** 意图的人类可读中文说明。 */
    private final String description;

    IntentCategory(String code, String defaultTargetAgent, String description) {
        this.code = code;
        this.defaultTargetAgent = defaultTargetAgent;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultTargetAgent() {
        return defaultTargetAgent;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据意图 code 反查枚举值，大小写敏感，code 不存在时返回 {@link #UNKNOWN}。
     */
    public static IntentCategory fromCode(String code) {
        if (code == null || code.isBlank()) {
            return UNKNOWN;
        }
        for (IntentCategory c : values()) {
            if (c.code.equals(code)) {
                return c;
            }
        }
        return UNKNOWN;
    }
}
