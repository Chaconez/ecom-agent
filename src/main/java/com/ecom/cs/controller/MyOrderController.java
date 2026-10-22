package com.ecom.cs.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ecom.cs.agent.context.AgentSessionContext;
import com.ecom.cs.agent.tools.OrderWriteTools;
import com.ecom.cs.business.approval.entity.ApprovalRecord;
import com.ecom.cs.business.approval.service.ApprovalService;
import com.ecom.cs.business.order.entity.EcommerceOrder;
import com.ecom.cs.business.order.repo.EcommerceOrderRepository;
import com.ecom.cs.business.order.entity.AfterSaleTicket;
import com.ecom.cs.business.order.entity.AfterSaleTicketStatus;
import com.ecom.cs.business.order.repo.AfterSaleTicketRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 普通用户「我的订单」入口：聚合展示当前登录用户的订单（预订记录）与售后工单信息。
 *
 * <p>与后台客服的 {@link AdminApprovalController} 不同，本接口不做角色校验，
 * 仅按 {@code StpUtil.getLoginIdAsString()} 隔离数据租户。</p>
 *
 * @author Hollis
 */
@RestController
@RequestMapping("/api/my-orders")
@CrossOrigin(origins = "*")
public class MyOrderController {

    private static final Logger logger = LoggerFactory.getLogger(MyOrderController.class);


    @Autowired
    private AfterSaleTicketRepository ticketRepository;
    @Autowired
    private ApprovalService approvalService;
    @Autowired
    private EcommerceOrderRepository orderRepository;
    @Autowired
    private OrderWriteTools orderWriteTools;
    @Autowired
    private MinioClient minioClient;
    @Value("${app.minio.bucket}")
    private String minioBucket;

    /**
     * 查询当前登录用户的全部售后工单，含最新审批状态与订单摘要。
     * <p>按创建时间倒序返回，方便前端顶部优先展示最新申请。</p>
     */
    @GetMapping("/tickets")
    @SaCheckLogin
    public List<Map<String, Object>> listMyOrders() {
        String userId = StpUtil.getLoginIdAsString();
        List<AfterSaleTicket> orders = ticketRepository.findByUserId(userId);
        // 按创建时间倒序（null 视为最早）
        orders.sort(Comparator.comparing(AfterSaleTicket::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        List<Map<String, Object>> result = new ArrayList<>(orders.size());
        for (AfterSaleTicket order : orders) {
            result.add(toView(userId, order));
        }
        logger.info("[MyOrderController] userId={} 返回 {} 条工单", userId, result.size());
        return result;
    }

    /**
     * 查询当前登录用户的全部订单（电商订单平铺视图）。
     * <p>按下单时间倒序返回，供「我的订单」页面直接渲染。</p>
     */
    @GetMapping("/orders")
    @SaCheckLogin
    public List<Map<String, Object>> listMyOrdersFlat() {
        String userId = StpUtil.getLoginIdAsString();
        List<EcommerceOrder> orders = orderRepository.findByUserId(userId);
        // 按下单时间倒序（null 视为最早）
        orders.sort(Comparator.comparing(EcommerceOrder::getOrderedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));

        List<Map<String, Object>> result = new ArrayList<>(orders.size());
        for (EcommerceOrder br : orders) {
            result.add(toOrderView(br));
        }
        logger.info("[MyOrderController] userId={} 返回 {} 条订单", userId, result.size());
        return result;
    }

    /**
     * 查看指定工单的方案详情 HTML。
     * <p>从 MinIO 读取持久化的方案 HTML，直接返回 text/html 给前端渲染。
     * 仅允许工单所属用户访问。
     */
    @GetMapping("/plan-html/{orderId}")
    @SaCheckLogin
    public ResponseEntity<String> getPlanHtml(@PathVariable String orderId) {
        String userId = StpUtil.getLoginIdAsString();
        Optional<AfterSaleTicket> orderOpt = ticketRepository.findByOrderId(orderId);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        AfterSaleTicket order = orderOpt.get();
        if (!userId.equals(order.getUserId())) {
            return ResponseEntity.status(403).body("无权访问该工单");
        }
        String objectKey = order.getPlanHtmlUrl();
        if (objectKey == null || objectKey.isBlank()) {
            return ResponseEntity.notFound().build();
        }
        try (InputStream is = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioBucket)
                        .object(objectKey)
                        .build())) {
            String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(html);
        } catch (Exception e) {
            logger.error("[MyOrderController] 从 MinIO 读取方案 HTML 失败, orderId={}, key={}",
                    orderId, objectKey, e);
            return ResponseEntity.internalServerError().body("读取方案失败");
        }
    }

