package com.ecom.cs.agent.tools.model;

/**
 * 售后工单重复检测的冲突类型。
 * <p>fastjson2 默认按 {@code name()} 序列化，输出 JSON 与原字符串常量完全一致。
 *
 * @author Hollis
 */
public enum ConflictType {

    /** 同商品 + 同购买日期 + 同问题描述：完全重复提交 */
    DUPLICATE_TICKET,

    /** 同商品存在其他生效中工单：可能重复售后，也可能是同一商品的不同问题 */
    SAME_PRODUCT_ACTIVE
}
