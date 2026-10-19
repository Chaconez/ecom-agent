package com.ecom.cs.business.preference.repo;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ecom.cs.business.preference.entity.UserPreference;
import com.ecom.cs.business.preference.mapper.UserPreferenceMapper;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 用户偏好数据库实现（基于 MyBatis-Plus）
 *
 * @author Hollis
 */
@Repository
public class UserPreferenceRepositoryImpl implements UserPreferenceRepository {

    @Autowired
    private UserPreferenceMapper userPreferenceMapper;

    @Override
    public List<UserPreference> findByUserId(String userId) {
        return userPreferenceMapper.selectList(
                new LambdaQueryWrapper<UserPreference>()
                        .eq(UserPreference::getUserId, userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void replaceAll(String userId, Map<String, Object> preferences) {
        // 1. 删除该用户历史偏好（replace 语义，与前端表单全量勾选保持一致）
        userPreferenceMapper.delete(
                new LambdaUpdateWrapper<UserPreference>()
                        .eq(UserPreference::getUserId, userId));
        if (preferences == null || preferences.isEmpty()) {
            return;
        }
        // 2. 逐条插入（多值存 JSON 数组字符串，单值存原始字符串）
        for (Map.Entry<String, Object> entry : preferences.entrySet()) {
            Object value = entry.getValue();
            String valueStr;
            if (value instanceof List<?> list) {
                valueStr = list.isEmpty() ? "" : JSON.toJSONString(list);
            } else {
                valueStr = value != null ? value.toString() : "";
            }
            if (valueStr.isBlank()) {
                continue;
            }
            UserPreference pref = new UserPreference();
            pref.setUserId(userId);
            pref.setPrefKey(entry.getKey());
            pref.setPrefValue(valueStr);
            userPreferenceMapper.insert(pref);
        }
    }
}
