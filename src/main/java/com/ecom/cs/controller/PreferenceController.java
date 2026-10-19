package com.ecom.cs.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.alibaba.fastjson2.JSON;
import com.ecom.cs.agent.memory.TravelPreferenceLongTermMemoryFactory;
import com.ecom.cs.business.preference.entity.UserPreference;
import com.ecom.cs.business.preference.repo.UserPreferenceRepository;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 用户偏好管理接口。
 *
 * <p>提供预定义的用户偏好选项（购物 / 服务 / 物流售后 / 常用信息）供用户勾选，
 * 保存后写入百炼长期记忆，供 Agent 在服务过程中通过 {@code retrieve_from_memory} 召回。</p>
 *
 * <p>读取时直接走 {@link com.ecom.cs.agent.memory.TravelPreferenceLongTermMemory#retrieve}，
 * 其内部已有 Redis 缓存层（{@link com.ecom.cs.agent.memory.TravelPreferenceMemoryCache}），
 * 无需额外存储。</p>
 *
 * @author Hollis
 */
@RestController
@RequestMapping("/api/preferences")
@CrossOrigin(origins = "*")
public class PreferenceController {

    private static final Logger logger = LoggerFactory.getLogger(PreferenceController.class);

    @Autowired
    private TravelPreferenceLongTermMemoryFactory memoryFactory;

    @Autowired
    private UserPreferenceRepository preferenceRepository;

    @Autowired
    @Qualifier("fastModel")
    private Model fastModel;

    /**
     * 获取所有可供选择的偏好项（分类 + 选项列表）。
     */
    @GetMapping("/options")
    @SaCheckLogin
    public List<Map<String, Object>> getPreferenceOptions() {
        return buildPreferenceOptions();
    }

    /**
     * 获取当前用户已保存的偏好。
     *
     * <p>读取优先级：DB（{@code user_preference} 表，权威源）→ 百炼长期记忆（兼容历史数据）。
     * 长期记忆存在索引延迟，record 后立即 retrieve 可能为空，故不能作为唯一来源。</p>
     */
    @GetMapping
    @SaCheckLogin
    public ResponseEntity<Map<String, Object>> getUserPreferences() {
        String userId = StpUtil.getLoginIdAsString();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", userId);

        // 1. 优先读 DB
        List<UserPreference> rows = preferenceRepository.findByUserId(userId);
        if (!rows.isEmpty()) {
            Map<String, Object> prefs = new LinkedHashMap<>();
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
            result.put("preferences", prefs);
            result.put("source", "db");
            return ResponseEntity.ok(result);
        }

        // 2. DB 无记录时回退长期记忆（从长期记忆召回，已有 Redis 缓存层加速）
        String memorySummary = retrieveFromLongTermMemory(userId);
        if (memorySummary != null && !memorySummary.isBlank()) {
            result.put("memorySummary", memorySummary);
            // 通过 LLM 将自然语言解析为结构化偏好 JSON
            Map<String, Object> parsed = parseMemoryToPreferences(memorySummary);
            if (parsed != null && !parsed.isEmpty()) {
                result.put("preferences", parsed);
                result.put("source", "memory");
            }
        }
        return ResponseEntity.ok(result);
    }

    /**
     * 保存用户偏好设置：全量写入 DB（权威源，供推荐引擎查询），并双写长期记忆（供 Agent 对话召回）。
     */
    @PostMapping
    @SaCheckLogin
    public ResponseEntity<Map<String, Object>> saveUserPreferences(@RequestBody Map<String, Object> preferences) {
        String userId = StpUtil.getLoginIdAsString();
        logger.info("[PreferenceController] 保存用户偏好 userId={}, keys={}", userId, preferences.keySet());

        // DB 权威源：replace 语义全量覆盖
        preferenceRepository.replaceAll(userId, preferences);
        // 长期记忆双写（失败不阻断保存）
        recordToLongTermMemory(userId, preferences);

        return ResponseEntity.ok(Map.of("saved", true));
    }

    /**
     * 通过 LLM 将自然语言偏好描述解析为结构化 JSON。
     *
     * <p>使用 fastModel（qwen-flash）完成轻量级抽取任务，
     * 将百炼 retrieve 返回的自然语言映射回预定义选项值。</p>
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseMemoryToPreferences(String memorySummary) {
        try {
            String systemPrompt = buildParsePrompt();
            List<Msg> messages = List.of(
                    Msg.builder()
                            .role(MsgRole.SYSTEM).name("system")
                            .content(TextBlock.builder().text(systemPrompt).build())
                            .build(),
                    Msg.builder()
                            .role(MsgRole.USER).name("user")
                            .content(TextBlock.builder().text(memorySummary).build())
                            .build()
            );
            List<ChatResponse> responses = fastModel.stream(messages, null, GenerateOptions.builder().build()).collectList().block();
            String jsonText = extractText(responses);
            // 去除可能的 markdown 代码块标记
            jsonText = jsonText.strip();
            if (jsonText.startsWith("```")) {
                jsonText = jsonText.replaceFirst("```(?:json)?\\s*", "").replaceFirst("\\s*```$", "");
            }
            logger.debug("[PreferenceController] LLM 解析结果: {}", jsonText);
            return JSON.parseObject(jsonText, Map.class);
        } catch (Exception e) {
            logger.warn("[PreferenceController] LLM 解析偏好失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 构建 LLM 解析提示词，包含所有合法选项定义。
     */
    private String buildParsePrompt() {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一个 JSON 提取器。根据用户的自然语言偏好描述，提取出结构化的用户偏好 JSON。\n");
        sb.append("\n规则：\n");
        sb.append("1. 只输出纯 JSON，不要任何解释\n");
        sb.append("2. key 必须是下面定义的字段名\n");
        sb.append("3. single 类型的值为字符串，multi 类型的值为字符串数组\n");
        sb.append("4. 值必须从对应的 options 中选取，尽量模糊匹配（如'国航'匹配'国航(CA)'）\n");
        sb.append("5. 如果某个字段在描述中未提及，不要输出该字段\n");
        sb.append("6. 如果描述中的值无法匹配任何选项，跳过该字段\n");
        sb.append("\n可用字段定义：\n");

        List<Map<String, Object>> categories = buildPreferenceOptions();
        for (Map<String, Object> cat : categories) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) cat.get("items");
            for (Map<String, Object> item : items) {
                sb.append("- ").append(item.get("key"))
                        .append(" (").append(item.get("type")).append("): ")
                        .append(item.get("options")).append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * 将流式 ChatResponse 中的所有 TextBlock 拼接为完整字符串。
     */
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

    /**
     * 从百炼长期记忆中召回用户偏好摘要。
     * 已经被 {@link com.ecom.cs.agent.memory.TravelPreferenceMemoryCache} 缓存，30 分钟内不会重复调百炼。
     */
    private String retrieveFromLongTermMemory(String userId) {
        try {
            LongTermMemory memory = memoryFactory.create(userId, 100);
            if (memory == null) {
                return null;
            }
            Msg queryMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .name("user")
                    .content(TextBlock.builder()
                            .text("我的偏好是什么？包括购物、服务沟通、物流售后等方面的偏好")
                            .build())
                    .build();
            String result = memory.retrieve(queryMsg).block();
            logger.info("[PreferenceController] 从长期记忆召回偏好 userId={}, length={}",
                    userId, result != null ? result.length() : 0);
            return result;
        } catch (Exception e) {
            logger.warn("[PreferenceController] 从长期记忆召回偏好失败 userId={}: {}", userId, e.getMessage());
            return null;
        }
    }

    /**
     * 将结构化偏好转为自然语言，通过百炼长期记忆 record 接口持久化。
     * record 成功后会自动失效 Redis 缓存，下次 retrieve 将拿到最新数据。
     */
    private void recordToLongTermMemory(String userId, Map<String, Object> preferences) {
        try {
            LongTermMemory memory = memoryFactory.create(userId, 100);
            if (memory == null) {
                logger.warn("[PreferenceController] 长期记忆未配置，跳过记录 userId={}", userId);
                return;
            }
            String naturalLanguage = formatPreferencesAsText(preferences);
            Msg userMsg = Msg.builder()
                    .role(MsgRole.USER)
                    .name("user")
                    .content(TextBlock.builder()
                            .text("请记住我的偏好设置：" + naturalLanguage)
                            .build())
                    .build();
            Msg assistantMsg = Msg.builder()
                    .role(MsgRole.ASSISTANT)
                    .name("assistant")
                    .content(TextBlock.builder()
                            .text("好的，我已记住您的偏好：" + naturalLanguage)
                            .build())
                    .build();
            memory.record(List.of(userMsg, assistantMsg)).block();
            logger.info("[PreferenceController] 偏好已记录到长期记忆 userId={}", userId);
        } catch (Exception e) {
            logger.warn("[PreferenceController] 记录偏好到长期记忆失败 userId={}: {}", userId, e.getMessage());
        }
    }

    private String formatPreferencesAsText(Map<String, Object> preferences) {
        Map<String, String> labels = getFieldLabels();
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Object> entry : preferences.entrySet()) {
            String label = labels.getOrDefault(entry.getKey(), entry.getKey());
            Object value = entry.getValue();
            String valueStr;
            if (value instanceof List<?> list) {
                valueStr = String.join("、", list.stream().map(Object::toString).toList());
            } else {
                valueStr = value != null ? value.toString() : "";
            }
            if (!valueStr.isBlank()) {
                sb.append(label).append("：").append(valueStr).append("；");
            }
        }
        return sb.toString();
    }

    private Map<String, String> getFieldLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("fav_categories", "常购品类");
        labels.put("price_preference", "价位偏好");
        labels.put("brand_preference", "品牌倾向");
        labels.put("decision_focus", "决策关注点");
        labels.put("reply_style", "回复风格");
        labels.put("contact_time", "方便联系时段");
        labels.put("notify_channel", "通知方式");
        labels.put("logistics_preference", "物流偏好");
        labels.put("aftersale_preference", "售后偏好");
        labels.put("pickup_time", "上门取件时段");
        labels.put("invoice_preference", "发票需求");
        labels.put("clothing_size", "服装尺码");
        labels.put("shoe_size", "鞋码");
        labels.put("delivery_method", "收货方式");
        labels.put("payment_preference", "支付偏好");
        return labels;
    }

    /**
     * 构建电商客服场景下的偏好选项：购物偏好 / 服务偏好 / 物流与售后 / 常用信息。
     * <p>选项值会随偏好一起写入长期记忆，Agent 在推荐商品、处理售后时召回。</p>
     */
    private List<Map<String, Object>> buildPreferenceOptions() {
        List<Map<String, Object>> categories = new ArrayList<>();

        categories.add(Map.of(
                "category", "shopping",
                "label", "购物偏好",
                "icon", "🛍️",
                "items", List.of(
                        Map.of("key", "fav_categories", "label", "常购品类", "type", "multi",
                                "options", List.of("数码电子", "家用电器", "服饰鞋包", "美妆护肤", "食品生鲜", "母婴亲子", "家居日用", "运动户外")),
                        Map.of("key", "price_preference", "label", "价位偏好", "type", "single",
                                "options", List.of("价格优先(尽量低价)", "性价比优先", "品质优先(预算内最好)", "无偏好")),
                        Map.of("key", "brand_preference", "label", "品牌倾向", "type", "multi",
                                "options", List.of("国货品牌", "国际大牌", "新锐小众品牌", "白牌工厂店", "无偏好")),
                        Map.of("key", "decision_focus", "label", "决策关注点", "type", "multi",
                                "options", List.of("价格优惠", "口碑评价", "品牌知名度", "功能参数", "售后服务", "物流时效"))
                )
        ));

        categories.add(Map.of(
                "category", "service",
                "label", "服务偏好",
                "icon", "💬",
                "items", List.of(
                        Map.of("key", "reply_style", "label", "回复风格", "type", "single",
                                "options", List.of("简洁直接", "详细全面", "耐心温和", "无偏好")),
                        Map.of("key", "contact_time", "label", "方便联系时段", "type", "multi",
                                "options", List.of("工作日白天", "工作日晚间", "周末", "节假日")),
                        Map.of("key", "notify_channel", "label", "通知方式", "type", "multi",
                                "options", List.of("站内消息", "短信", "电话", "邮件"))
                )
        ));

        categories.add(Map.of(
                "category", "logistics",
                "label", "物流与售后",
                "icon", "📦",
                "items", List.of(
                        Map.of("key", "logistics_preference", "label", "物流偏好", "type", "single",
                                "options", List.of("时效优先(尽快送达)", "价格优先(运费最低)", "时效价格平衡", "无偏好")),
                        Map.of("key", "aftersale_preference", "label", "售后偏好", "type", "single",
                                "options", List.of("优先退货退款", "优先换货", "优先补发", "优先维修", "无偏好")),
                        Map.of("key", "pickup_time", "label", "上门取件时段", "type", "multi",
                                "options", List.of("工作日", "周末", "晚间", "均可")),
                        Map.of("key", "invoice_preference", "label", "发票需求", "type", "single",
                                "options", List.of("电子发票", "纸质发票", "暂不需要", "无偏好"))
                )
        ));

        categories.add(Map.of(
                "category", "profile",
                "label", "常用信息",
                "icon", "🧾",
                "items", List.of(
                        Map.of("key", "clothing_size", "label", "服装尺码", "type", "single",
                                "options", List.of("XS", "S", "M", "L", "XL", "XXL", "无偏好")),
                        Map.of("key", "shoe_size", "label", "鞋码", "type", "single",
                                "options", List.of("35", "36", "37", "38", "39", "40", "41", "42", "43", "44", "45", "无偏好")),
                        Map.of("key", "delivery_method", "label", "收货方式", "type", "multi",
                                "options", List.of("送货上门", "驿站代收", "快递柜", "前台代收", "无偏好")),
                        Map.of("key", "payment_preference", "label", "支付偏好", "type", "single",
                                "options", List.of("在线支付", "货到付款", "分期付款", "无偏好"))
                )
        ));

        return categories;
    }
}