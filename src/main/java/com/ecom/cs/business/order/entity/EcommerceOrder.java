package com.ecom.cs.business.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ecom.cs.config.InstantTypeHandler;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 电商订单，对应 {@code ecommerce_order} 表。
 * <p>统一存储用户通过 Agent 下单的商品 / 服务类订单，兼容不同渠道：
 * <ul>
 *   <li>{@link #bizType} 区分业务类型，{@link #platform} 区分下单渠道；</li>
 *   <li>{@link #externalOrderNo} 记录外部平台单号；</li>
 *   <li>{@link #status} 为统一状态，{@link #externalStatus} 保留平台原始状态码；</li>
 *   <li>{@link #detail} 存放平台差异化明细的 JSON 字符串。</li>
 * </ul>
 *
 * @author Hollis
 */
@TableName(value = "ecommerce_order", autoResultMap = true)
public class EcommerceOrder {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 内部订单号（系统生成，全局唯一） */
    private String orderNo;

    private String userId;

    /** 关联会话ID */
    private String conversationId;

    /** 关联售后工单ID（after_sale_ticket.order_id），可为空 */
    @TableField("after_sale_ticket_id")
    private String ticketId;

    /** 订单业务类型 */
    private OrderType bizType;

    /** 下单渠道：app / mini-program / web 等 */
    private String platform;

    /** 外部平台主订单号 */
    private String externalOrderNo;


    /** 统一预订状态 */
    private OrderStatus status;

    /** 外部平台原始状态码/文案 */
    private String externalStatus;

    /** 支付状态：UNPAID/PAID/REFUNDED */
    private String paymentStatus;

    /** 订单标题（商品名 + 规格 / 数量） */
    private String title;

    /** 订单总金额（元） */
    private BigDecimal totalAmount;

    /** 币种 */
    private String currency;

    private String contactName;

    private String contactPhone;

    /** 服务开始时间（出发/入住） */
    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant startTime;

    /** 服务结束时间（到达/离店） */
    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant endTime;

    /** 平台原始/明细信息 JSON 字符串（规格、数量、物流等） */
    private String detail;

    private String remark;

    /** 下单时间 */
    @TableField(typeHandler = InstantTypeHandler.class)
    private Instant orderedAt;

    @TableField(fill = FieldFill.INSERT, typeHandler = InstantTypeHandler.class)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE, typeHandler = InstantTypeHandler.class)
    private Instant updatedAt;

    @TableLogic
    @TableField(value = "deleted")
    private Integer deleted;

    public EcommerceOrder() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getOrderNo() {
        return orderNo;
    }

    public void setOrderNo(String orderNo) {
        this.orderNo = orderNo;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = ticketId;
    }

    public OrderType getBizType() {
        return bizType;
    }

    public void setBizType(OrderType bizType) {
        this.bizType = bizType;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getExternalOrderNo() {
        return externalOrderNo;
    }

    public void setExternalOrderNo(String externalOrderNo) {
        this.externalOrderNo = externalOrderNo;
    }



    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getExternalStatus() {
        return externalStatus;
    }

    public void setExternalStatus(String externalStatus) {
        this.externalStatus = externalStatus;
    }

    public String getPaymentStatus() {
        return paymentStatus;
    }

    public void setPaymentStatus(String paymentStatus) {
        this.paymentStatus = paymentStatus;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getContactName() {
        return contactName;
    }

    public void setContactName(String contactName) {
        this.contactName = contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public void setContactPhone(String contactPhone) {
        this.contactPhone = contactPhone;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Instant getOrderedAt() {
        return orderedAt;
    }

    public void setOrderedAt(Instant orderedAt) {
        this.orderedAt = orderedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
