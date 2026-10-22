package com.ecom.cs.agent.tools.model;

/**
 * 售后工单重复检测的严重等级。
 * <ul>
 *   <li>{@code HIGH}   — 明确重复：同商品、同购买日期、同问题描述已存在生效中工单。</li>
 *   <li>{@code MEDIUM} — 疑似重复：同一商品已存在其他生效中工单，需确认是否同一问题。</li>
 *   <li>{@code LOW}    — 仅作提示：信息相近，可继续提交。</li>
 * </ul>
 * <p>fastjson2 默认按 {@code name()} 序列化，输出 JSON 与原字符串常量完全一致。
 *
 * @author Hollis
 */
public enum ConflictSeverity {

    /** 明确重复 */
    HIGH(0),
    /** 疑似重复 */
    MEDIUM(1),
    /** 仅作提示 */
    LOW(2);

    private final int rank;

    ConflictSeverity(int rank) {
        this.rank = rank;
    }

    /** 排序权重：数值越小越严重 */
    public int rank() {
        return rank;
    }
}
