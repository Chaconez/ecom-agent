package com.ecom.cs.business.audit.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.ecom.cs.config.InstantTypeHandler;
import java.time.Instant;

/**
 * 管理端操作审计日志实体，对应 admin_audit_log 表。
 *
 * <p>记录管理端（客服/管理员）的关键动作：接管会话、结束接管、人工发言、审批决策等，
 * 满足「谁在什么时候对哪个对象做了什么」的追溯要求。</p>
 *
 * @author Hollis
 */
@TableName(value = "admin_audit_log", autoResultMap = true)
public class AdminAuditLog {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 操作人（sa-token loginId，即 user_account.user_id） */
    private String adminUserId;

    /** 动作：takeover / release / human_message / approval_decide */
    private String action;

    /** 对象类型：conversation / approval */
    private String targetType;

    /** 对象 ID（会话 ID / 审批单 ID） */
    private String targetId;

    /** 操作详情快照（JSON 字符串，如决策结果、消息摘要） */
    private String detail;

    @TableField(fill = com.baomidou.mybatisplus.annotation.FieldFill.INSERT,
            typeHandler = InstantTypeHandler.class)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAdminUserId() {
        return adminUserId;
    }

    public void setAdminUserId(String adminUserId) {
        this.adminUserId = adminUserId;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
