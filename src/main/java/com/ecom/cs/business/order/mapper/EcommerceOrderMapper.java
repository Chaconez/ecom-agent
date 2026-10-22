package com.ecom.cs.business.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecom.cs.business.order.entity.EcommerceOrder;
import org.apache.ibatis.annotations.Mapper;

/**
 * 预订记录 Mapper
 *
 * @author Hollis
 */
@Mapper
public interface EcommerceOrderMapper extends BaseMapper<EcommerceOrder> {
}
