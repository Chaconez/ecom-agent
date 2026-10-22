package com.ecom.cs.agent.memory;

import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.bailian.BailianLongTermMemory;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

/**
 * 旅行偏好长期记忆工厂。
 *
 * <p>负责根据当前登录用户动态构建 {@link UserPreferenceLongTermMemory} 实例。
 * 百炼记忆库相关参数从 {@code application.yml} 读取；{@code userId} 在每次 Agent
 * 构建时从请求上下文中传入，确保多用户隔离。</p>
 *
 * @author Hollis
 */
public class UserPreferenceLongTermMemoryFactory {

    private static final Logger logger = LoggerFactory.getLogger(UserPreferenceLongTermMemoryFactory.class);

    private final String apiKey;
    private final String memoryLibraryId;
    private final String projectId;
    private final String profileSchema;
    private final UserPreferenceMemoryCache cache;

    public UserPreferenceLongTermMemoryFactory(
            String apiKey,
            String memoryLibraryId,
            String projectId,
            String profileSchema,
            UserPreferenceMemoryCache cache) {
        this.apiKey = apiKey;
        this.memoryLibraryId = memoryLibraryId;
        this.projectId = projectId;
        this.profileSchema = profileSchema;
        this.cache = cache;
    }

    /**
     * 为指定用户创建旅行偏好长期记忆实例。
     *
     * <p>若未配置百炼 API Key，则返回 {@code null}，Agent 将回退到无长期记忆运行，
     * 避免启动或运行时因缺失配置而抛出异常。</p>
     *
     * @param userId 当前登录用户 ID
     * @return 长期记忆实例，未配置时返回 {@code null}
     */
    public LongTermMemory create(String userId,Integer topK) {
        if (!StringUtils.hasText(apiKey)) {
            logger.warn("[UserPreferenceLTM] 未配置 agentscope.bailian.api-key，跳过长期记忆初始化，userId={}", userId);
            return null;
        }
        if (!StringUtils.hasText(userId)) {
            logger.warn("[UserPreferenceLTM] userId 为空，跳过长期记忆初始化");
            return null;
        }

        BailianLongTermMemory bailianMemory = BailianLongTermMemory.builder()
                .apiKey(apiKey)
                .userId(userId)
                .memoryLibraryId(StringUtils.hasText(memoryLibraryId) ? memoryLibraryId : null)
                .projectId(StringUtils.hasText(projectId) ? projectId : null)
                .profileSchema(StringUtils.hasText(profileSchema) ? profileSchema : null)
                .metadata(Map.of("source", "ecom-cs-agent"))
                .topK(topK)
                .build();

        logger.info("[UserPreferenceLTM] 已为用户 {} 创建百炼长期记忆实例", userId);
        return new UserPreferenceLongTermMemory(bailianMemory, cache, userId);
    }
}