    /**
     * 删除指定预订记录（逻辑删除）。
     * <p>仅允许记录归属当前登录用户时执行，防止越权操作。</p>
     */
    @DeleteMapping("/orders/{orderNo}")
    @SaCheckLogin
    public ResponseEntity<Map<String, Object>> deleteOrder(@PathVariable String orderNo) {
        String userId = StpUtil.getLoginIdAsString();
        boolean deleted = orderRepository.deleteByOrderNoAndUserId(orderNo, userId);
        if (!deleted) {
            logger.warn("[MyOrderController] 删除订单失败或无权操作, userId={}, orderNo={}", userId, orderNo);
            return ResponseEntity.status(404).body(Map.of("deleted", false, "message", "订单不存在或无权删除"));
        }
        logger.info("[MyOrderController] 删除订单成功, userId={}, orderNo={}", userId, orderNo);
        return ResponseEntity.ok(Map.of("deleted", true));
    }

    /**
     * 取消指定订单（同步调用渠道取消接口 + 更新内部状态为 CANCELLED）。
     * <p>可取消订单会调用渠道取消接口；渠道不支持时仅标记内部状态。</p>
     * <p>取消与删除的区别：取消会在平台侧同步取消订单，删除仅做本地逻辑删除。</p>
     */
    @PostMapping("/orders/{orderNo}/cancel")
    @SaCheckLogin
    public ResponseEntity<Map<String, Object>> cancelOrder(@PathVariable String orderNo,
                                                              @RequestBody(required = false) Map<String, String> body) {
        String userId = StpUtil.getLoginIdAsString();
        String reason = body != null ? body.get("reason") : null;
        logger.info("[MyOrderController] 取消订单, userId={}, orderNo={}, reason={}", userId, orderNo, reason);

        // 构造轻量 AgentSessionContext 供 OrderWriteTools 读取 userId
        AgentSessionContext sessionCtx = new AgentSessionContext(userId, "my-orders-" + UUID.randomUUID());
        String resultJson = orderWriteTools.cancelOrder(sessionCtx, orderNo, reason);

        JSONObject result = JSON.parseObject(resultJson);
        boolean success = Boolean.TRUE.equals(result.getBoolean("success"));
        if (!success) {
            String errorCode = result.getString("errorCode");
            String message = result.getString("message");
            logger.warn("[MyOrderController] 取消订单失败, userId={}, orderNo={}, errorCode={}, message={}",
                    userId, orderNo, errorCode, message);
            return ResponseEntity.badRequest().body(Map.of("cancelled", false, "message", message != null ? message : "取消失败"));
        }
        logger.info("[MyOrderController] 取消订单成功, userId={}, orderNo={}", userId, orderNo);
        return ResponseEntity.ok(Map.of("cancelled", true, "message", result.getString("message")));
    }

