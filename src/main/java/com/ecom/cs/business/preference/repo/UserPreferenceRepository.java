package com.ecom.cs.business.preference.repo;

import com.ecom.cs.business.preference.entity.UserPreference;
import java.util.List;
import java.util.Map;

/**
 * 用户偏好仓储接口
 *
 * @author Hollis
 */
public interface UserPreferenceRepository {

    /** 查询用户全部偏好（pref_key -> pref_value 原始字符串） */
    List<UserPreference> findByUserId(String userId);

    /** 全量覆盖保存用户偏好（先删后插，保持与表单勾选一致） */
    void replaceAll(String userId, Map<String, Object> preferences);
}
