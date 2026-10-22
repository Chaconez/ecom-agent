package com.ecom.cs.agent.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ecom.cs.agent.context.AgentSessionContext;
import com.ecom.cs.business.approval.entity.ApprovalRecord;
import com.ecom.cs.business.approval.entity.ApprovalRecordStatus;
import com.ecom.cs.business.approval.service.ApprovalService;
import com.ecom.cs.business.order.entity.AfterSaleTicket;
import com.ecom.cs.business.order.entity.AfterSaleTicketStatus;
import com.ecom.cs.business.order.repo.AfterSaleTicketRepository;
import com.ecom.cs.business.order.repo.EcommerceOrderRepository;
import com.ecom.cs.business.order.service.AfterSaleTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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
    private static final String PRODUCT = "主动降噪无线蓝牙耳机 Pro 黑色 1件";
    private static final String ISSUE = "右耳偶发断连，申请换货";

    /** 购买日期固定用过去日期，避免"不能晚于今天"的校验随运行时间失效 */
    private static final String PURCHASE_DATE = "2026-08-01";

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

    private AfterSaleTicket ticket(String orderId, AfterSaleTicketStatus status) {
        return AfterSaleTicket.builder()
                .orderId(orderId).userId(USER_ID)
                .productName(PRODUCT).issueDesc(ISSUE).purchaseDate(PURCHASE_DATE)
                .status(status)
                .build();
    }

    private static ApprovalRecord approval(String processInstanceId) {
        return new ApprovalRecord(processInstanceId, USER_ID, "售后申请", ApprovalRecordStatus.PENDING,
                "{}", null, Instant.now(), Instant.now());
    }

    // ============ submit_after_sale_ticket ============

    @Test
    void submit_invalidDateFormat_rejected() {
        JSONObject o = JSON.parseObject(tools.submitAfterSaleTicket(ctx, PRODUCT, ISSUE, "2026/08/01"));
        assertFalse(o.getBooleanValue("success"));
        assertEquals("INVALID_PARAM", o.getString("errorCode"));
        verifyNoInteractions(ticketService);
    }

    @Test
    void submit_blankProduct_rejected() {
        JSONObject o = JSON.parseObject(tools.submitAfterSaleTicket(ctx, "  ", ISSUE, PURCHASE_DATE));
        assertFalse(o.getBooleanValue("success"));
        assertEquals("INVALID_PARAM", o.getString("errorCode"));
    }

    @Test
    void submit_futurePurchaseDate_rejected() {
        String tomorrow = LocalDate.now().plusDays(1).toString();
        JSONObject o = JSON.parseObject(tools.submitAfterSaleTicket(ctx, PRODUCT, ISSUE, tomorrow));
        assertFalse(o.getBooleanValue("success"));
        assertEquals("INVALID_DATE", o.getString("errorCode"));
        verifyNoInteractions(ticketService);
    }

    @Test
    void submit_duplicateActiveTicket_idempotentHit() {
        // 已存在商品/问题/购买日期完全一致的生效中工单：直接返回已有单，不重复创建
        AfterSaleTicket dup = ticket("to_dup", AfterSaleTicketStatus.SUBMITTED);
        dup.setApprovalId("pi_dup");
        when(ticketRepository.findActiveByUserId(eq(USER_ID), any())).thenReturn(List.of(dup));
        when(approvalService.findByProcessInstanceId("pi_dup")).thenReturn(Optional.of(approval("pi_dup")));

        JSONObject o = JSON.parseObject(tools.submitAfterSaleTicket(ctx, PRODUCT, ISSUE, PURCHASE_DATE));

        assertTrue(o.getBooleanValue("success"));
        assertEquals("to_dup", o.getString("orderId"));
        assertTrue(o.getString("message").contains("已存在"));
        assertEquals("to_dup", ctx.getTicketId());
        verifyNoInteractions(ticketService);
    }

    @Test
    void submit_goldenPath_setsSessionTicketId() {
        when(ticketRepository.findActiveByUserId(eq(USER_ID), any())).thenReturn(List.of());
        when(ticketService.createAndSubmit(any(AfterSaleTicket.class))).thenAnswer(inv -> {
            AfterSaleTicket t = inv.getArgument(0);
            t.setOrderId("to_1");
            t.setStatus(AfterSaleTicketStatus.SUBMITTED);
            t.setApprovalId("pi_1");
            return approval("pi_1");
        });
        when(ticketRepository.findByOrderId("to_1"))
                .thenReturn(Optional.of(ticket("to_1", AfterSaleTicketStatus.SUBMITTED)));

        JSONObject o = JSON.parseObject(tools.submitAfterSaleTicket(ctx, PRODUCT, ISSUE, PURCHASE_DATE));

        assertTrue(o.getBooleanValue("success"));
        assertEquals("to_1", o.getString("orderId"));
        assertEquals("pi_1", o.getString("processInstanceId"));
        assertEquals(PRODUCT, o.getString("productName"));
        assertEquals("to_1", ctx.getTicketId());
    }

    @Test
    void submit_verifyStatusMismatch_reportsVerifyFailed() {
        when(ticketRepository.findActiveByUserId(eq(USER_ID), any())).thenReturn(List.of());
        when(ticketService.createAndSubmit(any(AfterSaleTicket.class))).thenAnswer(inv -> {
            AfterSaleTicket t = inv.getArgument(0);
            t.setOrderId("to_1");
            return approval("pi_1");
        });
        // 事务后查库状态仍是 DRAFT：验证应拦截
        when(ticketRepository.findByOrderId("to_1"))
                .thenReturn(Optional.of(ticket("to_1", AfterSaleTicketStatus.DRAFT)));

        JSONObject o = JSON.parseObject(tools.submitAfterSaleTicket(ctx, PRODUCT, ISSUE, PURCHASE_DATE));

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
        AfterSaleTicket other = ticket("to_1", AfterSaleTicketStatus.SUBMITTED);
        other.setUserId("u_other");
        when(ticketRepository.findByOrderId("to_1")).thenReturn(Optional.of(other));

        JSONObject o = JSON.parseObject(tools.cancelAfterSaleTicket(ctx, "to_1", null, null));

        assertEquals("PERMISSION_DENIED", o.getString("errorCode"));
        verifyNoInteractions(ticketService);
    }

    @Test
    void cancel_alreadyCancelled_invalidState() {
        when(ticketRepository.findByOrderId("to_1"))
                .thenReturn(Optional.of(ticket("to_1", AfterSaleTicketStatus.CANCELLED)));
        JSONObject o = JSON.parseObject(tools.cancelAfterSaleTicket(ctx, "to_1", null, null));
        assertEquals("INVALID_STATE", o.getString("errorCode"));
    }

    @Test
    void cancel_approvedWithoutForce_needConfirm() {
        when(ticketRepository.findByOrderId("to_1"))
                .thenReturn(Optional.of(ticket("to_1", AfterSaleTicketStatus.APPROVED)));

        JSONObject o = JSON.parseObject(tools.cancelAfterSaleTicket(ctx, "to_1", null, null));

        assertFalse(o.getBooleanValue("success"));
        assertTrue(o.getBooleanValue("needConfirm"));
        verifyNoInteractions(ticketService);
    }

    @Test
    void cancel_goldenPath_clearsSessionTicketId() {
        AfterSaleTicket t = ticket("to_1", AfterSaleTicketStatus.SUBMITTED);
        when(ticketRepository.findByOrderId("to_1")).thenReturn(Optional.of(t));
        when(ticketService.cancelWithApproval(t)).thenAnswer(inv -> {
            t.setStatus(AfterSaleTicketStatus.CANCELLED);
            return new AfterSaleTicketService.CancelOutcome(true, true, "pi_old");
        });
        when(orderRepository.findByUserIdAndTicketId(USER_ID, "to_1")).thenReturn(List.of());
        ctx.setTicketId("to_1");

        JSONObject o = JSON.parseObject(tools.cancelAfterSaleTicket(ctx, "to_1", "不退了", null));

        assertTrue(o.getBooleanValue("success"));
        assertTrue(o.getBooleanValue("approvalCancelled"));
        // 被撤回的正是会话当前关联的售后工单：必须清空，防止后续订单挂到已撤回的单上
        assertNull(ctx.getTicketId());
    }

    @Test
    void cancel_unrelatedSessionTicket_notCleared() {
        AfterSaleTicket t = ticket("to_1", AfterSaleTicketStatus.SUBMITTED);
        when(ticketRepository.findByOrderId("to_1")).thenReturn(Optional.of(t));
        when(ticketService.cancelWithApproval(t)).thenAnswer(inv -> {
            t.setStatus(AfterSaleTicketStatus.CANCELLED);
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
                .thenReturn(Optional.of(ticket("to_1", AfterSaleTicketStatus.SUBMITTED)));

        // 提交的字段与当前值完全一致：应幂等短路，不撤审重提
        JSONObject o = JSON.parseObject(tools.modifyAfterSaleTicket(ctx, "to_1", PRODUCT, null, PURCHASE_DATE, null));

        assertTrue(o.getBooleanValue("success"));
        assertTrue(o.getString("message").contains("未做任何修改"));
        verify(ticketService, never()).modifyAndResubmit(any());
    }

    @Test
    void modify_approvedWithoutForce_needConfirm() {
        when(ticketRepository.findByOrderId("to_1"))
                .thenReturn(Optional.of(ticket("to_1", AfterSaleTicketStatus.APPROVED)));

        JSONObject o = JSON.parseObject(tools.modifyAfterSaleTicket(ctx, "to_1", "智能手表 1件", null, null, null));

        assertFalse(o.getBooleanValue("success"));
        assertTrue(o.getBooleanValue("needConfirm"));
        verify(ticketService, never()).modifyAndResubmit(any());
    }

    @Test
    void modify_futurePurchaseDate_rejected() {
        String tomorrow = LocalDate.now().plusDays(1).toString();
        JSONObject o = JSON.parseObject(tools.modifyAfterSaleTicket(ctx, "to_1", null, null, tomorrow, null));

        assertFalse(o.getBooleanValue("success"));
        assertEquals("INVALID_DATE", o.getString("errorCode"));
        verifyNoInteractions(ticketService);
    }

    @Test
    void modify_issueDescChanged_resubmits() {
        AfterSaleTicket existing = ticket("to_1", AfterSaleTicketStatus.SUBMITTED);
        when(ticketRepository.findByOrderId("to_1")).thenReturn(Optional.of(existing));
        when(ticketService.modifyAndResubmit(any(AfterSaleTicket.class))).thenAnswer(inv -> {
            AfterSaleTicket t = inv.getArgument(0);
            t.setStatus(AfterSaleTicketStatus.SUBMITTED);
            t.setApprovalId("pi_2");
            return new AfterSaleTicketService.ModifyOutcome("pi_1", true, approval("pi_2"));
        });

        JSONObject o = JSON.parseObject(tools.modifyAfterSaleTicket(ctx, "to_1", null, "屏幕有划痕，申请退货退款", null, null));

        assertTrue(o.getBooleanValue("success"));
        assertTrue(o.getString("updatedFields").contains("问题描述"));
    }
}
