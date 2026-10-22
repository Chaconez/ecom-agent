package com.ecom.cs.agent.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ecom.cs.agent.context.AgentSessionContext;
import com.ecom.cs.business.approval.entity.ApprovalRecord;
import com.ecom.cs.business.approval.entity.ApprovalRecordStatus;
import com.ecom.cs.business.approval.service.ApprovalService;
import com.ecom.cs.business.order.repo.EcommerceOrderRepository;
import com.ecom.cs.business.order.entity.AfterSaleTicket;
import com.ecom.cs.business.order.entity.AfterSaleTicketStatus;
import com.ecom.cs.business.order.repo.AfterSaleTicketRepository;
import com.ecom.cs.business.order.service.AfterSaleTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AfterSaleTicketWriteTools 单元测试：
 * submit 的入参校验/幂等命中/落库验证，cancel 的状态机与"清空会话关联售后工单"，
 * modify 的空变更短路与 APPROVED 二次确认。
 *
 * @author Hollis
 */
@ExtendWith(MockitoExtension.class)
class AfterSaleTicketWriteToolsTest {

    private static final String USER_ID = "u_test";

    @Mock
    private AfterSaleTicketRepository ticketRepository;
    @Mock
    private AfterSaleTicketService ticketService;
    @Mock
    private ApprovalService approvalService;
    @Mock
    private EcommerceOrderRepository orderRepository;

    @InjectMocks
    private AfterSaleTicketWriteTools tools;

