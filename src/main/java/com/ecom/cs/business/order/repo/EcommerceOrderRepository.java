package com.ecom.cs.business.order.repo;

import com.ecom.cs.business.order.entity.EcommerceOrder;
import com.ecom.cs.business.order.entity.OrderStatus;
import com.ecom.cs.business.order.entity.OrderType;
import java.util.List;
import java.util.Optional;

/**
 * 预订记录仓储接口
 *
 * @author Hollis
 */
public interface EcommerceOrderRepository {

    /** 查询用户的所有预订记录（按下单时间倒序） */
    List<EcommerceOrder> findByUserId(String userId);

    /** 查询全部订单（管理端视图，按下单时间倒序；逻辑删除的记录自动排除） */
    List<EcommerceOrder> findAll();

    /** 按用户 + 业务类型查询 */
    List<EcommerceOrder> findByUserIdAndBizType(String userId, OrderType bizType);

    /** 按用户 + 售后工单ID查询（按下单时间倒序） */
    List<EcommerceOrder> findByUserIdAndTicketId(String userId, String ticketId, OrderType bizType);

    List<EcommerceOrder> findByUserIdAndTicketId(String userId, String ticketId);

    /** 按内部预订单号查询 */
    Optional<EcommerceOrder> findByOrderNo(String orderNo);

    /** 按平台 + 业务类型 + 外部主订单号查询（用于去重/回查） */
    Optional<EcommerceOrder> findByExternalOrder(String platform, OrderType bizType, String externalOrderNo);

    /**
     * 保存预订记录：存在则更新，不存在则插入。
     * <p>{@code orderNo} 为空时自动生成。
     */
    EcommerceOrder save(EcommerceOrder record);

    /** 更新预订状态（可同步写入外部平台原始状态，传 null 则不更新该字段） */
    boolean updateStatus(String orderNo, OrderStatus status, String externalStatus);

    /** 按内部预订单号逻辑删除（@TableLogic 软删除），仅当记录归属指定用户时才执行 */
    boolean deleteByOrderNoAndUserId(String orderNo, String userId);
}
