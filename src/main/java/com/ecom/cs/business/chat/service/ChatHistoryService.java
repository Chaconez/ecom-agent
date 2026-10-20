package com.ecom.cs.business.chat.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.ecom.cs.business.chat.entity.ChatConversation;
import com.ecom.cs.business.chat.entity.ChatMessage;
import com.ecom.cs.business.chat.repository.ChatHistoryRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 对话历史服务：负责会话与消息的持久化、查询与删除。
 *
 * @author Hollis
 */
@Service
public class ChatHistoryService {

    private static final Logger logger = LoggerFactory.getLogger(ChatHistoryService.class);

    private static final String DEFAULT_TITLE = "新对话";

    /** 对话标题最大字符数 */
    private static final int TITLE_MAX_LENGTH = 24;

    /** 会话模式：AI 智能助手处理中。 */
    public static final String MODE_AI = "AI";
    /** 会话模式：人工客服接管中（转人工）。 */
    public static final String MODE_HUMAN = "HUMAN";

    @Autowired
    private ChatHistoryRepository chatHistoryRepository;

    /**
     * 查询某用户的所有会话，按最后更新时间倒序。
     */
    public List<ConversationView> listConversations(String userId) {
        return chatHistoryRepository.findConversationsByUserId(userId).stream()
                .map(this::toConversationView)
                .collect(Collectors.toList());
    }

    /**
     * 查询全部会话（管理端视角，不按用户过滤），按最后更新时间倒序。
     *
     * <p>附带每条会话的最后一条消息预览（客服工作台列表需要）。演示数据量下
     * N+1 查询开销可忽略；量大后应改为一次 GROUP BY 子查询或宽表。</p>
     */
    public List<AdminConversationView> listAllConversationsForAdmin() {
        return chatHistoryRepository.findAllConversations().stream()
                .map(c -> {
                    List<ChatMessage> last = chatHistoryRepository.findRecentMessages(
                            c.getConversationId(), 1);
                    String preview = last.isEmpty() ? "" : String.valueOf(last.get(0).getContent());
                    String previewRole = last.isEmpty() ? "" : String.valueOf(last.get(0).getRole());
                    return new AdminConversationView(
                            c.getConversationId(),
                            c.getUserId(),
                            c.getTitle(),
                            normalizeMode(c.getMode()),
                            preview.length() > 60 ? preview.substring(0, 60) + "…" : preview,
                            previewRole,
                            toEpochMilli(c.getUpdatedAt()));
                })
                .collect(Collectors.toList());
    }
    /**
     * 查询某会话下的所有消息（管理端视角，不做用户归属校验——客服可查看任意会话）。
     */
    public List<MessageView> listMessagesForAdmin(String conversationId) {
        return chatHistoryRepository.findMessagesByConversationId(conversationId).stream()
                .map(this::toMessageView)
                .collect(Collectors.toList());
    }

    /**
     * 查询会话当前模式（AI / HUMAN），会话不存在时返回 AI（按未接管处理）。
     */
    public String getConversationMode(String conversationId) {
        return chatHistoryRepository.findConversationById(conversationId)
                .map(c -> normalizeMode(c.getMode()))
                .orElse(MODE_AI);
    }

    /**
     * 判断会话是否存在（管理端操作前校验，避免对不存在会话误操作）。
     */
    public boolean conversationExists(String conversationId) {
        return chatHistoryRepository.findConversationById(conversationId).isPresent();
    }

