package com.ecom.cs.controller;

import cn.dev33.satoken.annotation.SaCheckRole;
import cn.dev33.satoken.stp.StpUtil;
import com.ecom.cs.agent.registry.SseEmitterRegistry;
import com.ecom.cs.agent.service.ChatSseNotifier;
import com.ecom.cs.business.audit.service.AdminAuditService;
import com.ecom.cs.business.chat.service.ChatHistoryService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 管理端会话接管接口（人工客服工作台）。
 *
 * <p>全部接口要求 {@code role = ADMIN}：普通用户（test 等）即使携带有效 Token
 * 也会被 sa-token 角色校验拦截（403），由 {@code GlobalExceptionHandler}
 * 的 {@code NotPermissionException} 处理器统一返回 JSON。</p>
 *
 * <p>消息投递策略（演示项目）：管理端发出的消息先落库，再尝试通过
 * {@link SseEmitterRegistry} 推送给在线的 C 端 SSE 连接；C 端不在线时
 * 由前端轮询兜底（ HUMAN 模式下 3s 一轮）。</p>
 *
 * @author Hollis
 */
@RestController
@RequestMapping("/api/admin/conversations")
@CrossOrigin(origins = "*")
public class AdminChatController {

    private static final Logger logger = LoggerFactory.getLogger(AdminChatController.class);

    /** 接管/释放时下发给 C 端的系统提示文案 */
    private static final String TAKEOVER_NOTICE = "已为您转接人工客服，请稍候，客服正在赶来～";
    private static final String RELEASE_NOTICE = "人工客服已结束服务，接下来由智能助手继续为您服务。";

    @Autowired
    private ChatHistoryService chatHistoryService;
    @Autowired
    private SseEmitterRegistry sseEmitterRegistry;
    @Autowired
    private ChatSseNotifier chatSseNotifier;
    @Autowired
    private AdminAuditService adminAuditService;

    /**
     * 查询全部会话（客服工作台列表，按最后更新时间倒序）。
     */
    @GetMapping
    @SaCheckRole("ADMIN")
    public java.util.List<ChatHistoryService.AdminConversationView> listConversations() {
        return chatHistoryService.listAllConversationsForAdmin();
    }

    /**
     * 回放指定会话的全部消息（客服查看上下文，无用户归属校验）。
     */
    @GetMapping("/{conversationId}/messages")
    @SaCheckRole("ADMIN")
    public java.util.List<ChatHistoryService.MessageView> listMessages(@PathVariable String conversationId) {
        return chatHistoryService.listMessagesForAdmin(conversationId);
    }

    /**
     * 接管会话：切换为 HUMAN 模式，AI 智能体入口随即停止响应。
     */
    @PostMapping("/{conversationId}/takeover")
    @SaCheckRole("ADMIN")
    public ResponseEntity<Map<String, Object>> takeover(@PathVariable String conversationId) {
        requireConversationExists(conversationId);
        String adminUserId = StpUtil.getLoginIdAsString();
        chatHistoryService.switchConversationMode(conversationId, ChatHistoryService.MODE_HUMAN);
        chatHistoryService.saveSystemMessage(conversationId, null, TAKEOVER_NOTICE);
        pushToCustomer(conversationId, TAKEOVER_NOTICE);
        adminAuditService.record(adminUserId, "takeover",
                AdminAuditService.TARGET_CONVERSATION, conversationId, Map.of());
        logger.info("[ADMIN_CHAT] 接管会话 conversationId={}, admin={}", conversationId, adminUserId);
        return ResponseEntity.ok(Map.of("mode", ChatHistoryService.MODE_HUMAN, "conversationId", conversationId));
    }

    /**
     * 结束接管：切换回 AI 模式，智能助手恢复响应。
     */
    @PostMapping("/{conversationId}/release")
    @SaCheckRole("ADMIN")
    public ResponseEntity<Map<String, Object>> release(@PathVariable String conversationId) {
        requireConversationExists(conversationId);
        String adminUserId = StpUtil.getLoginIdAsString();
        chatHistoryService.switchConversationMode(conversationId, ChatHistoryService.MODE_AI);
        chatHistoryService.saveSystemMessage(conversationId, null, RELEASE_NOTICE);
        pushToCustomer(conversationId, RELEASE_NOTICE);
        adminAuditService.record(adminUserId, "release",
                AdminAuditService.TARGET_CONVERSATION, conversationId, Map.of());
        logger.info("[ADMIN_CHAT] 结束接管 conversationId={}, admin={}", conversationId, adminUserId);
        return ResponseEntity.ok(Map.of("mode", ChatHistoryService.MODE_AI, "conversationId", conversationId));
    }

    /**
     * 人工客服发言：消息落库（{@code agent_name=human:{adminUserId}}）并尝试推送 C 端。
     */
    @PostMapping("/{conversationId}/human-message")
    @SaCheckRole("ADMIN")
    public ResponseEntity<Map<String, Object>> sendHumanMessage(
            @PathVariable String conversationId, @RequestBody HumanMessageRequest request) {
        requireConversationExists(conversationId);
        String adminUserId = StpUtil.getLoginIdAsString();
        String content = request == null || request.content() == null ? "" : request.content().trim();
        if (content.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "消息内容不能为空"));
        }
        String messageId = chatHistoryService.saveHumanMessage(conversationId, adminUserId, content);
        pushToCustomer(conversationId, content);
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("messageId", messageId);
        detail.put("contentLength", content.length());
        adminAuditService.record(adminUserId, "human_message",
                AdminAuditService.TARGET_CONVERSATION, conversationId, detail);
        logger.info("[ADMIN_CHAT] 人工发言 conversationId={}, admin={}, messageId={}",
                conversationId, adminUserId, messageId);
        return ResponseEntity.ok(Map.of("messageId", messageId));
    }

    /**
     * 把文本推送到 C 端在线 SSE 连接（不在线则静默跳过，靠前端轮询兜底）。
     */
    private void pushToCustomer(String conversationId, String text) {
        SseEmitter emitter = sseEmitterRegistry.get(conversationId);
        if (emitter == null) {
            logger.debug("[ADMIN_CHAT] C 端无在线 SSE 连接，靠轮询兜底 conversationId={}", conversationId);
            return;
        }
        chatSseNotifier.sendMessage(emitter, text);
    }

    /**
     * 校验会话存在，不存在则抛 404（由调用方转为 404 JSON）。
     */
    private void requireConversationExists(String conversationId) {
        if (!chatHistoryService.conversationExists(conversationId)) {
            throw new java.util.NoSuchElementException("会话不存在: " + conversationId);
        }
    }

    private record HumanMessageRequest(String content) {
    }
}
