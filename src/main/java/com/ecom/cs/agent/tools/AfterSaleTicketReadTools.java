package com.ecom.cs.agent.tools;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecom.cs.agent.context.AgentSessionContext;
import com.ecom.cs.business.approval.entity.ApprovalRecord;
import com.ecom.cs.business.approval.service.ApprovalService;
import com.ecom.cs.business.order.entity.AfterSaleTicket;
import com.ecom.cs.business.order.entity.AfterSaleTicketStatus;
import com.ecom.cs.business.order.mapper.AfterSaleTicketMapper;
import com.ecom.cs.business.order.repo.AfterSaleTicketRepository;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.*;

/**
 * @author Hollis
 */
@Component
public class AfterSaleTicketReadTools {

    private static final Logger logger = LoggerFactory.getLogger(AfterSaleTicketReadTools.class);

    @Autowired
    private AfterSaleTicketRepository ticketRepository;

    @Autowired
    private AfterSaleTicketMapper ticketMapper;

    @Autowired
    private ApprovalService approvalService;


//废弃： https://thoughts.aliyun.com/workspaces/6963289eb0fc2e001bb052eb/docs/6a905bc1a7c8ff00013e3a0d
//    @Tool(name = "query_after_sale_ticket", description = "查询用户售后工单列表或指定售后工单详情。支持按状态、出发日期范围过滤。")
//    public String queryAfterSaleTicket(
//            AgentSessionContext sessionCtx,
//            @ToolParam(name = "order_id", description = "售后工单ID，传入则直接返回该单详情，忽略其他筛选条件", required = false) String orderId,
//            @ToolParam(name = "status", description = "状态过滤，支持单个或逗号分隔的多个状态，如 DRAFT 或 DRAFT,SUBMITTED。可选值: DRAFT/SUBMITTED/APPROVED/REJECTED/COMPLETED/CANCELLED", required = false) String status,
//            @ToolParam(name = "start_date", description = "筛选出发日期不早于此日期的售后工单，格式 YYYY-MM-DD，可选", required = false) String startDate,
//            @ToolParam(name = "end_date", description = "筛选出发日期不晚于此日期的售后工单，格式 YYYY-MM-DD，可选", required = false) String endDate) {
//
//        String userId = sessionCtx.getUserId();
//        logger.info("[TOOL][query_after_sale_ticket] userId={}, orderId={}, status={}, startDate={}, endDate={}",
//                userId, orderId, status, startDate, endDate);
//
//        if (orderId != null && !orderId.isBlank()) {
//            AfterSaleTicket order = ticketRepository.findByOrderId(orderId).orElse(null);
//            if (order != null) {
//                sessionCtx.setTicketId(orderId);
//                sessionCtx.setCachedOrders(List.of(order));
//            }
//            String result = JSON.toJSONString(order != null ? order : "{\"error\":\"order not found\"}");
//            logger.info("[TOOL][query_after_sale_ticket] result={}", result);
//            return result;
//        }
//
//        List<AfterSaleTicket> orders;
//        boolean hasDateFilter = (startDate != null && !startDate.isBlank())
//                || (endDate != null && !endDate.isBlank());
//
//        if (hasDateFilter) {
//            LambdaQueryWrapper<AfterSaleTicket> wrapper = new LambdaQueryWrapper<AfterSaleTicket>()
//                    .eq(AfterSaleTicket::getUserId, userId);
//            if (status != null && !status.isBlank()) {
//                List<AfterSaleTicketStatus> statusList = parseStatusList(status);
//                if (statusList == null) {
//                    return JSON.toJSONString(java.util.Map.of("error",
//                            "无效的状态参数: " + status + "，可选值: DRAFT/SUBMITTED/APPROVED/REJECTED/COMPLETED/CANCELLED"));
//                }
//                if (statusList.size() == 1) {
//                    wrapper.eq(AfterSaleTicket::getStatus, statusList.get(0));
//                } else {
//                    wrapper.in(AfterSaleTicket::getStatus, statusList);
//                }
//            }
//            if (startDate != null && !startDate.isBlank()) {
//                wrapper.ge(AfterSaleTicket::getDepartureDate, startDate);
//            }
//            if (endDate != null && !endDate.isBlank()) {
//                wrapper.le(AfterSaleTicket::getDepartureDate, endDate);
//            }
//            wrapper.orderByAsc(AfterSaleTicket::getDepartureDate);
//            orders = ticketMapper.selectList(wrapper);
//        } else if (status != null && !status.isBlank()) {
//            List<AfterSaleTicketStatus> statusList = parseStatusList(status);
//            if (statusList == null) {
//                return JSON.toJSONString(java.util.Map.of("error",
//                        "无效的状态参数: " + status + "，可选值: DRAFT/SUBMITTED/APPROVED/REJECTED/COMPLETED/CANCELLED"));
//            }
//            if (statusList.size() == 1) {
//                orders = ticketRepository.findByUserIdAndStatus(userId, statusList.get(0));
//            } else {
//                LambdaQueryWrapper<AfterSaleTicket> wrapper = new LambdaQueryWrapper<AfterSaleTicket>()
//                        .eq(AfterSaleTicket::getUserId, userId)
//                        .in(AfterSaleTicket::getStatus, statusList)
//                        .orderByAsc(AfterSaleTicket::getDepartureDate);
//                orders = ticketMapper.selectList(wrapper);
//            }
//        } else {
//            orders = ticketRepository.findByUserId(userId);
//        }
//
//        String result = JSON.toJSONString(orders);
//        if (orders != null && orders.size() == 1) {
//            sessionCtx.setCachedOrders(orders);
//            logger.info("[TOOL][query_after_sale_ticket] result={}", result);
//            return result;
//        } else {
//            // 提示查询到多条，需要选择其中一条
//            return JSON.toJSONString(java.util.Map.of("error", "查询到多条售后工单，请指定 order_id", "orders", JSON.toJSONString(orders)));
//        }
//    }


