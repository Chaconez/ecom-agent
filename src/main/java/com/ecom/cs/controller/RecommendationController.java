package com.ecom.cs.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.ecom.cs.business.recommendation.service.RecommendationService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 个性化推荐接口：「为你推荐」页面数据源。
 *
 * <p>推荐策略：用户偏好（user_preference 表）→ 品类召回候选商品 →
 * LLM 重排并生成可解释推荐理由 → 规则兜底。无偏好时退化为热销榜。</p>
 *
 * @author Hollis
 */
@RestController
@RequestMapping("/api/recommendations")
@CrossOrigin(origins = "*")
public class RecommendationController {

    @Autowired
    private RecommendationService recommendationService;

    /**
     * 获取当前登录用户的个性化推荐商品列表。
     *
     * @param limit 返回条数，默认 8
     */
    @GetMapping
    @SaCheckLogin
    public Map<String, Object> list(@RequestParam(defaultValue = "8") int limit) {
        String userId = StpUtil.getLoginIdAsString();
        List<Map<String, Object>> items = recommendationService.recommend(userId, limit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);
        result.put("hasPreferences", recommendationService.hasPreferences(userId));
        result.put("items", items);
        return result;
    }
}
