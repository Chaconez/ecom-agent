package com.ecom.cs.business.audit.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ecom.cs.business.audit.entity.AdminAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理端操作审计日志 Mapper。
 *
 * @author Hollis
 */
@Mapper
public interface AdminAuditLogMapper extends BaseMapper<AdminAuditLog> {
}
