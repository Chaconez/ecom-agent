package com.ecom.cs.business.order.repo;

import com.ecom.cs.business.order.entity.AfterSaleTicket;
import com.ecom.cs.business.order.entity.AfterSaleTicketStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * @author Hollis
 */
public interface AfterSaleTicketRepository {

    List<AfterSaleTicket> findByUserId(String userId);

    List<AfterSaleTicket> findByUserIdAndStatus(String userId, AfterSaleTicketStatus status);

    /**
     * 查询用户所有生效中的售后工单（DRAFT / SUBMITTED / APPROVED），
     * 可选地与给定日期范围 [fromDate, toDate] 有重叠。
     * <p>重叠判定：existing.departureDate <= toDate AND existing.returnDate >= fromDate。
     * <p>任何一端传 null 则不参与该端的比较。
     */
    List<AfterSaleTicket> findActiveByUserIdAndDateRange(String userId, Collection<AfterSaleTicketStatus> statuses,
                                                    String fromDate, String toDate);

    Optional<AfterSaleTicket> findByOrderId(String orderId);

    AfterSaleTicket save(AfterSaleTicket order);

    /** 更新售后工单状态 */
    boolean updateStatus(String orderId, AfterSaleTicketStatus status);

    /** 更新售后工单的行程方案 HTML 对象 key */
    boolean updatePlanHtmlUrl(String orderId, String planHtmlUrl);
}