    /**
     * 切换会话模式（转人工接管 / 结束接管）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void switchConversationMode(String conversationId, String mode) {
        chatHistoryRepository.updateMode(conversationId, normalizeMode(mode));
        logger.info("[CHAT_HISTORY] 切换会话模式 conversationId={}, mode={}", conversationId, mode);
    }

    /**
     * 保存一条人工客服消息（管理端工作台发出）。
     *
     * <p>复用 {@code chat_message}：{@code role=agent} + {@code agent_name=human:{adminUserId}}，
     * {@code extra} 打 takeover 标记——不新建表，C 端渲染无感知（同一 message 事件）。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public String saveHumanMessage(String conversationId, String adminUserId, String content) {
        ensureConversationExists(conversationId, null);
        String messageId = generateMessageId();
        String agentName = "human:" + adminUserId;
        String extraJson = JSON.toJSONString(Map.of("takeover", true, "adminUserId", adminUserId));
        ChatMessage message = new ChatMessage(messageId, conversationId, "agent", content, agentName, extraJson);
        chatHistoryRepository.saveMessage(message);
        logger.info("[CHAT_HISTORY] 保存人工消息 conversationId={}, admin={}, messageId={}",
                conversationId, adminUserId, messageId);
        return messageId;
    }

    private static String normalizeMode(String mode) {
        return MODE_HUMAN.equalsIgnoreCase(String.valueOf(mode)) ? MODE_HUMAN : MODE_AI;
    }

    /**
     * 查询某会话下的所有消息（需校验会话属于当前用户）。
     */
    public List<MessageView> listMessages(String conversationId, String userId) {
        ChatConversation conversation = chatHistoryRepository.findConversationById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!Objects.equals(conversation.getUserId(), userId)) {
            throw new IllegalArgumentException("无权访问该会话");
        }
        return chatHistoryRepository.findMessagesByConversationId(conversationId).stream()
                .map(this::toMessageView)
                .collect(Collectors.toList());
    }

    /**
     * 查询某会话下最近 N 条消息（按时间正序返回）。
     *
     * <p>用于问题改写智能体加载跨 Agent 的全量对话上下文。</p>
     *
     * @param conversationId 会话 ID
     * @param limit          最大条数
     * @return 按时间正序排列的消息实体列表
     */
    public List<ChatMessage> findRecentMessages(String conversationId, int limit) {
        try {
            return chatHistoryRepository.findRecentMessages(conversationId, limit);
        } catch (Exception e) {
            logger.warn("[CHAT_HISTORY] 查询最近消息失败 conversationId={}: {}", conversationId, e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 保存一条用户消息，同时创建/更新会话标题。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserMessage(String conversationId, String userId, String content) {
        ensureConversationExists(conversationId, userId);
        if (content != null && !content.isBlank()) {
            updateTitleFromFirstUserMessage(conversationId, content);
        }
        ChatMessage message = new ChatMessage(
                generateMessageId(), conversationId, "user", content, null, null);
        chatHistoryRepository.saveMessage(message);
        logger.debug("[CHAT_HISTORY] 保存用户消息 conversationId={}", conversationId);
    }

    /**
     * 保存一条 AI 回复消息。
     */
    @Transactional(rollbackFor = Exception.class)
    public String saveAssistantMessage(String conversationId, String userId, String content,
                                       String agentName, String extraJson) {
        ensureConversationExists(conversationId, userId);
        String messageId = generateMessageId();
        ChatMessage message = new ChatMessage(
                messageId, conversationId, "agent", content, agentName, extraJson);
        chatHistoryRepository.saveMessage(message);
        logger.debug("[CHAT_HISTORY] 保存AI消息 conversationId={}, agent={}, messageId={}",
                conversationId, agentName, messageId);
        return messageId;
    }

    /**
     * 保存一条系统消息（如错误提示、中断提示）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveSystemMessage(String conversationId, String userId, String content) {
        ensureConversationExists(conversationId, userId);
        ChatMessage message = new ChatMessage(
                generateMessageId(), conversationId, "system", content, null, null);
        chatHistoryRepository.saveMessage(message);
    }

    /**
     * 反馈状态：点赞 / 点踩。
     */
    public enum Feedback {
        /**
         * 点赞
         */
        LIKE,
        /**
         * 点踩
         */
        DISLIKE
    }

    /**
     * 更新某条消息的用户反馈。feedback 为 null 时表示清空反馈。
     *
     * @throws IllegalArgumentException 消息不存在或无权访问
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateFeedback(String messageId, String userId, String feedback) {
        ChatMessage message = chatHistoryRepository.findMessageById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("消息不存在"));
        ChatConversation conversation = chatHistoryRepository
                .findConversationById(message.getConversationId())
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!Objects.equals(conversation.getUserId(), userId)) {
            throw new IllegalArgumentException("无权访问该消息");
        }
        String normalized = normalizeFeedback(feedback);
        Instant feedbackAt = normalized == null ? null : Instant.now();
        int rows = chatHistoryRepository.updateFeedback(messageId, normalized, feedbackAt);
        if (rows == 0) {
            throw new IllegalArgumentException("消息不存在");
        }
        logger.info("[CHAT_HISTORY] 更新反馈 messageId={}, feedback={}", messageId, normalized);
    }

    private String normalizeFeedback(String feedback) {
        if (feedback == null) {
            return null;
        }
        String trimmed = feedback.trim();
        if (trimmed.isEmpty() || "null".equalsIgnoreCase(trimmed) || "CLEAR".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed.toUpperCase();
    }

    /**
     * 更新会话标题。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateTitle(String conversationId, String userId, String title) {
        ChatConversation conversation = chatHistoryRepository.findConversationById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!Objects.equals(conversation.getUserId(), userId)) {
            throw new IllegalArgumentException("无权访问该会话");
        }
        chatHistoryRepository.updateTitle(conversationId, title);
    }

    /**
     * 删除会话及其消息（逻辑删除）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteConversation(String conversationId, String userId) {
        ChatConversation conversation = chatHistoryRepository.findConversationById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("会话不存在"));
        if (!Objects.equals(conversation.getUserId(), userId)) {
            throw new IllegalArgumentException("无权访问该会话");
        }
        chatHistoryRepository.deleteMessagesByConversationId(conversationId);
        chatHistoryRepository.deleteConversation(conversationId);
        logger.info("[CHAT_HISTORY] 删除会话 conversationId={}, userId={}", conversationId, userId);
    }

    private void ensureConversationExists(String conversationId, String userId) {
        if (chatHistoryRepository.findConversationById(conversationId).isEmpty()) {
            ChatConversation conversation = new ChatConversation(conversationId, userId, DEFAULT_TITLE);
            chatHistoryRepository.saveConversation(conversation);
        }
    }

    private void updateTitleFromFirstUserMessage(String conversationId, String content) {
        ChatConversation conversation = chatHistoryRepository.findConversationById(conversationId)
                .orElse(null);
        if (conversation == null) {
            return;
        }
        if (DEFAULT_TITLE.equals(conversation.getTitle())) {
            String title = content.length() > TITLE_MAX_LENGTH ? content.substring(0, TITLE_MAX_LENGTH) : content;
            chatHistoryRepository.updateTitle(conversationId, title);
        }
    }

    private String generateMessageId() {
        return "msg_" + UUID.randomUUID().toString().replace("-", "");
    }

    private ConversationView toConversationView(ChatConversation conversation) {
        return new ConversationView(
                conversation.getConversationId(),
                conversation.getTitle(),
                normalizeMode(conversation.getMode()),
                toEpochMilli(conversation.getCreatedAt()),
                toEpochMilli(conversation.getUpdatedAt()));
    }

    private MessageView toMessageView(ChatMessage message) {
        Map<String, Object> extraMap = null;
        if (message.getExtra() != null && !message.getExtra().isBlank()) {
            try {
                extraMap = JSON.parseObject(message.getExtra(), new TypeReference<Map<String, Object>>() {
                });
            } catch (Exception e) {
                logger.warn("[CHAT_HISTORY] 解析消息 extra 失败 messageId={}: {}",
                        message.getMessageId(), e.getMessage());
            }
        }
        return new MessageView(
                message.getMessageId(),
                message.getRole(),
                message.getContent(),
                message.getAgentName(),
                toEpochMilli(message.getCreatedAt()),
                extraMap == null ? Collections.emptyMap() : extraMap,
                message.getFeedback(),
                toEpochMilli(message.getFeedbackAt()));
    }

    private long toEpochMilli(Instant instant) {
        return instant == null ? System.currentTimeMillis() : instant.toEpochMilli();
    }

    public record ConversationView(String id, String title, String mode,
                                   long createdAt, long updatedAt) {
    }

    /**
     * 管理端会话列表视图：附带最后一条消息预览与归属用户，供客服工作台展示。
     */
    public record AdminConversationView(String conversationId, String userId, String title,
                                        String mode, String preview, String previewRole,
                                        long updatedAt) {
    }

    public record MessageView(String id, String role, String content, String agentName,
                              long timestamp, Map<String, Object> extra,
                              String feedback, long feedbackAt) {
    }
}
