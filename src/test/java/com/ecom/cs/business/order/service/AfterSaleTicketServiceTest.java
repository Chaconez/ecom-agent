package com.ecom.cs.business.order.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ecom.cs.business.approval.entity.ApprovalRecord;
import com.ecom.cs.business.approval.entity.ApprovalRecordStatus;
import com.ecom.cs.business.approval.service.ApprovalService;
import com.ecom.cs.business.order.entity.AfterSaleTicket;
import com.ecom.cs.business.order.entity.AfterSaleTicketStatus;
import com.ecom.cs.business.order.repo.AfterSaleTicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * AfterSaleTicketService 单元测试：
 * 覆盖 createAndSubmit / cancelWithApproval / modifyAndResubmit / decideAndSyncOrder
 * 四个事务方法的黄金路径与幂等/降级分支，以及审批表单 JSON 的特殊字符转义。
 *
 * @author Hollis
 */
@ExtendWith(MockitoExtension.class)
class AfterSaleTicketServiceTest {

    @Mock
    private AfterSaleTicketRepository ticketRepository;

    @Mock
    private ApprovalService approvalService;

    @InjectMocks
    private AfterSaleTicketService ticketService;

    private static ApprovalRecord record(String processInstanceId, ApprovalRecordStatus status) {
        ApprovalRecord r = new ApprovalRecord(processInstanceId, "u_1", "售后", status,
                "{}", null, Instant.now(), Instant.now());
        return r;
    }

    /** 模拟仓储行为：无 orderId 时分配固定 ID，原样返回 */
    private void stubSaveAssignsId(String orderId) {
        when(ticketRepository.save(any(AfterSaleTicket.class))).thenAnswer(inv -> {
            AfterSaleTicket o = inv.getArgument(0);
            if (o.getOrderId() == null || o.getOrderId().isBlank()) {
                o.setOrderId(orderId);
            }
            return o;
        });
    }

    // ============ createAndSubmit ============

    @Test
    void createAndSubmit_goldenPath() {
        stubSaveAssignsId("to_1");
        when(approvalService.submit(eq("u_1"), anyString(), anyString(), eq("to_1")))
                .thenReturn(record("pi_1", ApprovalRecordStatus.PENDING));

        AfterSaleTicket order = AfterSaleTicket.builder()
                .userId("u_1").destination("北京").departureCity("杭州")
                .departureDate("2026-08-01").returnDate("2026-08-03").purpose("参加会议")
                .build();

        ApprovalRecord result = ticketService.createAndSubmit(order);

        assertEquals("pi_1", result.getProcessInstanceId());
        assertEquals("to_1", order.getOrderId());
        assertEquals("pi_1", order.getApprovalId());
        assertEquals(AfterSaleTicketStatus.SUBMITTED, order.getStatus());
        // ① DRAFT 落库 + ③ SUBMITTED 回写，共两次 save
        verify(ticketRepository, times(2)).save(order);
    }

    @Test
    void createAndSubmit_approvalFormEscapesSpecialChars() {
        // purpose 含引号与反斜杠，验证表单仍是合法 JSON 且内容不失真（手拼字符串时代会翻车）
        String trickyPurpose = "参加\"双十一\"发布会\\预算说明";
        stubSaveAssignsId("to_1");
        when(approvalService.submit(eq("u_1"), anyString(), anyString(), eq("to_1")))
                .thenReturn(record("pi_1", ApprovalRecordStatus.PENDING));

        AfterSaleTicket order = AfterSaleTicket.builder()
                .userId("u_1").destination("北京").departureCity("杭州")
                .departureDate("2026-08-01").returnDate("2026-08-03").purpose(trickyPurpose)
                .build();
        ticketService.createAndSubmit(order);

        ArgumentCaptor<String> formCaptor = ArgumentCaptor.forClass(String.class);
        verify(approvalService).submit(eq("u_1"), anyString(), formCaptor.capture(), eq("to_1"));
        JSONObject form = JSON.parseObject(formCaptor.getValue());
        assertEquals(trickyPurpose, form.getString("purpose"));
        assertEquals("to_1", form.getString("orderId"));
        assertEquals("北京", form.getString("destination"));
    }

    // ============ cancelWithApproval ============

    @Test
    void cancel_orderUpdateFailed_noApprovalTouched() {
        when(ticketRepository.updateStatus("to_1", AfterSaleTicketStatus.CANCELLED)).thenReturn(false);
        AfterSaleTicket order = AfterSaleTicket.builder().orderId("to_1").build();

        AfterSaleTicketService.CancelOutcome outcome = ticketService.cancelWithApproval(order);

        assertFalse(outcome.orderCancelled());
        assertFalse(outcome.approvalCancelled());
        verifyNoInteractions(approvalService);
    }

    @Test
    void cancel_withApprovalId_cancelsLinkedApproval() {
        when(ticketRepository.updateStatus("to_1", AfterSaleTicketStatus.CANCELLED)).thenReturn(true);
        when(approvalService.cancel("pi_old")).thenReturn(true);
        AfterSaleTicket order = AfterSaleTicket.builder().orderId("to_1").approvalId("pi_old").build();

        AfterSaleTicketService.CancelOutcome outcome = ticketService.cancelWithApproval(order);

        assertTrue(outcome.orderCancelled());
        assertTrue(outcome.approvalCancelled());
        assertEquals("pi_old", outcome.cancelledApprovalId());
    }

