package com.ecom.cs.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import com.ecom.cs.business.auth.service.AuthService;
import com.ecom.cs.business.order.entity.EcommerceOrder;
import com.ecom.cs.business.order.repo.EcommerceOrderRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理端订单查询接口（客服工作台 · 所有订单）。
 *
 * <p>与 C 端 {@code /api/my-orders/orders} 的区别：不按 loginId 过滤，返回全部用户的订单，
 * 并附带每条订单的归属用户（{@code userId} + 展示名 {@code userName}）。
 * 仅 {@code role = ADMIN} 可访问，普通用户返回 403。</p>
 *
 * @author Hollis
 */
@RestController
@RequestMapping("/api/admin")
@CrossOrigin(origins = "*")
public class AdminOrderController {

    private static final Logger logger = LoggerFactory.getLogger(AdminOrderController.class);

    @Autowired
    private EcommerceOrderRepository orderRepository;

    @Autowired
    private AuthService authService;

    /**
     * 查询全部订单（按下单时间倒序），供客服工作台展示订单归属。
     */
    @GetMapping("/orders")
    @SaCheckRole("ADMIN")
    public List<Map<String, Object>> listAllOrders() {
        List<EcommerceOrder> orders = orderRepository.findAll();
        List<Map<String, Object>> result = new ArrayList<>(orders.size());
        for (EcommerceOrder order : orders) {
            result.add(toView(order));
        }
        logger.info("[ADMIN_ORDER] 返回全部订单 {} 条", result.size());
        return result;
    }

    /**
     * 订单视图：字段与 C 端保持一致，额外补充归属用户信息。
     */
    private Map<String, Object> toView(EcommerceOrder br) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("orderNo", br.getOrderNo());
        // 归属用户：userId 用于定位，userName 用于展示（真实姓名 → 登录账号 → userId）
        v.put("userId", br.getUserId());
        v.put("userName", authService.getDisplayName(br.getUserId()));
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
        // 自营商城暂无对外订单详情页，与 C 端保持一致返回 null
        v.put("orderUrl", null);
        return v;
    }

    private Long toMillis(Instant instant) {
        return instant == null ? null : instant.toEpochMilli();
    }
}
