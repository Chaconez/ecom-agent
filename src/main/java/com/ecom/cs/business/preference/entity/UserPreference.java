package com.ecom.cs.business.preference.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 用户偏好持久化实体，对应 {@code user_preference} 表。
 *
 * <p>百炼长期记忆存在索引延迟（record 后立即 retrieve 可能为空），
 * 故以本表作为偏好权威源：保存时双写（DB + LTM），读取时优先查库。</p>
 *
 * @author Hollis
 */
@TableName(value = "user_preference")
public class UserPreference {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private String userId;

    /** 偏好字段 key，如 fav_categories / price_preference */
    private String prefKey;

    /** 偏好值：单值为字符串，多值为 JSON 数组字符串 */
    private String prefValue;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPrefKey() {
        return prefKey;
    }

    public void setPrefKey(String prefKey) {
        this.prefKey = prefKey;
    }

    public String getPrefValue() {
        return prefValue;
    }

    public void setPrefValue(String prefValue) {
        this.prefValue = prefValue;
    }
}