    @Test
    void cancel_withoutApprovalId_fallsBackToLatestByOrderId() {
        when(ticketRepository.updateStatus("to_1", AfterSaleTicketStatus.CANCELLED)).thenReturn(true);
        when(approvalService.findLatestByOrderId("to_1"))
                .thenReturn(Optional.of(record("pi_x", ApprovalRecordStatus.PENDING)));
        when(approvalService.cancel("pi_x")).thenReturn(true);
        AfterSaleTicket order = AfterSaleTicket.builder().orderId("to_1").build();

        AfterSaleTicketService.CancelOutcome outcome = ticketService.cancelWithApproval(order);

        assertTrue(outcome.orderCancelled());
        assertTrue(outcome.approvalCancelled());
        assertEquals("pi_x", outcome.cancelledApprovalId());
    }

    @Test
    void cancel_latestApprovalAlreadyCancelled_skipsCancel() {
        when(ticketRepository.updateStatus("to_1", AfterSaleTicketStatus.CANCELLED)).thenReturn(true);
        when(approvalService.findLatestByOrderId("to_1"))
                .thenReturn(Optional.of(record("pi_x", ApprovalRecordStatus.CANCELLED)));
        AfterSaleTicket order = AfterSaleTicket.builder().orderId("to_1").build();

        AfterSaleTicketService.CancelOutcome outcome = ticketService.cancelWithApproval(order);

        assertTrue(outcome.orderCancelled());
        assertFalse(outcome.approvalCancelled());
        assertNull(outcome.cancelledApprovalId());
        verify(approvalService, never()).cancel(anyString());
    }

    // ============ modifyAndResubmit ============

    @Test
    void modify_goldenPath_cancelsOldAndLinksNew() {
        when(approvalService.cancel("pi_old")).thenReturn(true);
        when(ticketRepository.save(any(AfterSaleTicket.class))).thenAnswer(inv -> inv.getArgument(0));
        when(approvalService.submit(eq("u_1"), anyString(), anyString(), eq("to_1")))
                .thenReturn(record("pi_new", ApprovalRecordStatus.PENDING));

        AfterSaleTicket order = AfterSaleTicket.builder()
                .orderId("to_1").userId("u_1").approvalId("pi_old")
                .destination("上海").departureCity("杭州")
                .departureDate("2026-08-05").returnDate("2026-08-06").purpose("修改后的行程")
                .build();

        AfterSaleTicketService.ModifyOutcome outcome = ticketService.modifyAndResubmit(order);

        assertEquals("pi_old", outcome.oldApprovalId());
        assertTrue(outcome.oldApprovalCancelled());
        assertEquals("pi_new", outcome.newApproval().getProcessInstanceId());
        assertEquals("pi_new", order.getApprovalId());
        assertEquals(AfterSaleTicketStatus.SUBMITTED, order.getStatus());
        // DRAFT 重置 + SUBMITTED 关联，共两次 save
        verify(ticketRepository, times(2)).save(order);
    }

    // ============ decideAndSyncOrder ============

    @Test
    void decide_agree_syncsOrderToApproved() {
        ApprovalRecord approved = record("pi_1", ApprovalRecordStatus.APPROVED);
        approved.setOrderId("to_1");
        when(approvalService.decide("pi_1", true, "ok")).thenReturn(Optional.of(approved));

        Optional<ApprovalRecord> result = ticketService.decideAndSyncOrder("pi_1", true, "ok");

        assertTrue(result.isPresent());
        verify(ticketRepository).updateStatus("to_1", AfterSaleTicketStatus.APPROVED);
    }

    @Test
    void decide_refuse_syncsOrderToRejected() {
        ApprovalRecord rejected = record("pi_1", ApprovalRecordStatus.REJECTED);
        rejected.setOrderId("to_1");
        when(approvalService.decide("pi_1", false, "预算不足")).thenReturn(Optional.of(rejected));

        ticketService.decideAndSyncOrder("pi_1", false, "预算不足");

        verify(ticketRepository).updateStatus("to_1", AfterSaleTicketStatus.REJECTED);
    }

    @Test
    void decide_idempotentSkip_noOrderUpdate() {
        // 审批单不存在或已被处理：decide 返回 empty，不应触碰售后工单
        when(approvalService.decide("pi_1", true, null)).thenReturn(Optional.empty());

        Optional<ApprovalRecord> result = ticketService.decideAndSyncOrder("pi_1", true, null);

        assertTrue(result.isEmpty());
        verifyNoInteractions(ticketRepository);
    }

    @Test
    void decide_recordWithoutOrderId_noOrderUpdate() {
        when(approvalService.decide("pi_1", true, null))
                .thenReturn(Optional.of(record("pi_1", ApprovalRecordStatus.APPROVED)));

        ticketService.decideAndSyncOrder("pi_1", true, null);

        verifyNoInteractions(ticketRepository);
    }
}
