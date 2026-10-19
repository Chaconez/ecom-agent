package com.ecom.cs.business.product.repo;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ecom.cs.business.product.entity.Product;
import com.ecom.cs.business.product.mapper.ProductMapper;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * 商品库数据库实现（基于 MyBatis-Plus）
 *
 * @author Hollis
 */
@Repository
public class ProductRepositoryImpl implements ProductRepository {

    @Autowired
    private ProductMapper productMapper;

    @Override
    public List<Product> findByCategories(List<String> categories) {
        if (categories == null || categories.isEmpty()) {
            return List.of();
        }
        return productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .in(Product::getCategory, categories)
                        .orderByDesc(Product::getSales));
    }

    @Override
    public List<Product> findHot(int limit) {
        return productMapper.selectList(
                new LambdaQueryWrapper<Product>()
                        .orderByDesc(Product::getSales)
                        .last("LIMIT " + Math.max(1, limit)));
    }

    @Override
    public List<Product> findAll() {
        return productMapper.selectList(null);
    }
}
