package com.ecom.cs.business.order.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ecom.cs.config.InstantTypeHandler;
import java.time.Instant;

/**
 * 售后工单，对应 {@code after_sale_ticket} 表。
 *
 * <p>工单用于承载用户发起的退换货、维修、投诉、发票等服务请求，
 * 提交后进入审批流，审批结果回写本表的 {@link #status}。</p>
 *
 * @author Hollis
 */
@TableName(value = "after_sale_ticket", autoResultMap = true)
public class AfterSaleTicket {

    @TableId(value = "order_id", type = IdType.INPUT)
    private String orderId;
    private String userId;
    /** 申请售后的商品 / 服务名称，含规格与数量 */
    private String productName;
    /** 问题描述（用户诉求 + 现象说明） */
    private String issueDesc;
    /** 购买日期（yyyy-MM-dd），用于判断是否在售后政策有效期内 */
    private String purchaseDate;
    private AfterSaleTicketStatus status;
    /** 关联审批单ID，提交审批后写入 */
    private String approvalId;
    /** 工单详情 HTML 在 MinIO 中的对象 key（如 tickets/to_20260701_001/1721700000.html） */
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

        public Builder productName(String productName) {
            order.productName = productName;
            return this;
        }

        public Builder issueDesc(String issueDesc) {
            order.issueDesc = issueDesc;
            return this;
        }

        public Builder purchaseDate(String purchaseDate) {
            order.purchaseDate = purchaseDate;
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

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getIssueDesc() {
        return issueDesc;
    }

    public void setIssueDesc(String issueDesc) {
        this.issueDesc = issueDesc;
    }

    public String getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(String purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public AfterSaleTicketStatus getStatus() {
        return status;
    }

    public void setStatus(AfterSaleTicketStatus status) {
        this.status = status;
    }
}