    @Tool(name = "query_after_sale_ticket", description = "查询用户服务工单列表或指定工单详情。支持按状态、申请日期范围过滤。（底层为售后工单表，电商场景下按服务工单语义使用）")
    public String queryAfterSaleTicket(
            AgentSessionContext sessionCtx,
            @ToolParam(name = "origin", description = "出发地城市，如'上海'")
            String origin,
            @ToolParam(name = "destination", description = "目的地城市，如'杭州'")
            String destination,
            @ToolParam(name = "departure_date", description = "出发日期，格式 YYYY-MM-DD")
            String departureDate) {

        String userId = sessionCtx.getUserId();
        logger.info("[TOOL][query_after_sale_ticket] userId={}, origin={}, destination={}, departureDate={}",
                userId, origin, destination, departureDate);

        LambdaQueryWrapper<AfterSaleTicket> wrapper = new LambdaQueryWrapper<AfterSaleTicket>()
                .eq(AfterSaleTicket::getUserId, userId)
                .eq(AfterSaleTicket::getDepartureCity, origin)
                .eq(AfterSaleTicket::getDestination, destination)
                .eq(AfterSaleTicket::getDepartureDate, departureDate);

        AfterSaleTicket ticket = ticketMapper.selectOne(wrapper);

        if (ticket != null) {
            if(!ticket.getUserId().equals(userId)){
                return JSON.toJSONString(java.util.Map.of("error", "无权限访问该订单"));
            }
            sessionCtx.setCachedOrder(ticket);
        }
        String result = JSON.toJSONString(ticket != null ? ticket : "{\"error\":\"order not found\"}");
        logger.info("[TOOL][query_after_sale_ticket] result={}", result);
        return result;
    }

    @Tool(name = "query_after_sale_ticket_by_order_id", description = "根据工单编号，查询指定工单详情。")
    public String queryAfterSaleTicketByOrderId(
            AgentSessionContext sessionCtx,
            @ToolParam(name = "order_id", description = "售后工单ID") String orderId) {

        String userId = sessionCtx.getUserId();
        logger.info("[TOOL][query_after_sale_ticket_by_order_id] userId={}, orderId={}", userId, orderId);

        AfterSaleTicket order = ticketRepository.findByOrderId(orderId).orElse(null);
        if (order != null) {
            if(!order.getUserId().equals(userId)){
                return JSON.toJSONString(java.util.Map.of("error", "无权限访问该订单"));
            }
            sessionCtx.setCachedOrder(order);
        }

        String result = JSON.toJSONString(order != null ? order : "{\"error\":\"order not found\"}");
        logger.info("[TOOL][query_after_sale_ticket_by_order_id] result={}", result);
        return result;
    }

