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
     * 查询用户指定状态集合下的售后工单，按创建时间倒序。
     * <p>statuses 为空时默认查询生效中的状态（DRAFT / SUBMITTED / APPROVED）。
     */
    List<AfterSaleTicket> findActiveByUserId(String userId, Collection<AfterSaleTicketStatus> statuses);

    Optional<AfterSaleTicket> findByOrderId(String orderId);

    AfterSaleTicket save(AfterSaleTicket order);

    /** 更新售后工单状态 */
    boolean updateStatus(String orderId, AfterSaleTicketStatus status);

    /** 更新售后工单的行程方案 HTML 对象 key */
    boolean updatePlanHtmlUrl(String orderId, String planHtmlUrl);
}
