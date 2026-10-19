package com.ecom.cs.business.approval.entity;

import com.baomidou.mybatisplus.annotation.EnumValue;

/**
 * 工单（审批单）状态枚举
 * <ul>
 *   <li>PENDING   - 待处理</li>
 *   <li>APPROVED  - 已通过</li>
 *   <li>REJECTED  - 已驳回</li>
 *   <li>CANCELLED - 已撤销</li>
 * </ul>
 *
 * @author Hollis
 */
public enum ApprovalRecordStatus {

    /**
     * 待处理
     */
    PENDING("PENDING", "待处理"),
    /**
     * 已通过
     */
    APPROVED("APPROVED", "已通过"),
    /**
     * 已驳回
     */
    REJECTED("REJECTED", "已驳回"),
    /**
     * 已撤销
     */
    CANCELLED("CANCELLED", "已撤销");

    /** 存入数据库的值（与历史字符串兼容） */
    @EnumValue
    private final String code;

    /** 中文显示名 */
    private final String label;

    ApprovalRecordStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 根据 code 安全解析，非法值返回 null */
    public static ApprovalRecordStatus of(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (ApprovalRecordStatus s : values()) {
            if (s.code.equalsIgnoreCase(code)) {
                return s;
            }
        }
        return null;
    }
}
