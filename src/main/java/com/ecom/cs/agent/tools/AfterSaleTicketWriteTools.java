package com.ecom.cs.agent.tools;

import com.alibaba.fastjson2.JSON;
import com.ecom.cs.agent.context.AgentSessionContext;
import com.ecom.cs.business.approval.entity.ApprovalRecord;
import com.ecom.cs.business.approval.service.ApprovalService;
import com.ecom.cs.business.order.entity.AfterSaleTicket;
import com.ecom.cs.business.order.entity.AfterSaleTicketStatus;
import com.ecom.cs.business.order.entity.EcommerceOrder;
import com.ecom.cs.business.order.entity.OrderStatus;
import com.ecom.cs.business.order.entity.OrderType;
import com.ecom.cs.business.order.repo.AfterSaleTicketRepository;
import com.ecom.cs.business.order.repo.EcommerceOrderRepository;
import com.ecom.cs.business.order.service.AfterSaleTicketService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 售后工单全生命周期工具集：提交审批、查询审批状态、撤回工单、修改工单。
 * 多步写操作统一委托给 AfterSaleTicketService 在事务内执行，本类只负责参数校验、幂等/状态检查与 JSON 包装。
 *
 * @author Hollis
 */
@Component
public class AfterSaleTicketWriteTools {

    private static final Logger logger = LoggerFactory.getLogger(AfterSaleTicketWriteTools.class);

    /**
     * 日期格式校验：YYYY-MM-DD
     */
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    @Autowired
    private AfterSaleTicketRepository ticketRepository;
    @Autowired
    private AfterSaleTicketService ticketService;
    @Autowired
    private ApprovalService approvalService;
    @Autowired
    private EcommerceOrderRepository orderRepository;

    // ─────────────────────────────────────────────────────────────────────────
    // 强类型返回结构（Java Record）
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 通用失败响应
     */
    record ErrorResult(boolean success, String errorCode, String message) {
        static ErrorResult of(String errorCode, String message) {
            return new ErrorResult(false, errorCode, message);
        }
    }

    /**
     * 需用户二次确认的拦截响应
     */
    record ConfirmRequiredResult(boolean success, boolean needConfirm, String orderId, String currentStatus,
                                 String message) {
    }

    /**
     * submit_after_sale_ticket 成功响应
     */
    record SubmitResult(boolean success, String orderId, String processInstanceId, String orderStatus,
                        String approvalStatus, String submitTime, String productName, String issueDesc,
                        String purchaseDate, String message) {
    }

    /**
     * query_approval_status 响应（found=false 时业务字段为 null）
     */
    record QueryApprovalResult(boolean found, String queryMode, String processInstanceId, String orderId, String userId,
                               String title, String status, String submitTime, String updateTime, String remark,
                               String message) {
    }

    /**
     * 关联订单摘要（用于在撤回/修改结果中告知 LLM 受影响的订单）
     */
    record OrderSummary(String orderNo, String bizType, String title, String status, String externalOrderNo) {
    }

    /**
     * cancel_after_sale_ticket 成功响应
     */
    record CancelResult(boolean success, String orderId, String orderStatus, boolean approvalCancelled,
                        String cancelledApprovalId, String reason, String message,
                        List<OrderSummary> associatedOrders, String affectedOrdersMessage) {
    }