    /** 将售后工单 + 关联审批 + 关联预订记录聚合为前端视图 */
    private Map<String, Object> toView(String userId, AfterSaleTicket order) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("orderId", order.getOrderId());
        view.put("destination", order.getDestination());
        view.put("departureCity", order.getDepartureCity());
        view.put("departureDate", order.getDepartureDate());
        view.put("returnDate", order.getReturnDate());
        view.put("purpose", order.getPurpose());
        AfterSaleTicketStatus status = order.getStatus();
        view.put("status", status != null ? status.getCode() : null);
        view.put("statusLabel", status != null ? status.getLabel() : null);
        view.put("createdAt", toMillis(order.getCreatedAt()));
        view.put("updatedAt", toMillis(order.getUpdatedAt()));
        view.put("planHtmlUrl", order.getPlanHtmlUrl());

        // 审批信息（取该工单最新一条审批记录）
        Optional<ApprovalRecord> approvalOpt = approvalService.findLatestByOrderId(order.getOrderId());
        if (approvalOpt.isPresent()) {
            ApprovalRecord ar = approvalOpt.get();
            view.put("approvalId", ar.getProcessInstanceId());
            view.put("approvalStatus", ar.getStatus() != null ? ar.getStatus().getCode() : null);
            view.put("approvalStatusLabel", ar.getStatus() != null ? ar.getStatus().getLabel() : null);
            view.put("approvalRemark", ar.getRemark());
            view.put("approvalSubmitTime", toMillis(ar.getSubmitTime()));
            view.put("approvalUpdateTime", toMillis(ar.getUpdateTime()));
        } else {
            view.put("approvalId", null);
            view.put("approvalStatus", null);
            view.put("approvalStatusLabel", null);
            view.put("approvalRemark", null);
            view.put("approvalSubmitTime", null);
            view.put("approvalUpdateTime", null);
        }

        // 关联订单
        List<EcommerceOrder> relatedOrders = orderRepository.findByUserIdAndTicketId(userId, order.getOrderId());
        List<Map<String, Object>> orderViews = new ArrayList<>(relatedOrders.size());
        for (EcommerceOrder br : relatedOrders) {
            orderViews.add(toOrderView(br));
        }
        view.put("orders", orderViews);
        view.put("orderCount", orderViews.size());
        return view;
    }

    private Map<String, Object> toOrderView(EcommerceOrder br) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("orderNo", br.getOrderNo());
        v.put("bizType", br.getBizType() != null ? br.getBizType().getCode() : null);
        v.put("bizTypeLabel", br.getBizType() != null ? br.getBizType().getLabel() : null);
        v.put("title", br.getTitle());
        v.put("status", br.getStatus() != null ? br.getStatus().getCode() : null);
        v.put("statusLabel", br.getStatus() != null ? br.getStatus().getLabel() : null);
        v.put("externalStatus", br.getExternalStatus());
        BigDecimal amount = br.getTotalAmount();
        v.put("totalAmount", amount != null ? amount.toPlainString() : null);
        v.put("currency", br.getCurrency());
        v.put("platform", br.getPlatform());
        v.put("externalOrderNo", br.getExternalOrderNo());
        v.put("paymentStatus", br.getPaymentStatus());
        v.put("startTime", toMillis(br.getStartTime()));
        v.put("endTime", toMillis(br.getEndTime()));
        v.put("orderedAt", toMillis(br.getOrderedAt()));
        v.put("orderUrl", buildOrderUrl(br));
        return v;
    }

    private Long toMillis(Instant t) {
        return t != null ? t.toEpochMilli() : null;
    }

    /**
     * 根据下单渠道与外部单号构造第三方平台订单详情页 URL。
     * <p>不同平台的 URL 模板不同，若平台或单号缺失则返回 null，前端不展示跳转入口。</p>
     */
    private String buildOrderUrl(EcommerceOrder br) {
        String platform = br.getPlatform();
        String orderNo = br.getExternalOrderNo();
        if (platform == null || platform.isBlank() || orderNo == null || orderNo.isBlank()) {
            return null;
        }
        // 自营商城暂无对外订单详情页；后续接入第三方渠道时在此补充 URL 模板
        return switch (platform.toLowerCase()) {
            default -> null;
        };
    }


    private boolean isCjk(char c) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
                || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION;
    }
}