    private AgentSessionContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new AgentSessionContext(USER_ID, "s_test");
    }

    private AfterSaleTicket order(String orderId, AfterSaleTicketStatus status) {
        return AfterSaleTicket.builder()
                .orderId(orderId).userId(USER_ID)
                .departureCity("北京").destination("上海")
                .departureDate("2026-08-01").returnDate("2026-08-03")
                .purpose("参加会议").status(status)
                .build();
    }

    private static ApprovalRecord approval(String processInstanceId) {
        return new ApprovalRecord(processInstanceId, USER_ID, "售后", ApprovalRecordStatus.PENDING,
                "{}", null, Instant.now(), Instant.now());
    }

    // ============ submit_after_sale_ticket ============

    @Test
    void submit_invalidDateFormat_rejected() {
        JSONObject o = JSON.parseObject(tools.submitTravelApproval(ctx, "上海", "北京", "2026/08/01", "2026-08-03", "开会"));
        assertFalse(o.getBooleanValue("success"));
        assertEquals("INVALID_PARAM", o.getString("errorCode"));
        verifyNoInteractions(ticketService);
    }

    @Test
    void submit_departureAfterReturn_rejected() {
        JSONObject o = JSON.parseObject(tools.submitTravelApproval(ctx, "上海", "北京", "2026-08-05", "2026-08-03", "开会"));
        assertFalse(o.getBooleanValue("success"));
        assertEquals("INVALID_DATE_RANGE", o.getString("errorCode"));
    }

    @Test
    void submit_duplicateActiveOrder_idempotentHit() {
        // 已存在出行要素完全一致的生效中售后工单：直接返回已有单，不重复创建
        AfterSaleTicket dup = order("to_dup", AfterSaleTicketStatus.SUBMITTED);
        dup.setApprovalId("pi_dup");
        when(ticketRepository.findActiveByUserIdAndDateRange(eq(USER_ID), any(), eq("2026-08-01"), eq("2026-08-03")))
                .thenReturn(List.of(dup));
        when(approvalService.findByProcessInstanceId("pi_dup")).thenReturn(Optional.of(approval("pi_dup")));

        JSONObject o = JSON.parseObject(tools.submitTravelApproval(ctx, "上海", "北京", "2026-08-01", "2026-08-03", "参加会议"));

        assertTrue(o.getBooleanValue("success"));
        assertEquals("to_dup", o.getString("orderId"));
        assertTrue(o.getString("message").contains("已存在"));
        assertEquals("to_dup", ctx.getTicketId());
        verifyNoInteractions(ticketService);
    }

    @Test
    void submit_goldenPath_setsSessionTicketId() {
        when(ticketRepository.findActiveByUserIdAndDateRange(eq(USER_ID), any(), anyString(), anyString()))
                .thenReturn(List.of());
        when(ticketService.createAndSubmit(any(AfterSaleTicket.class))).thenAnswer(inv -> {
            AfterSaleTicket order = inv.getArgument(0);
            order.setOrderId("to_1");
            order.setStatus(AfterSaleTicketStatus.SUBMITTED);
            order.setApprovalId("pi_1");
            return approval("pi_1");
        });
        when(ticketRepository.findByOrderId("to_1"))
                .thenReturn(Optional.of(order("to_1", AfterSaleTicketStatus.SUBMITTED)));

        JSONObject o = JSON.parseObject(tools.submitTravelApproval(ctx, "上海", "北京", "2026-08-01", "2026-08-03", "参加会议"));

        assertTrue(o.getBooleanValue("success"));
        assertEquals("to_1", o.getString("orderId"));
        assertEquals("pi_1", o.getString("processInstanceId"));
        assertEquals("to_1", ctx.getTicketId());
    }

    @Test
    void submit_verifyStatusMismatch_reportsVerifyFailed() {
        when(ticketRepository.findActiveByUserIdAndDateRange(eq(USER_ID), any(), anyString(), anyString()))
                .thenReturn(List.of());
        when(ticketService.createAndSubmit(any(AfterSaleTicket.class))).thenAnswer(inv -> {
            AfterSaleTicket order = inv.getArgument(0);
            order.setOrderId("to_1");
            return approval("pi_1");
        });
        // 事务后查库状态仍是 DRAFT：验证应拦截
        when(ticketRepository.findByOrderId("to_1"))
                .thenReturn(Optional.of(order("to_1", AfterSaleTicketStatus.DRAFT)));

        JSONObject o = JSON.parseObject(tools.submitTravelApproval(ctx, "上海", "北京", "2026-08-01", "2026-08-03", "参加会议"));

        assertFalse(o.getBooleanValue("success"));
        assertEquals("VERIFY_FAILED", o.getString("errorCode"));
    }

    // ============ cancel_after_sale_ticket ============

    @Test
    void cancel_orderNotFound_rejected() {
        when(ticketRepository.findByOrderId("to_404")).thenReturn(Optional.empty());
        JSONObject o = JSON.parseObject(tools.cancelAfterSaleTicket(ctx, "to_404", null, null));
        assertEquals("ORDER_NOT_FOUND", o.getString("errorCode"));
    }

    @Test
    void cancel_wrongOwner_permissionDenied() {
        AfterSaleTicket other = order("to_1", AfterSaleTicketStatus.SUBMITTED);
        other.setUserId("u_other");
        when(ticketRepository.findByOrderId("to_1")).thenReturn(Optional.of(other));

        JSONObject o = JSON.parseObject(tools.cancelAfterSaleTicket(ctx, "to_1", null, null));

        assertEquals("PERMISSION_DENIED", o.getString("errorCode"));
        verifyNoInteractions(ticketService);
    }

    @Test
    void cancel_alreadyCancelled_invalidState() {
        when(ticketRepository.findByOrderId("to_1"))
                .thenReturn(Optional.of(order("to_1", AfterSaleTicketStatus.CANCELLED)));
        JSONObject o = JSON.parseObject(tools.cancelAfterSaleTicket(ctx, "to_1", null, null));
        assertEquals("INVALID_STATE", o.getString("errorCode"));
    }

    @Test
    void cancel_approvedWithoutForce_needConfirm() {
        when(ticketRepository.findByOrderId("to_1"))
                .thenReturn(Optional.of(order("to_1", AfterSaleTicketStatus.APPROVED)));

        JSONObject o = JSON.parseObject(tools.cancelAfterSaleTicket(ctx, "to_1", null, null));

        assertFalse(o.getBooleanValue("success"));
        assertTrue(o.getBooleanValue("needConfirm"));
        verifyNoInteractions(ticketService);
    }

    @Test
    void cancel_goldenPath_clearsSessionTicketId() {
        AfterSaleTicket order = order("to_1", AfterSaleTicketStatus.SUBMITTED);
        when(ticketRepository.findByOrderId("to_1")).thenReturn(Optional.of(order));
        when(ticketService.cancelWithApproval(order)).thenAnswer(inv -> {
            order.setStatus(AfterSaleTicketStatus.CANCELLED);
            return new AfterSaleTicketService.CancelOutcome(true, true, "pi_old");
        });
        when(orderRepository.findByUserIdAndTicketId(USER_ID, "to_1")).thenReturn(List.of());
        ctx.setTicketId("to_1");

        JSONObject o = JSON.parseObject(tools.cancelAfterSaleTicket(ctx, "to_1", "行程有变", null));

        assertTrue(o.getBooleanValue("success"));
        assertTrue(o.getBooleanValue("approvalCancelled"));
        // 被取消的正是会话当前关联的售后工单：必须清空，防止后续预订挂到已取消的单上
        assertNull(ctx.getTicketId());
    }

    @Test
    void cancel_unrelatedSessionOrder_notCleared() {
        AfterSaleTicket order = order("to_1", AfterSaleTicketStatus.SUBMITTED);
        when(ticketRepository.findByOrderId("to_1")).thenReturn(Optional.of(order));
        when(ticketService.cancelWithApproval(order)).thenAnswer(inv -> {
            order.setStatus(AfterSaleTicketStatus.CANCELLED);
            return new AfterSaleTicketService.CancelOutcome(true, false, null);
        });
        when(orderRepository.findByUserIdAndTicketId(USER_ID, "to_1")).thenReturn(List.of());
        ctx.setTicketId("to_other");

        tools.cancelAfterSaleTicket(ctx, "to_1", null, null);

        // 会话关联的是别的单，不应被误清
        assertEquals("to_other", ctx.getTicketId());
    }

    // ============ modify_after_sale_ticket ============

    @Test
    void modify_noFieldChanged_shortCircuits() {
        when(ticketRepository.findByOrderId("to_1"))
                .thenReturn(Optional.of(order("to_1", AfterSaleTicketStatus.SUBMITTED)));

        // 提交的字段与当前值完全一致：应幂等短路，不撤审重提
        JSONObject o = JSON.parseObject(tools.modifyAfterSaleTicket(ctx, "to_1", "上海", null, "2026-08-01", null, null, null));

        assertTrue(o.getBooleanValue("success"));
        assertTrue(o.getString("message").contains("未做任何修改"));
        verify(ticketService, never()).modifyAndResubmit(any());
    }

    @Test
    void modify_approvedWithoutForce_needConfirm() {
        when(ticketRepository.findByOrderId("to_1"))
                .thenReturn(Optional.of(order("to_1", AfterSaleTicketStatus.APPROVED)));

        JSONObject o = JSON.parseObject(tools.modifyAfterSaleTicket(ctx, "to_1", "深圳", null, null, null, null, null));

        assertFalse(o.getBooleanValue("success"));
        assertTrue(o.getBooleanValue("needConfirm"));
        verify(ticketService, never()).modifyAndResubmit(any());
    }

    @Test
    void modify_effectiveDateRangeInvalid_rejected() {
        when(ticketRepository.findByOrderId("to_1"))
                .thenReturn(Optional.of(order("to_1", AfterSaleTicketStatus.SUBMITTED)));

        // 只改出发日期，晚于原返回日期：新旧值综合校验应拦截
        JSONObject o = JSON.parseObject(tools.modifyAfterSaleTicket(ctx, "to_1", null, null, "2026-08-10", null, null, null));

        assertFalse(o.getBooleanValue("success"));
        assertEquals("INVALID_DATE_RANGE", o.getString("errorCode"));
    }
}