    @Tool(name = "query_after_sale_tickets", description = "查询用户服务工单列表。支持按状态、申请日期范围过滤。")
    public String queryAfterSaleTickets(
            AgentSessionContext sessionCtx,
            @ToolParam(name = "status", description = "状态过滤，支持单个或逗号分隔的多个状态，如 DRAFT 或 DRAFT,SUBMITTED。可选值: DRAFT/SUBMITTED/APPROVED/REJECTED/COMPLETED/CANCELLED", required = false) String status,
            @ToolParam(name = "start_date", description = "筛选出发日期不早于此日期的售后工单，格式 YYYY-MM-DD，可选", required = false) String startDate,
            @ToolParam(name = "end_date", description = "筛选出发日期不晚于此日期的售后工单，格式 YYYY-MM-DD，可选", required = false) String endDate) {

        String userId = sessionCtx.getUserId();

        List<AfterSaleTicket> orders;
        boolean hasDateFilter = (startDate != null && !startDate.isBlank())
                || (endDate != null && !endDate.isBlank());

        if (hasDateFilter) {
            LambdaQueryWrapper<AfterSaleTicket> wrapper = new LambdaQueryWrapper<AfterSaleTicket>()
                    .eq(AfterSaleTicket::getUserId, userId);
            if (status != null && !status.isBlank()) {
                List<AfterSaleTicketStatus> statusList = parseStatusList(status);
                if (statusList == null) {
                    return JSON.toJSONString(java.util.Map.of("error",
                            "无效的状态参数: " + status + "，可选值: DRAFT/SUBMITTED/APPROVED/REJECTED/COMPLETED/CANCELLED"));
                }
                if (statusList.size() == 1) {
                    wrapper.eq(AfterSaleTicket::getStatus, statusList.get(0));
                } else {
                    wrapper.in(AfterSaleTicket::getStatus, statusList);
                }
            }
            if (startDate != null && !startDate.isBlank()) {
                wrapper.le(AfterSaleTicket::getDepartureDate, endDate != null && !endDate.isBlank() ? endDate : startDate);
            }
            if (endDate != null && !endDate.isBlank()) {
                wrapper.ge(AfterSaleTicket::getReturnDate, startDate);
            }
            wrapper.orderByAsc(AfterSaleTicket::getDepartureDate);
            orders = ticketMapper.selectList(wrapper);
        } else if (status != null && !status.isBlank()) {
            List<AfterSaleTicketStatus> statusList = parseStatusList(status);
            if (statusList == null) {
                return JSON.toJSONString(java.util.Map.of("error",
                        "无效的状态参数: " + status + "，可选值: DRAFT/SUBMITTED/APPROVED/REJECTED/COMPLETED/CANCELLED"));
            }
            if (statusList.size() == 1) {
                orders = ticketRepository.findByUserIdAndStatus(userId, statusList.get(0));
            } else {
                LambdaQueryWrapper<AfterSaleTicket> wrapper = new LambdaQueryWrapper<AfterSaleTicket>()
                        .eq(AfterSaleTicket::getUserId, userId)
                        .in(AfterSaleTicket::getStatus, statusList)
                        .orderByAsc(AfterSaleTicket::getDepartureDate);
                orders = ticketMapper.selectList(wrapper);
            }
        } else {
            orders = ticketRepository.findByUserId(userId);
        }

        String result = JSON.toJSONString(orders);
        logger.info("[TOOL][query_after_sale_tickets] result={}", result);
        return result;
    }


    // ─────────────────────────────────────────────────────────────────────────
    // 查询审批状态
    // ─────────────────────────────────────────────────────────────────────────

