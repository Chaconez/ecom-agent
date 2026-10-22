package com.ecom.cs.business.policy.repo;

import com.ecom.cs.business.policy.entity.AfterSalePolicyRule;

import java.util.Optional;

/**
 * @author Hollis
 */
public interface AfterSalePolicyRuleRepository {

    /**
     * 根据职级数字和城市等级查询匹配的政策规则。
     * 城市等级取值：一线 / 新一线 / 其他
     */
    Optional<AfterSalePolicyRule> findByLevelAndCityTier(int levelNum, String cityTier);
}
