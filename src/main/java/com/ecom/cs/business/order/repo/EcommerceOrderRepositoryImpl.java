package com.ecom.cs.business.order.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ecom.cs.business.order.entity.EcommerceOrder;
import com.ecom.cs.business.order.entity.OrderStatus;
import com.ecom.cs.business.order.entity.OrderType;
import com.ecom.cs.business.order.mapper.EcommerceOrderMapper;
import com.ecom.cs.utils.IdGenerator;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * 预订记录数据库实现（基于 MyBatis-Plus）
 *
 * @author Hollis
 */
@Repository
public class EcommerceOrderRepositoryImpl implements EcommerceOrderRepository {

    @Autowired
    private EcommerceOrderMapper bookingRecordMapper;

    @Override
    public List<EcommerceOrder> findByUserId(String userId) {
        return bookingRecordMapper.selectList(
                new LambdaQueryWrapper<EcommerceOrder>()
                        .eq(EcommerceOrder::getUserId, userId)
                        .orderByDesc(EcommerceOrder::getOrderedAt));
    }

    @Override
    public List<EcommerceOrder> findByUserIdAndBizType(String userId, OrderType bizType) {
        return bookingRecordMapper.selectList(
                new LambdaQueryWrapper<EcommerceOrder>()
                        .eq(EcommerceOrder::getUserId, userId)
                        .eq(EcommerceOrder::getBizType, bizType)
                        .orderByDesc(EcommerceOrder::getOrderedAt));
    }

    @Override
    public List<EcommerceOrder> findByUserIdAndTicketId(String userId, String ticketId,OrderType bizType) {
        return bookingRecordMapper.selectList(
                new LambdaQueryWrapper<EcommerceOrder>()
                        .eq(EcommerceOrder::getUserId, userId)
                        .eq(EcommerceOrder::getTicketId, ticketId)
                        .eq(EcommerceOrder::getBizType, bizType)
                        .orderByDesc(EcommerceOrder::getOrderedAt));
    }

    @Override
    public List<EcommerceOrder> findByUserIdAndTicketId(String userId, String ticketId) {
        return bookingRecordMapper.selectList(
                new LambdaQueryWrapper<EcommerceOrder>()
                        .eq(EcommerceOrder::getUserId, userId)
                        .eq(EcommerceOrder::getTicketId, ticketId)
                        .orderByDesc(EcommerceOrder::getOrderedAt));
    }

    @Override
    public Optional<EcommerceOrder> findByOrderNo(String orderNo) {
        return Optional.ofNullable(bookingRecordMapper.selectOne(
                new LambdaQueryWrapper<EcommerceOrder>().eq(EcommerceOrder::getOrderNo, orderNo)));
    }

    @Override
    public Optional<EcommerceOrder> findByExternalOrder(String platform, OrderType bizType, String externalOrderNo) {
        return Optional.ofNullable(bookingRecordMapper.selectOne(
                new LambdaQueryWrapper<EcommerceOrder>()
                        .eq(EcommerceOrder::getPlatform, platform)
                        .eq(EcommerceOrder::getBizType, bizType)
                        .eq(EcommerceOrder::getExternalOrderNo, externalOrderNo)));
    }

    @Override
    public EcommerceOrder save(EcommerceOrder record) {
        if (record.getOrderNo() == null || record.getOrderNo().isBlank()) {
            record.setOrderNo(IdGenerator.next("ec_"));
        }
        EcommerceOrder existing = bookingRecordMapper.selectOne(
                new LambdaQueryWrapper<EcommerceOrder>().eq(EcommerceOrder::getOrderNo, record.getOrderNo()));
        if (existing == null) {
            bookingRecordMapper.insert(record);
        } else {
            record.setId(existing.getId());
            bookingRecordMapper.updateById(record);
        }
        return record;
    }

    @Override
    public boolean updateStatus(String orderNo, OrderStatus status, String externalStatus) {
        LambdaUpdateWrapper<EcommerceOrder> wrapper = new LambdaUpdateWrapper<EcommerceOrder>()
                .eq(EcommerceOrder::getOrderNo, orderNo)
                .set(EcommerceOrder::getStatus, status);
        if (externalStatus != null) {
            wrapper.set(EcommerceOrder::getExternalStatus, externalStatus);
        }
        return bookingRecordMapper.update(wrapper) > 0;
    }

    @Override
    public boolean deleteByOrderNoAndUserId(String orderNo, String userId) {
        // @TableLogic 会将 DELETE 转换为 UPDATE deleted=1 软删除
        return bookingRecordMapper.delete(
                new LambdaQueryWrapper<EcommerceOrder>()
                        .eq(EcommerceOrder::getOrderNo, orderNo)
                        .eq(EcommerceOrder::getUserId, userId)) > 0;
    }
}
