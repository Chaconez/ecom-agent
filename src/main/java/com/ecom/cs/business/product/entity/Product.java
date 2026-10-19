package com.ecom.cs.business.product.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;

/**
 * 商品库实体，对应 {@code ecom_product} 表（mock 数据，P3 替换为真实商品中心）。
 *
 * @author Hollis
 */
@TableName(value = "ecom_product")
public class Product {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 商品编号 */
    private String productId;

    /** 商品名称 */
    private String name;

    /** 品类（与偏好选项对齐：数码电子 / 服饰鞋包 / 美妆护肤 / 食品生鲜 / 母婴亲子 / 家居日用 / 运动户外 / 家用电器） */
    private String category;

    /** 品牌 */
    private String brand;

    /** 售价 */
    private BigDecimal price;

    /** 原价（划线价） */
    private BigDecimal originalPrice;

    /** 标签，逗号分隔 */
    private String tags;

    /** 评分 0-5 */
    private BigDecimal rating;

    /** 销量 */
    private Integer sales;

    /** 商品图标（emoji，避免外链图） */
    private String imageEmoji;

    /** 一句话卖点 */
    private String description;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getOriginalPrice() {
        return originalPrice;
    }

    public void setOriginalPrice(BigDecimal originalPrice) {
        this.originalPrice = originalPrice;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public BigDecimal getRating() {
        return rating;
    }

    public void setRating(BigDecimal rating) {
        this.rating = rating;
    }

    public Integer getSales() {
        return sales;
    }

    public void setSales(Integer sales) {
        this.sales = sales;
    }

    public String getImageEmoji() {
        return imageEmoji;
    }

    public void setImageEmoji(String imageEmoji) {
        this.imageEmoji = imageEmoji;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
