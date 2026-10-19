package com.ecom.cs.business.recommendation.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.ecom.cs.business.preference.entity.UserPreference;
import com.ecom.cs.business.preference.repo.UserPreferenceRepository;
import com.ecom.cs.business.product.entity.Product;
import com.ecom.cs.business.product.repo.ProductRepository;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * 个性化推荐服务：偏好召回 + LLM 重排与可解释推荐理由。
 *
 * <p>流程：
 * <ol>
 *   <li>从 {@code user_preference} 表读取用户偏好（权威源，规避长期记忆索引延迟）；</li>
 *   <li>按偏好的「常购品类」召回候选商品，无偏好时退化为热销榜；</li>
 *   <li>调用 fastModel 对候选集重排并生成一句话个性化推荐理由；</li>
 *   <li>LLM 异常时使用规则兜底（品类匹配 / 价格友好 / 热销好评）。</li>
 * </ol>
 *
 * @author Hollis
 */
@Service
public class RecommendationService {

    private static final Logger logger = LoggerFactory.getLogger(RecommendationService.class);

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private UserPreferenceRepository preferenceRepository;
    @Autowired
    @Qualifier("fastModel")
    private Model fastModel;

    /**
     * 为指定用户生成个性化推荐列表。
     *
     * @param userId 用户ID
     * @param limit  返回条数
     * @return 每项包含商品字段 + {@code reason}（推荐理由）+ {@code matchScore}
     */
    public List<Map<String, Object>> recommend(String userId, int limit) {
        Map<String, Object> prefs = loadPreferences(userId);

        // 1. 召回：偏好品类命中优先，无偏好走热销榜
        List<String> categories = extractCategories(prefs);
        List<Product> candidates = categories.isEmpty()
                ? productRepository.findHot(Math.max(limit * 2, 10))
                : productRepository.findByCategories(categories);
        if (candidates.isEmpty()) {
            candidates = productRepository.findHot(Math.max(limit * 2, 10));
        }

        // 2. LLM 重排 + 推荐理由（失败时返回空 map，走规则兜底）
        Map<String, RankedItem> ranked = rankWithLlm(prefs, candidates);

        // 3. 组装结果：LLM 有评分的按分数排在前，其余按销量兜底
        List<Map<String, Object>> result = new ArrayList<>();
        List<Product> sorted = new ArrayList<>(candidates);
        sorted.sort((a, b) -> {
            RankedItem ra = ranked.get(a.getProductId());
            RankedItem rb = ranked.get(b.getProductId());
            if (ra != null && rb != null) {
                return Double.compare(rb.score, ra.score);
            }
            if (ra != null) return -1;
            if (rb != null) return 1;
            return Integer.compare(
                    Optional.ofNullable(b.getSales()).orElse(0),
                    Optional.ofNullable(a.getSales()).orElse(0));
        });

        for (int i = 0; i < Math.min(limit, sorted.size()); i++) {
            Product p = sorted.get(i);
            RankedItem r = ranked.get(p.getProductId());
            String reason = r != null && r.reason != null && !r.reason.isBlank()
                    ? r.reason
                    : fallbackReason(prefs, categories, p);
            result.add(toView(p, reason, r != null ? r.score : 0.0));
        }
        logger.info("[RecommendationService] userId={}, 偏好品类={}, 候选={}, 返回={}",
                userId, categories, candidates.size(), result.size());
        return result;
    }

    /** 是否已配置偏好（供前端引导用户去偏好设置勾选） */
    public boolean hasPreferences(String userId) {
        return !loadPreferences(userId).isEmpty();
    }

    // ---------------------------------------------------------------- 内部实现

    /** 从 user_preference 表读取偏好并还原为结构化 map（多值还原为 List） */
    private Map<String, Object> loadPreferences(String userId) {
        Map<String, Object> prefs = new LinkedHashMap<>();
        List<UserPreference> rows = preferenceRepository.findByUserId(userId);
        for (UserPreference row : rows) {
            String value = row.getPrefValue();
            if (value == null || value.isBlank()) {
                continue;
            }
            if (value.startsWith("[")) {
                try {
                    prefs.put(row.getPrefKey(), JSON.parseArray(value));
                } catch (Exception e) {
                    prefs.put(row.getPrefKey(), value);
                }
            } else {
                prefs.put(row.getPrefKey(), value);
            }
        }
        return prefs;
    }

    /** 提取偏好品类列表 */
    @SuppressWarnings("unchecked")
    private List<String> extractCategories(Map<String, Object> prefs) {
        Object fav = prefs.get("fav_categories");
        if (fav instanceof List<?> list && !list.isEmpty()) {
            return list.stream().map(Object::toString).toList();
        }
        return List.of();
    }