    @Tool(name = "query_approval_status", description = "查询服务工单的处理状态。若提供工单编号则按编号查询；否则按用户ID查询其最近的工单")
    public String queryApprovalStatus(AgentSessionContext sessionCtx, @ToolParam(name = "process_instance_id", description = "审批实例ID，可选", required = false) String processInstanceId) {

        String userId = sessionCtx.getUserId();
        logger.info("[TOOL][query_approval_status] userId={}, processInstanceId={}", userId, processInstanceId);

        if (isBlank(userId)) {
            return toJson(AfterSaleTicketWriteTools.ErrorResult.of("INVALID_PARAM", "user_id 不能为空"));
        }

        try {
            Optional<ApprovalRecord> recordOpt;
            String queryMode;
            if (!isBlank(processInstanceId)) {
                recordOpt = approvalService.findByProcessInstanceId(processInstanceId);
                queryMode = "by_instance";
            } else {
                recordOpt = approvalService.findLatestByUserId(userId);
                queryMode = "latest";
            }

            if (recordOpt.isEmpty()) {
                return toJson(new AfterSaleTicketWriteTools.QueryApprovalResult(false, queryMode, null, null, null, null, null, null, null, null, "未找到相关审批记录。"));
            }

            ApprovalRecord record = recordOpt.get();
            logger.info("[TOOL][query_approval_status] 查询到审批单 processInstanceId={}, status={}", record.getProcessInstanceId(), record.getStatus());

            return toJson(new AfterSaleTicketWriteTools.QueryApprovalResult(true, queryMode, record.getProcessInstanceId(), record.getOrderId(), record.getUserId(), record.getTitle(), record.getStatus().getCode(), record.getSubmitTime() != null ? record.getSubmitTime().toString() : null, record.getUpdateTime() != null ? record.getUpdateTime().toString() : null, record.getRemark(), null));

        } catch (Exception e) {
            logger.error("[TOOL][query_approval_status] 查询失败 userId={}", userId, e);
            return toJson(AfterSaleTicketWriteTools.ErrorResult.of("INTERNAL_ERROR", "查询审批状态失败：" + e.getMessage()));
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 将强类型结果对象序列化为 JSON 字符串
     */
    private static String toJson(Object obj) {
        return JSON.toJSONString(obj);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 行程时间有效性检查
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 检查用户已审批售后工单的日期有效性，用于行程规划前的前置校验。
     *
     * <p>判断逻辑：
     * <ul>
     *   <li>无 APPROVED 售后工单 → {@code valid=true}（可规划，只是还没有审批单）</li>
     *   <li>当前日期 > 返回日期 → 行程已结束，{@code valid=false}</li>
     *   <li>当前日期 >= 出发日期 → 行程已开始，{@code valid=false}</li>
     *   <li>当前日期 < 出发日期 → 行程尚未开始，{@code valid=true}</li>
     * </ul>
     *
     * @param sessionCtx 会话上下文（自动注入）
     * @return JSON：{@code {valid, today, blocked_orders:[{order_id, destination, departure_date, return_date, status}], message}}
     */
    @Tool(name = "check_time_validity",
            description = "检查用户售后工单的行程时间是否仍然有效（未开始/未结束），用于行程规划前的前置校验。" +
                    "返回 valid=true 表示存在可规划的行程，valid=false 表示行程已开始或已结束、无法规划。")
    public String checkTravelTimeValidity(AgentSessionContext sessionCtx, @ToolParam(name = "order_id", description = "售后工单ID") String orderId) {
        String userId = sessionCtx.getUserId();
        LocalDate today = LocalDate.now();

        logger.info("[TOOL][check_time_validity] userId={}, today={}", userId, today);

        AfterSaleTicket ticket = sessionCtx.getCachedOrder() == null ? ticketRepository.findByOrderId(orderId).orElse(null) : null;

        if (ticket != null) {
            sessionCtx.setCachedOrder(ticket);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("today", today.toString());
        if (ticket == null) {
            result.put("valid", true);
            result.put("blocked_orders", List.of());
            result.put("message", "当前尚不存在售后工单，但是仍可直接规划行程。");
            logger.info("[TOOL][check_time_validity] userId={}, 无 APPROVED 售后工单", userId);
            return JSON.toJSONString(result);
        }

        String blockReason = evaluateBlockReason(ticket, today);
        List<Map<String, Object>> blockedOrders = new ArrayList<>();
        if (blockReason != null) {
            Map<String, Object> blocked = new LinkedHashMap<>();
            blocked.put("order_id", ticket.getOrderId());
            blocked.put("destination", ticket.getDestination());
            blocked.put("departure_date", ticket.getDepartureDate());
            blocked.put("return_date", ticket.getReturnDate());
            blocked.put("reason", blockReason);
            blockedOrders.add(blocked);
        }

        if (!blockedOrders.isEmpty()) {
            result.put("valid", false);
            result.put("blocked_orders", blockedOrders);
            String firstReason = (String) blockedOrders.get(0).get("reason");
            result.put("message", String.format("检测到%s的售后工单，无法进行规划。请告知用户并停止规划。", firstReason));
            logger.info("[TOOL][check_time_validity] userId={}, 检测到 {} 个过期售后工单",
                    userId, blockedOrders.size());
        } else {
            result.put("valid", true);
            result.put("blocked_orders", List.of());
            result.put("message", "售后工单日期均有效，可继续规划。");
            logger.info("[TOOL][check_time_validity] userId={}, 所有 APPROVED 售后工单日期有效", userId);
        }

        return JSON.toJSONString(result);
    }

    /**
     * 评估售后工单是否因时间原因被拦截。
     *
     * @return 拦截原因描述，null 表示日期有效不拦截
     */
    private String evaluateBlockReason(AfterSaleTicket order, LocalDate today) {
        try {
            if (order.getReturnDate() != null) {
                LocalDate returnDate = LocalDate.parse(order.getReturnDate());
                if (today.isAfter(returnDate)) {
                    return String.format("行程已结束（返回日期 %s 已过）", order.getReturnDate());
                }
            }
            if (order.getDepartureDate() != null) {
                LocalDate departureDate = LocalDate.parse(order.getDepartureDate());
                if (!today.isBefore(departureDate)) {
                    return String.format("行程已开始（出发日期 %s 已到达或已过）", order.getDepartureDate());
                }
            }
        } catch (DateTimeParseException e) {
            logger.debug("[TOOL][check_time_validity] 日期解析失败: orderId={}, error={}", order.getOrderId(), e.getMessage());
        }
        return null;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 审批状态检查（供 OrderAgent 使用）
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 检查用户是否存在已审批通过的售后工单，用于预订执行前的前置校验。
     *
     * <p>判断逻辑：
     * <ul>
     *   <li>存在 APPROVED 售后工单 → {@code approved=true}，返回第一条审批单信息</li>
     *   <li>不存在 APPROVED 售后工单 → {@code approved=false}，拒绝预订</li>
     * </ul>
     *
     * @param sessionCtx 会话上下文（自动注入）
     * @return JSON：{@code {approved, order_id, destination, departure_date, return_date, message}}
     */
    @Tool(name = "check_after_sale_ticket_approval",
            description = "检查用户是否存在已审批通过的售后工单，用于预订执行前的前置校验。" +
                    "返回 approved=true 表示存在已审批售后工单、可以执行预订，approved=false 表示无审批单、应拒绝预订。")
    public String checkAfterSaleTicketApproval(AgentSessionContext sessionCtx, @ToolParam(name = "order_id", description = "售后工单ID") String orderId) {
        String userId = sessionCtx.getUserId();
        logger.info("[TOOL][check_after_sale_ticket_approval] userId={}", userId);

        // 优先复用已缓存的 APPROVED 售后工单
        AfterSaleTicket ticket = sessionCtx.getCachedOrder() == null ? ticketRepository.findByOrderId(orderId).orElse(null) : null;

        if (ticket != null) {
            sessionCtx.setCachedOrder(ticket);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        if (ticket == null) {
            result.put("approved", false);
            result.put("message", "未查询到您需要预订的售后工单，请提供一个售后工单号或者告知我出发时间、出发地、目的地等信息。");
            return JSON.toJSONString(result);
        }

        if (ticket.getStatus() != AfterSaleTicketStatus.APPROVED) {
            result.put("approved", false);
            result.put("message", "您想要预订的售后尚未完成审批，请先联系管理员审批通过。或者换一个新的售后行程帮您做预订。");
            return JSON.toJSONString(result);
        }

        result.put("approved", true);
        result.put("order_id", ticket.getOrderId());
        result.put("destination", ticket.getDestination());
        result.put("departure_date", ticket.getDepartureDate());
        result.put("return_date", ticket.getReturnDate());
        result.put("message", String.format("存在已审批售后工单（%s → %s），可以执行预订。",
                ticket.getDepartureCity(), ticket.getDestination()));
        logger.info("[TOOL][check_after_sale_ticket_approval] userId={}, 找到 APPROVED 售后工单 orderId={}",
                userId, ticket.getOrderId());

        return JSON.toJSONString(result);
    }

    /**
     * 解析状态参数，支持单个状态或逗号分隔的多个状态。
     * 返回 null 表示存在无效状态值。
     */
    private List<AfterSaleTicketStatus> parseStatusList(String status) {
        String[] parts = status.split(",");
        List<AfterSaleTicketStatus> list = new ArrayList<>(parts.length);
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty()) continue;
            AfterSaleTicketStatus s = AfterSaleTicketStatus.of(trimmed);
            if (s == null) {
                return null;
            }
            list.add(s);
        }
        return list.isEmpty() ? null : list;
    }
}
