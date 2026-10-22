package com.ecom.cs.agent.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ecom.cs.agent.context.AgentSessionContext;
import com.ecom.cs.business.order.entity.AfterSaleTicket;
import com.ecom.cs.business.order.entity.AfterSaleTicketStatus;
import com.ecom.cs.business.order.repo.AfterSaleTicketRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * AfterSaleTicketDuplicateTools 单元测试：
 * 入参校验、完全重复（HIGH）、同商品不同问题（MEDIUM）、不同商品无冲突、
 * 商品名归一化匹配、排除自身、按严重度排序。
 *
 * @author Hollis
 */
@ExtendWith(MockitoExtension.class)
class AfterSaleTicketDuplicateToolsTest {

    private static final String USER_ID = "u_test";
    private static final String PRODUCT = "主动降噪无线蓝牙耳机 Pro 黑色 1件";
    private static final String ISSUE = "右耳偶发断连，申请换货";
    private static final String PURCHASE_DATE = "2026-08-01";

    @Mock
    private AfterSaleTicketRepository ticketRepository;

    @InjectMocks
    private AfterSaleTicketDuplicateTools tools;

    private AgentSessionContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new AgentSessionContext(USER_ID, "s_test");
    }

    private AfterSaleTicket ticket(String orderId, String productName, String issueDesc, String purchaseDate) {
        return AfterSaleTicket.builder()
                .orderId(orderId).userId(USER_ID)
                .productName(productName).issueDesc(issueDesc).purchaseDate(purchaseDate)
                .status(AfterSaleTicketStatus.SUBMITTED)
                .build();
    }

    private void stubCandidates(AfterSaleTicket... tickets) {
        when(ticketRepository.findActiveByUserId(eq(USER_ID), any())).thenReturn(new ArrayList<>(List.of(tickets)));
    }

    private JSONObject check(String productName, String issueDesc, String purchaseDate, String excludeId) {
        return JSON.parseObject(tools.checkDuplicateAfterSaleTicket(ctx, productName, issueDesc, purchaseDate, excludeId));
    }

    private JSONObject firstConflict(JSONObject report) {
        JSONArray conflicts = report.getJSONArray("conflicts");
        assertFalse(conflicts.isEmpty());
        return conflicts.getJSONObject(0);
    }

    // ============ 入参校验 ============

    @Test
    void blankProductName_reportedWithoutConflict() {
        JSONObject o = check("  ", ISSUE, PURCHASE_DATE, null);
        assertFalse(o.getBooleanValue("has_conflict"));
        assertTrue(o.getString("summary").contains("入参校验失败"));
    }

    // ============ ① 完全重复 ============

    @Test
    void sameProductSameIssue_duplicateHigh() {
        stubCandidates(ticket("to_1", PRODUCT, ISSUE, PURCHASE_DATE));

        JSONObject c = firstConflict(check(PRODUCT, ISSUE, PURCHASE_DATE, null));

        assertEquals("DUPLICATE_TICKET", c.getString("type"));
        assertEquals("HIGH", c.getString("severity"));
        assertEquals("to_1", c.getString("order_id"));
    }

    @Test
    void sameProductSameIssue_normalizedMatch() {
        // 商品名带全角括号/多余空格，问题描述带标点差异：归一化后仍应判定为重复
        stubCandidates(ticket("to_1", "主动降噪无线蓝牙耳机Pro（黑色）1件", "右耳偶发断连 申请换货！", PURCHASE_DATE));

        JSONObject c = firstConflict(check(PRODUCT, ISSUE, PURCHASE_DATE, null));

        assertEquals("DUPLICATE_TICKET", c.getString("type"));
    }

    // ============ ② 同商品不同问题 ============

    @Test
    void sameProductDifferentIssue_sameProductActiveMedium() {
        stubCandidates(ticket("to_1", PRODUCT, "续航明显低于标称值，申请检测", PURCHASE_DATE));

        JSONObject c = firstConflict(check(PRODUCT, "充电盒无法充电，申请换货", PURCHASE_DATE, null));

        assertEquals("SAME_PRODUCT_ACTIVE", c.getString("type"));
        assertEquals("MEDIUM", c.getString("severity"));
    }

    @Test
    void sameProductDifferentPurchaseDate_sameProductActiveMedium() {
        stubCandidates(ticket("to_1", PRODUCT, ISSUE, "2026-07-01"));

        JSONObject c = firstConflict(check(PRODUCT, ISSUE, PURCHASE_DATE, null));

        assertEquals("SAME_PRODUCT_ACTIVE", c.getString("type"));
        assertEquals("MEDIUM", c.getString("severity"));
    }

    // ============ 无关商品 / 排除自身 ============

    @Test
    void differentProduct_noConflict() {
        stubCandidates(ticket("to_1", "智能手表 46mm 1件", ISSUE, PURCHASE_DATE));

        JSONObject o = check(PRODUCT, ISSUE, PURCHASE_DATE, null);

        assertFalse(o.getBooleanValue("has_conflict"));
        assertEquals(0, o.getIntValue("total_conflicts"));
    }

    @Test
    void excludeOrderId_removesSelf() {
        stubCandidates(ticket("to_self", PRODUCT, ISSUE, PURCHASE_DATE));

        JSONObject o = check(PRODUCT, ISSUE, PURCHASE_DATE, "to_self");

        assertFalse(o.getBooleanValue("has_conflict"));
    }

    // ============ 排序 ============

    @Test
    void conflicts_sortedBySeverityDesc() {
        // MEDIUM（同商品不同问题）先插入、HIGH（完全重复）后插入，输出应 HIGH 在首位
        stubCandidates(
                ticket("to_1", PRODUCT, "续航明显低于标称值，申请检测", PURCHASE_DATE),
                ticket("to_2", PRODUCT, ISSUE, PURCHASE_DATE));

        JSONObject o = check(PRODUCT, ISSUE, PURCHASE_DATE, null);

        assertEquals(2, o.getIntValue("total_conflicts"));
        assertEquals("HIGH", o.getJSONArray("conflicts").getJSONObject(0).getString("severity"));
        assertEquals("MEDIUM", o.getJSONArray("conflicts").getJSONObject(1).getString("severity"));
    }
}
