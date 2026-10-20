package com.ecom.cs.business.audit.service;

import com.alibaba.fastjson2.JSON;
import com.ecom.cs.business.audit.entity.AdminAuditLog;
import com.ecom.cs.business.audit.mapper.AdminAuditLogMapper;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 管理端操作审计服务。
 *
 * <p>所有审计写入均为「尽力而为」：异步执行且内部吞掉异常——
 * 审计失败不能影响管理端主业务流程（接管会话、审批决策等）。</p>
 *
 * @author Hollis
 */
@Service
public class AdminAuditService {

    private static final Logger logger = LoggerFactory.getLogger(AdminAuditService.class);

    /** 目标类型：会话 */
    public static final String TARGET_CONVERSATION = "conversation";
    /** 目标类型：审批单 */
    public static final String TARGET_APPROVAL = "approval";

    @Autowired
    private AdminAuditLogMapper auditLogMapper;

    /**
     * 记录一条管理端操作审计日志（异步，失败仅记日志不影响主流程）。
     *
     * @param adminUserId 操作人（sa-token loginId）
     * @param action      动作标识，如 takeover / release / human_message / approval_decide
     * @param targetType  对象类型，见 {@link #TARGET_CONVERSATION} / {@link #TARGET_APPROVAL}
     * @param targetId    对象 ID（会话 ID / 审批单 ID）
     * @param detail      操作详情，为 null 时记空对象
     */
    @Async
    public void record(String adminUserId, String action, String targetType,
                       String targetId, Map<String, Object> detail) {
        try {
            AdminAuditLog log = new AdminAuditLog();
            log.setAdminUserId(adminUserId);
            log.setAction(action);
            log.setTargetType(targetType);
            log.setTargetId(targetId);
            Map<String, Object> safeDetail = detail == null ? new LinkedHashMap<>() : detail;
            log.setDetail(JSON.toJSONString(safeDetail));
            auditLogMapper.insert(log);
            logger.info("[AUDIT] admin={}, action={}, target={}/{}, detail={}",
                    adminUserId, action, targetType, targetId, safeDetail);
        } catch (Exception e) {
            // 审计失败绝不向上抛：管理端主流程（接管/审批）不能因审计写入失败而回滚
            logger.warn("[AUDIT] 审计写入失败 admin={}, action={}, target={}/{}: {}",
                    adminUserId, action, targetType, targetId, e.getMessage());
        }
    }
}
