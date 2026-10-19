package com.ecom.cs.business.product.repo;

import com.ecom.cs.business.product.entity.Product;
import java.util.List;

/**
 * 商品库仓储接口
 *
 * @author Hollis
 */
public interface ProductRepository {

    /** 按品类集合查询商品（IN 查询） */
    List<Product> findByCategories(List<String> categories);

    /** 查询热销商品 TopN（按销量倒序） */
    List<Product> findHot(int limit);

    /** 查询全部商品 */
    List<Product> findAll();
}
