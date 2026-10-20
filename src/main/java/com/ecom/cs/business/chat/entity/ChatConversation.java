package com.ecom.cs.business.chat.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.ecom.cs.config.InstantTypeHandler;
import java.time.Instant;

/**
 * 对话会话实体，对应 chat_conversation 表。
 *
 * @author Hollis
 */
@TableName(value = "chat_conversation", autoResultMap = true)
public class ChatConversation {

    @TableId(value = "conversation_id", type = IdType.INPUT)
    private String conversationId;

    private String userId;

    private String title;

    /** 会话模式：AI 智能助手 / HUMAN 人工接管（转人工后由管理端客服应答）。 */
    private String mode;

    @TableField(fill = FieldFill.INSERT, typeHandler = InstantTypeHandler.class)
    private Instant createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE, typeHandler = InstantTypeHandler.class)
    private Instant updatedAt;

    @TableLogic
    @TableField(value = "deleted")
    private Integer deleted;

    public ChatConversation() {
    }

    public ChatConversation(String conversationId, String userId, String title) {
        this.conversationId = conversationId;
        this.userId = userId;
        this.title = title;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }
}
