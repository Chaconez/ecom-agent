package com.ecom.cs.business.preference.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecom.cs.business.preference.entity.UserPreference;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户偏好 Mapper
 *
 * @author Hollis
 */
@Mapper
public interface UserPreferenceMapper extends BaseMapper<UserPreference> {
}
