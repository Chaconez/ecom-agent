package com.ecom.cs.business.order.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ecom.cs.business.order.entity.AfterSaleTicket;
import com.ecom.cs.business.order.entity.AfterSaleTicketStatus;
import com.ecom.cs.business.order.mapper.AfterSaleTicketMapper;
import com.ecom.cs.utils.IdGenerator;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * 售后工单数据库实现（基于 MyBatis-Plus）
 *
 * @author Hollis
 */
@Repository
public class AfterSaleTicketRepositoryImpl implements AfterSaleTicketRepository {

    @Autowired
    private AfterSaleTicketMapper ticketMapper;

    @Override
    public List<AfterSaleTicket> findByUserId(String userId) {
        return ticketMapper.selectList(
                new LambdaQueryWrapper<AfterSaleTicket>().eq(AfterSaleTicket::getUserId, userId));
    }

    @Override
    public List<AfterSaleTicket> findByUserIdAndStatus(String userId, AfterSaleTicketStatus status) {
        return ticketMapper.selectList(
                new LambdaQueryWrapper<AfterSaleTicket>()
                        .eq(AfterSaleTicket::getUserId, userId)
                        .eq(AfterSaleTicket::getStatus, status));
    }

    @Override
    public List<AfterSaleTicket> findActiveByUserId(String userId,
                                                    java.util.Collection<AfterSaleTicketStatus> statuses) {
        LambdaQueryWrapper<AfterSaleTicket> wrapper = new LambdaQueryWrapper<AfterSaleTicket>()
                .eq(AfterSaleTicket::getUserId, userId);
        if (statuses != null && !statuses.isEmpty()) {
            wrapper.in(AfterSaleTicket::getStatus, statuses);
        } else {
            // 默认查询生效中状态
            wrapper.in(AfterSaleTicket::getStatus,
                    AfterSaleTicketStatus.DRAFT, AfterSaleTicketStatus.SUBMITTED, AfterSaleTicketStatus.APPROVED);
        }
        wrapper.orderByDesc(AfterSaleTicket::getCreatedAt);
        return ticketMapper.selectList(wrapper);
    }

    @Override
    public Optional<AfterSaleTicket> findByOrderId(String orderId) {
        return Optional.ofNullable(ticketMapper.selectById(orderId));
    }

    @Override
    public AfterSaleTicket save(AfterSaleTicket order) {
        if (order.getOrderId() == null || order.getOrderId().isBlank()) {
            order.setOrderId(IdGenerator.next("to_"));
        }
        // 存在则更新，不存在则插入
        if (ticketMapper.selectById(order.getOrderId()) == null) {
            ticketMapper.insert(order);
        } else {
            ticketMapper.updateById(order);
        }
        return order;
    }

    @Override
    public boolean updateStatus(String orderId, AfterSaleTicketStatus status) {
        int rows = ticketMapper.update(
                new LambdaUpdateWrapper<AfterSaleTicket>()
                        .eq(AfterSaleTicket::getOrderId, orderId)
                        .set(AfterSaleTicket::getStatus, status));
        return rows > 0;
    }

    @Override
    public boolean updatePlanHtmlUrl(String orderId, String planHtmlUrl) {
        int rows = ticketMapper.update(
                new LambdaUpdateWrapper<AfterSaleTicket>()
                        .eq(AfterSaleTicket::getOrderId, orderId)
                        .set(AfterSaleTicket::getPlanHtmlUrl, planHtmlUrl));
        return rows > 0;
    }
}
