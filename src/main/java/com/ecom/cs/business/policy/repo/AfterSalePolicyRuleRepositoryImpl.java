package com.ecom.cs.business.policy.repo;

import com.ecom.cs.business.policy.entity.AfterSalePolicyRule;
import com.ecom.cs.business.policy.mapper.AfterSalePolicyRuleMapper;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

/**
 * 售后政策规则数据库实现（基于 MyBatis-Plus）
 *
 * @author Hollis
 */
@Repository
public class AfterSalePolicyRuleRepositoryImpl implements AfterSalePolicyRuleRepository {

    @Autowired
    private AfterSalePolicyRuleMapper policyRuleMapper;

    @Override
    public Optional<AfterSalePolicyRule> findByLevelAndCityTier(int levelNum, String cityTier) {
        return Optional.ofNullable(
                policyRuleMapper.findByLevelAndTier(levelNum, cityTier));
    }
}