    /** 调用 fastModel 重排并生成推荐理由；任何失败都返回空 map（不阻断主流程） */
    private Map<String, RankedItem> rankWithLlm(Map<String, Object> prefs, List<Product> candidates) {
        if (candidates.isEmpty()) {
            return Map.of();
        }
        try {
            String prompt = buildRankPrompt(prefs, candidates);
            List<Msg> messages = List.of(
                    Msg.builder().role(MsgRole.SYSTEM).name("system")
                            .content(TextBlock.builder().text(
                                    "你是电商个性化推荐引擎，擅长根据用户偏好挑选最合适的商品并给出真实的推荐理由。").build())
                            .build(),
                    Msg.builder().role(MsgRole.USER).name("user")
                            .content(TextBlock.builder().text(prompt).build())
                            .build());
            List<ChatResponse> responses = fastModel.stream(messages, null, GenerateOptions.builder().build())
                    .collectList().block();
            String jsonText = extractText(responses).strip();
            if (jsonText.startsWith("```")) {
                jsonText = jsonText.replaceFirst("```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
            }
            JSONArray arr = JSON.parseArray(jsonText);
            Map<String, RankedItem> ranked = new LinkedHashMap<>();
            for (int i = 0; i < arr.size(); i++) {
                com.alibaba.fastjson2.JSONObject obj = arr.getJSONObject(i);
                String pid = obj.getString("product_id");
                if (pid != null) {
                    ranked.put(pid, new RankedItem(obj.getDoubleValue("score"), obj.getString("reason")));
                }
            }
            logger.info("[RecommendationService] LLM 重排完成, 评分 {} 条", ranked.size());
            return ranked;
        } catch (Exception e) {
            logger.warn("[RecommendationService] LLM 重排失败，使用规则兜底: {}", e.getMessage());
            return Map.of();
        }
    }

    /** 构造重排提示词：用户偏好摘要 + 候选商品清单 + 输出格式约束 */
    private String buildRankPrompt(Map<String, Object> prefs, List<Product> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("请根据用户偏好，为下面所有候选商品逐一打分，并按匹配程度从高到低排序。\n\n");
        sb.append("【用户偏好】\n");
        if (prefs.isEmpty()) {
            sb.append("- 暂无偏好信息（新用户）\n");
        } else {
            prefs.forEach((k, v) -> sb.append("- ").append(k).append(": ").append(v).append("\n"));
        }
        sb.append("\n【候选商品】\n");
        for (Product p : candidates) {
            sb.append("- product_id=").append(p.getProductId())
                    .append(" | ").append(p.getName())
                    .append(" | 品类=").append(p.getCategory())
                    .append(" | 价格=").append(p.getPrice()).append("元")
                    .append(" | 标签=").append(p.getTags())
                    .append(" | 评分=").append(p.getRating())
                    .append(" | 销量=").append(p.getSales())
                    .append("\n");
        }
        sb.append("\n【输出要求】\n");
        sb.append("1. 只输出纯 JSON 数组，不要任何解释\n");
        sb.append("2. 每个元素形如 {\"product_id\": \"...\", \"score\": 0-100, \"reason\": \"一句话推荐理由\"}\n");
        sb.append("3. 必须覆盖上面所有候选商品，每个都要打分\n");
        sb.append("4. score 表示与该用户的匹配程度；reason 必须结合用户偏好说明为什么适合他，20 字以内，口语化\n");
        sb.append("5. 没有偏好的用户，优先口碑与销量，reason 说明热销原因\n");
        return sb.toString();
    }

    /** 规则兜底推荐理由 */
    private String fallbackReason(Map<String, Object> prefs, List<String> categories, Product p) {
        if (categories.contains(p.getCategory())) {
            return "匹配你偏好的「" + p.getCategory() + "」品类";
        }
        Object pricePref = prefs.get("price_preference");
        if ("价格优先(尽量低价)".equals(pricePref) && p.getPrice() != null) {
            return "价格友好，预算内优选";
        }
        if (p.getRating() != null && p.getRating().doubleValue() >= 4.7) {
            return "高分好评热销款，口碑稳定";
        }
        return "热销商品，大家都在买";
    }

    private Map<String, Object> toView(Product p, String reason, double score) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("productId", p.getProductId());
        v.put("name", p.getName());
        v.put("category", p.getCategory());
        v.put("brand", p.getBrand());
        v.put("price", p.getPrice() != null ? p.getPrice().toPlainString() : null);
        v.put("originalPrice", p.getOriginalPrice() != null ? p.getOriginalPrice().toPlainString() : null);
        v.put("tags", p.getTags());
        v.put("rating", p.getRating() != null ? p.getRating().toPlainString() : null);
        v.put("sales", p.getSales());
        v.put("imageEmoji", p.getImageEmoji());
        v.put("description", p.getDescription());
        v.put("reason", reason);
        v.put("matchScore", score);
        return v;
    }

    private static String extractText(List<ChatResponse> responses) {
        if (responses == null || responses.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatResponse response : responses) {
            List<ContentBlock> blocks = response.getContent();
            if (blocks == null) {
                continue;
            }
            for (ContentBlock block : blocks) {
                if (block instanceof TextBlock textBlock) {
                    sb.append(textBlock.getText());
                }
            }
        }
        return sb.toString();
    }

    /** LLM 评分条目 */
    private record RankedItem(double score, String reason) {
    }
}
