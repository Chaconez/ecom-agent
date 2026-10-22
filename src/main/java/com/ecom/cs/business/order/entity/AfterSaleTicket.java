package com.ecom.cs.business.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ecom.cs.config.InstantTypeHandler;
import java.time.Instant;

/**
 * 售后工单（售后工单单）
 *
 * @author Hollis
 */
@TableName(value = "after_sale_ticket", autoResultMap = true)
public class AfterSaleTicket {

    @TableId(value = "order_id", type = IdType.INPUT)
    private String orderId;
    private String userId;
    private String destination;
    private String departureCity;
    private String departureDate;
    private String returnDate;
    private String purpose;
    private AfterSaleTicketStatus status;
    /** 关联审批单ID，提交审批后写入 */
    private String approvalId;
    /** 行程方案 HTML 在 MinIO 中的对象 key（如 plans/to_20260701_001/1721700000.html） */
    private String planHtmlUrl;
    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT, typeHandler = InstantTypeHandler.class)
    private Instant createdAt;
    /** 最后修改时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE, typeHandler = InstantTypeHandler.class)
    private Instant updatedAt;

    public AfterSaleTicket() {
    }

    /**
     * 创建 Builder，链式构造售后工单，避免长参数构造器与散落的 setter 调用
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 售后工单 Builder
     */
    public static final class Builder {

        private final AfterSaleTicket order = new AfterSaleTicket();

        private Builder() {
        }

        public Builder orderId(String orderId) {
            order.orderId = orderId;
            return this;
        }

        public Builder userId(String userId) {
            order.userId = userId;
            return this;
        }

        public Builder destination(String destination) {
            order.destination = destination;
            return this;
        }

        public Builder departureCity(String departureCity) {
            order.departureCity = departureCity;
            return this;
        }

        public Builder departureDate(String departureDate) {
            order.departureDate = departureDate;
            return this;
        }

        public Builder returnDate(String returnDate) {
            order.returnDate = returnDate;
            return this;
        }

        public Builder purpose(String purpose) {
            order.purpose = purpose;
            return this;
        }

        public Builder status(AfterSaleTicketStatus status) {
            order.status = status;
            return this;
        }

        public Builder approvalId(String approvalId) {
            order.approvalId = approvalId;
            return this;
        }

        public AfterSaleTicket build() {
            return order;
        }
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

    public String getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(String approvalId) {
        this.approvalId = approvalId;
    }

    public String getPlanHtmlUrl() {
        return planHtmlUrl;
    }

    public void setPlanHtmlUrl(String planHtmlUrl) {
        this.planHtmlUrl = planHtmlUrl;
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getDestination() {
        return destination;
    }

    public void setDestination(String destination) {
        this.destination = destination;
    }

    public String getDepartureCity() {
        return departureCity;
    }

    public void setDepartureCity(String departureCity) {
        this.departureCity = departureCity;
    }

    public String getDepartureDate() {
        return departureDate;
    }

    public void setDepartureDate(String departureDate) {
        this.departureDate = departureDate;
    }

    public String getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(String returnDate) {
        this.returnDate = returnDate;
    }

    public String getPurpose() {
        return purpose;
    }

    public void setPurpose(String purpose) {
        this.purpose = purpose;
    }

    public AfterSaleTicketStatus getStatus() {
        return status;
    }

    public void setStatus(AfterSaleTicketStatus status) {
        this.status = status;
    }
}
