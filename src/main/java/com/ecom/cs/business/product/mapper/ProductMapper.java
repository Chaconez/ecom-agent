package com.ecom.cs.business.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecom.cs.business.product.entity.Product;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商品库 Mapper
 *
 * @author Hollis
 */
@Mapper
public interface ProductMapper extends BaseMapper<Product> {
}