    /**
     * modify_after_sale_ticket 成功响应
     */
    record ModifyResult(boolean success, String orderId, String orderStatus, String oldApprovalId,
                        boolean oldApprovalCancelled, String newApprovalId, String newApprovalStatus,
                        String updatedFields, String message,
                        List<OrderSummary> associatedOrders, String affectedOrdersMessage) {
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 提交审批（同时创建售后工单 + 审批单，双向关联）
    // ─────────────────────────────────────────────────────────────────────────

    @Tool(name = "submit_after_sale_ticket",
            description = "一次性创建售后工单和审批记录，并将两者双向关联。"
                    + "执行步骤：① 根据用户诉求生成工单（DRAFT）；"
                    + "② 提交审批记录（PENDING）并绑定工单ID；"
                    + "③ 将工单状态更新为 SUBMITTED 并写入审批记录ID。"
                    + "返回工单ID（order_id）和审批记录ID（process_instance_id）。"
                    + "purchase_date 必须为 YYYY-MM-DD 格式（例如 2026-07-15），否则会返回参数错误。")
    public String submitAfterSaleTicket(AgentSessionContext sessionCtx,
                                        @ToolParam(name = "product_name", description = "申请售后的商品或服务名称，含规格与数量，如「主动降噪无线蓝牙耳机 Pro 黑色 1件」") String productName,
                                        @ToolParam(name = "issue_desc", description = "问题描述与期望处理方式，如「右耳偶发断连，申请换货」") String issueDesc,
                                        @ToolParam(name = "purchase_date", description = "购买日期，必须为 YYYY-MM-DD 格式，例如 2026-07-15") String purchaseDate) {

        String userId = sessionCtx.getUserId();
        logger.info("[TOOL][submit_after_sale_ticket] userId={}, productName={}, purchaseDate={}",
                userId, productName, purchaseDate);

        // ── 入参校验 ──────────────────────────────────────────────────────────
        List<String> errors = new ArrayList<>();
        if (isBlank(userId)) {
            errors.add("user_id 不能为空");
        }
        if (isBlank(productName)) {
            errors.add("product_name（商品名称）不能为空");
        }
        if (isBlank(issueDesc)) {
            errors.add("issue_desc（问题描述）不能为空");
        }

        String purchaseDateErr = validateDate(purchaseDate, "purchase_date（购买日期）");
        if (purchaseDateErr != null) {
            errors.add(purchaseDateErr);
        }

        if (!errors.isEmpty()) {
            return toJson(ErrorResult.of("INVALID_PARAM", String.join("；", errors)));
        }

        // 购买日期不允许晚于今天
        if (LocalDate.parse(purchaseDate).isAfter(LocalDate.now())) {
            return toJson(ErrorResult.of("INVALID_DATE",
                    "购买日期（" + purchaseDate + "）不能晚于今天，请核对后重新提交"));
        }

        // ── 业务逻辑 ──────────────────────────────────────────────────────────
        try {
            // ⓪ 幂等校验：相同用户 + 相同商品 + 相同问题若已存在生效中的工单，直接返回已有单，避免重复创建
            AfterSaleTicket duplicate = findDuplicateActiveTicket(userId, productName, issueDesc, purchaseDate);
            if (duplicate != null) {
                logger.info("[TOOL][submit_after_sale_ticket] 命中幂等：已存在生效中的售后工单 orderId={}，跳过重复创建",
                        duplicate.getOrderId());
                sessionCtx.setCachedOrder(duplicate);
                return buildExistingSubmitResult(duplicate);
            }

            // ①②③ 创建售后工单 + 提交审批单 + 双向关联，由事务保证原子性（任一步失败整体回滚）
            AfterSaleTicket ticket = AfterSaleTicket.builder()
                    .userId(userId)
                    .productName(productName)
                    .issueDesc(issueDesc)
                    .purchaseDate(purchaseDate)
                    .build();
            ApprovalRecord record = ticketService.createAndSubmit(ticket);
            String orderId = ticket.getOrderId();
            String processInstanceId = record.getProcessInstanceId();
            sessionCtx.setCachedOrder(ticket);

            // ④ 事务提交后查库验证写入成功
            String submitVerifyErr = verifyTicketStatus(orderId, AfterSaleTicketStatus.SUBMITTED, "submit_after_sale_ticket");
            if (submitVerifyErr != null) {
                return submitVerifyErr;
            }

            return toJson(new SubmitResult(true, orderId, processInstanceId,
                    AfterSaleTicketStatus.SUBMITTED.getCode(), record.getStatus().getCode(),
                    record.getSubmitTime().toString(), productName, issueDesc, purchaseDate,
                    String.format("售后工单已创建并提交。工单编号: %s，审批单号: %s，当前状态: 待处理。",
                            orderId, processInstanceId)));

        } catch (Exception e) {
            logger.error("[TOOL][submit_after_sale_ticket] 操作失败 userId={}", userId, e);
            return toJson(ErrorResult.of("INTERNAL_ERROR", "创建售后工单失败：" + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 撤回售后工单
    // ─────────────────────────────────────────────────────────────────────────
    @Tool(name = "cancel_after_sale_ticket",
            description = "撤回售后工单：将工单状态设为 CANCELLED，同时撤销关联的审批记录（若存在）。"
                    + "仅 DRAFT/SUBMITTED 状态可直接撤回；APPROVED 状态会给出警告并要求用户二次确认（force=true）。")
    public String cancelAfterSaleTicket(AgentSessionContext sessionCtx,
                                        @ToolParam(name = "order_id", description = "要撤回的售后工单ID") String orderId,
                                        @ToolParam(name = "reason", description = "撤回原因，可选", required = false) String reason,
                                        @ToolParam(name = "force", description = "是否强制撤回已审批的售后工单，默认 false", required = false) Boolean force) {

        String userId = sessionCtx.getUserId();
        logger.info("[TOOL][cancel_after_sale_ticket] userId={}, orderId={}, force={}", userId, orderId, force);

        if (isBlank(userId)) {
            return toJson(ErrorResult.of("INVALID_PARAM", "user_id 不能为空"));
        }
        if (isBlank(orderId)) {
            return toJson(ErrorResult.of("INVALID_PARAM", "order_id 不能为空"));
        }

        try {
            Optional<AfterSaleTicket> ticketOpt = ticketRepository.findByOrderId(orderId);
            if (ticketOpt.isEmpty()) {
                return toJson(ErrorResult.of("ORDER_NOT_FOUND", "工单不存在：" + orderId));
            }

            AfterSaleTicket ticket = ticketOpt.get();
            if (!userId.equals(ticket.getUserId())) {
                return toJson(ErrorResult.of("PERMISSION_DENIED", "无权操作该工单，归属用户不匹配。"));
            }

            AfterSaleTicketStatus currentStatus = ticket.getStatus();
            if (AfterSaleTicketStatus.CANCELLED == currentStatus) {
                return toJson(ErrorResult.of("INVALID_STATE", "工单已处于撤回状态，无需重复撤回。"));
            }
            if (AfterSaleTicketStatus.COMPLETED == currentStatus) {
                return toJson(ErrorResult.of("INVALID_STATE", "已完结的工单不可撤回。"));
            }
            if (AfterSaleTicketStatus.APPROVED == currentStatus && !Boolean.TRUE.equals(force)) {
                return toJson(new ConfirmRequiredResult(false, true, orderId, currentStatus.getCode(),
                        "该工单正在处理中，撤回后不可恢复且可能影响关联事项。请用户明确确认后再操作（传入 force=true）。"));
            }

            // 撤回售后工单并同步撤销关联审批单，由事务保证原子性
            AfterSaleTicketService.CancelOutcome outcome = ticketService.cancelWithApproval(ticket);
            if (!outcome.orderCancelled()) {
                return toJson(ErrorResult.of("UPDATE_FAILED", "售后工单状态更新失败，请稍后重试。"));
            }
            boolean approvalCancelled = outcome.approvalCancelled();
            String cancelledApprovalId = outcome.cancelledApprovalId();

            String msg = "售后工单已撤回。" + (approvalCancelled ? "关联审批记录已同步撤销。" : "");
            logger.info("[TOOL][cancel_after_sale_ticket] orderId={} 已撤回，关联审批撤销={}", orderId, approvalCancelled);

            // 事务提交后查库验证撤回状态
            String cancelVerifyErr = verifyTicketStatus(orderId, AfterSaleTicketStatus.CANCELLED, "cancel_after_sale_ticket");
            if (cancelVerifyErr != null) {
                return cancelVerifyErr;
            }

            // 被撤回的单若正是会话当前关联的工单，清空关联，
            // 避免同一请求内后续订单被 OrderPersistenceHook 挂到已撤回的单上
            if (orderId.equals(sessionCtx.getTicketId())) {
                sessionCtx.setTicketId(null);
            }

            List<OrderSummary> associatedOrders = queryAssociatedOrders(userId, orderId);
            String affectedMsg = buildAffectedOrdersMessage(associatedOrders);

            return toJson(new CancelResult(true, orderId, AfterSaleTicketStatus.CANCELLED.getCode(),
                    approvalCancelled, cancelledApprovalId, reason, msg, associatedOrders, affectedMsg));

        } catch (Exception e) {
            logger.error("[TOOL][cancel_after_sale_ticket] 撤回失败 orderId={}", orderId, e);
            return toJson(ErrorResult.of("INTERNAL_ERROR", "撤回售后工单失败：" + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 修改售后工单
    // ─────────────────────────────────────────────────────────────────────────

    @Tool(name = "modify_after_sale_ticket",
            description = "修改售后工单：更新工单中的一个或多个字段（商品名称/问题描述/购买日期），"
                    + "同时撤销旧审批记录并发起新的审批流程。"
                    + "APPROVED 状态的工单修改需传入 force=true 并获得用户明确确认。"
                    + "若提供购买日期，必须为 YYYY-MM-DD 格式，否则返回参数错误。")
    public String modifyAfterSaleTicket(AgentSessionContext sessionCtx,
                                        @ToolParam(name = "order_id", description = "要修改的工单ID") String orderId,
                                        @ToolParam(name = "product_name", description = "新的商品或服务名称，可选", required = false) String productName,
                                        @ToolParam(name = "issue_desc", description = "新的问题描述与期望处理方式，可选", required = false) String issueDesc,
                                        @ToolParam(name = "purchase_date", description = "新的购买日期，YYYY-MM-DD 格式，可选", required = false) String purchaseDate,
                                        @ToolParam(name = "force", description = "是否强制修改处理中的工单，默认 false", required = false) Boolean force) {

        String userId = sessionCtx.getUserId();
        logger.info("[TOOL][modify_after_sale_ticket] userId={}, orderId={}", userId, orderId);

        if (isBlank(userId)) {
            return toJson(ErrorResult.of("INVALID_PARAM", "user_id 不能为空"));
        }
        if (isBlank(orderId)) {
            return toJson(ErrorResult.of("INVALID_PARAM", "order_id 不能为空"));
        }

        // 日期字段若提供则必须符合格式
        if (!isBlank(purchaseDate)) {
            String err = validateDate(purchaseDate, "purchase_date（购买日期）");
            if (err != null) {
                return toJson(ErrorResult.of("INVALID_DATE", err));
            }
            if (LocalDate.parse(purchaseDate).isAfter(LocalDate.now())) {
                return toJson(ErrorResult.of("INVALID_DATE",
                        "购买日期（" + purchaseDate + "）不能晚于今天，请核对后重新提交"));
            }
        }

        try {
            Optional<AfterSaleTicket> ticketOpt = ticketRepository.findByOrderId(orderId);
            if (ticketOpt.isEmpty()) {
                return toJson(ErrorResult.of("ORDER_NOT_FOUND", "工单不存在：" + orderId));
            }

            AfterSaleTicket ticket = ticketOpt.get();
            if (!userId.equals(ticket.getUserId())) {
                return toJson(ErrorResult.of("PERMISSION_DENIED", "无权操作该工单，归属用户不匹配。"));
            }

            AfterSaleTicketStatus currentStatus = ticket.getStatus();
            if (AfterSaleTicketStatus.CANCELLED == currentStatus) {
                return toJson(ErrorResult.of("INVALID_STATE", "已撤回的工单不可修改，请重新提交新的工单。"));
            }
            if (AfterSaleTicketStatus.COMPLETED == currentStatus) {
                return toJson(ErrorResult.of("INVALID_STATE", "已完结的工单不可修改。"));
            }
            if (AfterSaleTicketStatus.APPROVED == currentStatus && !Boolean.TRUE.equals(force)) {
                return toJson(new ConfirmRequiredResult(false, true, orderId, currentStatus.getCode(),
                        "该工单正在处理中，修改将撤销当前处理进度并重新发起流程，请用户明确确认后继续（传入 force=true）。"));
            }

            // 幂等/空操作短路：所有提交字段与当前值一致时，无需撤审重提，直接返回
            boolean noChange = (isBlank(productName) || equalsSafe(productName, ticket.getProductName()))
                    && (isBlank(issueDesc) || equalsSafe(issueDesc, ticket.getIssueDesc()))
                    && (isBlank(purchaseDate) || equalsSafe(purchaseDate, ticket.getPurchaseDate()));
            if (noChange) {
                logger.info("[TOOL][modify_after_sale_ticket] 命中幂等：提交字段与当前值一致，orderId={}，跳过撤审重提", orderId);
                String currentApprovalId = ticket.getApprovalId();
                return toJson(new ModifyResult(true, orderId, ticket.getStatus().getCode(), currentApprovalId,
                        false, currentApprovalId, null, "无字段变更",
                        "提交的信息与当前售后工单一致，未做任何修改。", List.of(), null));
            }

            // 更新有变化的字段
            if (!isBlank(productName)) {
                ticket.setProductName(productName);
            }
            if (!isBlank(issueDesc)) {
                ticket.setIssueDesc(issueDesc);
            }
            if (!isBlank(purchaseDate)) {
                ticket.setPurchaseDate(purchaseDate);
            }

            // 撤销旧审批单 + 保存字段变更 + 提交新审批单 + 关联新审批，由事务保证原子性
            AfterSaleTicketService.ModifyOutcome outcome = ticketService.modifyAndResubmit(ticket);
            String oldApprovalId = outcome.oldApprovalId();
            boolean oldApprovalCancelled = outcome.oldApprovalCancelled();
            ApprovalRecord newRecord = outcome.newApproval();

            // 事务提交后查库验证修改后状态
            String modifyVerifyErr = verifyTicketStatus(orderId, AfterSaleTicketStatus.SUBMITTED, "modify_after_sale_ticket");
            if (modifyVerifyErr != null) {
                return modifyVerifyErr;
            }

            String updatedFields = buildUpdatedFields(productName, issueDesc, purchaseDate);
            logger.info("[TOOL][modify_after_sale_ticket] orderId={} 修改完成，新审批={}", orderId,
                    newRecord.getProcessInstanceId());

            List<OrderSummary> associatedOrders = queryAssociatedOrders(userId, orderId);
            String affectedMsg = buildAffectedOrdersMessage(associatedOrders);

            return toJson(new ModifyResult(true, orderId, AfterSaleTicketStatus.SUBMITTED.getCode(),
                    oldApprovalId, oldApprovalCancelled, newRecord.getProcessInstanceId(),
                    newRecord.getStatus().getCode(), updatedFields,
                    "售后工单已修改，旧审批记录已撤销，新审批记录已重新提交，等待处理结果。",
                    associatedOrders, affectedMsg));

        } catch (Exception e) {
            logger.error("[TOOL][modify_after_sale_ticket] 修改失败 orderId={}", orderId, e);
            return toJson(ErrorResult.of("INTERNAL_ERROR", "修改售后工单失败：" + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 私有工具方法
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * 将强类型结果对象序列化为 JSON 字符串
     */
    private static String toJson(Object obj) {
        return JSON.toJSONString(obj);
    }

    /**
     * 校验日期字段格式（YYYY-MM-DD）。
     *
     * @return null 表示合法；否则返回错误描述
     */
    private static String validateDate(String date, String fieldName) {
        if (date == null || date.isBlank()) {
            return fieldName + " 不能为空";
        }
        if (!DATE_PATTERN.matcher(date).matches()) {
            return fieldName + " 格式错误，必须为 YYYY-MM-DD（如 2026-07-15），实际值为：" + date;
        }
        try {
            LocalDate.parse(date);
            return null;
        } catch (DateTimeParseException e) {
            return fieldName + " 不是有效的日期，必须为 YYYY-MM-DD，实际值为：" + date;
        }
    }

    /**
     * 汇总已修改的字段名称
     */
    private static String buildUpdatedFields(String productName, String issueDesc, String purchaseDate) {
        List<String> fields = new ArrayList<>();
        if (!isBlank(productName)) {
            fields.add("商品名称");
        }
        if (!isBlank(issueDesc)) {
            fields.add("问题描述");
        }
        if (!isBlank(purchaseDate)) {
            fields.add("购买日期");
        }
        return fields.isEmpty() ? "无字段变更" : String.join("、", fields);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * 查询工单关联的有效订单记录（排除已取消/已退款）
     */
    private List<OrderSummary> queryAssociatedOrders(String userId, String orderId) {
        List<EcommerceOrder> records = orderRepository.findByUserIdAndTicketId(userId, orderId);
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        return records.stream()
                .filter(r -> r.getStatus() != OrderStatus.CANCELLED && r.getStatus() != OrderStatus.REFUNDED)
                .map(r -> new OrderSummary(
                        r.getOrderNo(),
                        r.getBizType() != null ? r.getBizType().getCode() : null,
                        r.getTitle(),
                        r.getStatus() != null ? r.getStatus().getCode() : null,
                        r.getExternalOrderNo()))
                .toList();
    }

    /**
     * 根据关联订单生成提示信息，引导 LLM 告知用户受影响的订单
     */
    private String buildAffectedOrdersMessage(List<OrderSummary> orders) {
        if (orders == null || orders.isEmpty()) {
            return null;
        }
        long goods = orders.stream().filter(o -> OrderType.GOODS.getCode().equals(o.bizType())).count();
        StringBuilder sb = new StringBuilder();
        sb.append("该工单关联 ").append(orders.size()).append(" 笔订单（");
        List<String> parts = new ArrayList<>();
        if (goods > 0) {
            parts.add("商品订单" + goods + "笔");
        }
        long others = orders.size() - goods;
        if (others > 0) {
            parts.add("其他订单" + others + "笔");
        }
        sb.append(String.join("、", parts));
        sb.append("），请提醒用户是否需要同步取消或退货退款。");
        return sb.toString();
    }

    /**
     * 空安全的字符串相等判断
     */
    private static boolean equalsSafe(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }

    /**
     * 幂等校验：查找商品、问题描述、购买日期完全一致且处于生效状态（DRAFT/SUBMITTED/APPROVED）的售后工单。
     * <p>命中说明是重复提交，返回已有单；无重复返回 {@code null}。
     */
    private AfterSaleTicket findDuplicateActiveTicket(String userId, String productName, String issueDesc,
                                                      String purchaseDate) {
        List<AfterSaleTicket> actives = ticketRepository.findActiveByUserId(userId,
                List.of(AfterSaleTicketStatus.DRAFT, AfterSaleTicketStatus.SUBMITTED, AfterSaleTicketStatus.APPROVED));
        for (AfterSaleTicket t : actives) {
            if (equalsSafe(t.getProductName(), productName)
                    && equalsSafe(t.getIssueDesc(), issueDesc)
                    && equalsSafe(t.getPurchaseDate(), purchaseDate)) {
                return t;
            }
        }
        return null;
    }

    /**
     * 基于已存在的售后工单构建 submit 幂等响应，补齐关联审批单信息。
     */
    private String buildExistingSubmitResult(AfterSaleTicket ticket) {
        String approvalId = ticket.getApprovalId();
        String approvalStatus = null;
        String submitTime = null;
        Optional<ApprovalRecord> approvalOpt = !isBlank(approvalId)
                ? approvalService.findByProcessInstanceId(approvalId)
                : approvalService.findLatestByOrderId(ticket.getOrderId());
        if (approvalOpt.isPresent()) {
            ApprovalRecord ar = approvalOpt.get();
            approvalId = ar.getProcessInstanceId();
            approvalStatus = ar.getStatus() != null ? ar.getStatus().getCode() : null;
            submitTime = ar.getSubmitTime() != null ? ar.getSubmitTime().toString() : null;
        }
        return toJson(new SubmitResult(true, ticket.getOrderId(), approvalId, ticket.getStatus().getCode(),
                approvalStatus, submitTime, ticket.getProductName(), ticket.getIssueDesc(), ticket.getPurchaseDate(),
                String.format("检测到相同商品与问题描述的售后工单已存在（工单编号: %s），已直接返回该工单，未重复创建。",
                        ticket.getOrderId())));
    }

    /**
     * 查库验证售后工单状态，确保写操作真正落库成功。
     * <p>验证通过返回 {@code null}；验证失败返回包含错误信息的 JSON 字符串，调用方可直接 return。
     *
     * @param orderId        售后工单 ID
     * @param expectedStatus 期望落库后的状态
     * @param toolName       工具名称（用于日志）
     */
    private String verifyTicketStatus(String orderId, AfterSaleTicketStatus expectedStatus, String toolName) {
        Optional<AfterSaleTicket> verifyOpt = ticketRepository.findByOrderId(orderId);
        if (verifyOpt.isEmpty()) {
            logger.error("[TOOL][{}] 验证失败：查库后售后工单不存在 orderId={}", toolName, orderId);
            return toJson(ErrorResult.of("VERIFY_FAILED",
                    "操作已执行但验证失败：售后工单 " + orderId + " 查询不到，请联系管理员。"));
        }
        AfterSaleTicketStatus actualStatus = verifyOpt.get().getStatus();
        if (actualStatus != expectedStatus) {
            logger.error("[TOOL][{}] 状态验证失败：期望 {}，实际 {} orderId={}", toolName,
                    expectedStatus.getCode(), actualStatus.getCode(), orderId);
            return toJson(ErrorResult.of("VERIFY_FAILED",
                    String.format("操作已执行但状态与预期不符，期望 %s，实际 %s，请稍后重试或联系管理员。",
                            expectedStatus.getCode(), actualStatus.getCode())));
        }
        logger.info("[TOOL][{}] 状态验证通过：orderId={}, status={}", toolName, orderId, actualStatus.getCode());
        return null;
    }
}
