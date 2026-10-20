package com.ecom.cs.config;

import cn.dev33.satoken.stp.StpInterface;
import com.ecom.cs.business.auth.entity.UserAccount;
import com.ecom.cs.business.auth.mapper.UserAccountMapper;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Sa-Token 权限认证接口实现：为 {@code @SaCheckRole} / {@code StpUtil.checkRole} 提供角色数据。
 *
 * <p>登录会话的 loginId 是 {@code user_account.user_id}（如 u_admin / u_test），
 * 因此按 userId 反查账号拿到 role（ADMIN / USER）。</p>
 *
 * <p>账号不存在或查询异常时返回空列表——sa-token 对空角色列表的
 * {@code @SaCheckRole} 校验会直接失败（NotRoleException → 403），
 * 符合「查不到账号就不放行」的保守语义。</p>
 *
 * @author Hollis
 */
@Component
public class SaTokenStpInterfaceImpl implements StpInterface {

    private static final Logger logger = LoggerFactory.getLogger(SaTokenStpInterfaceImpl.class);

    /** 管理员角色标识（与 user_account.role 列保持一致） */
    public static final String ROLE_ADMIN = "ADMIN";
    /** 普通用户角色标识 */
    public static final String ROLE_USER = "USER";

    @Autowired
    private UserAccountMapper userAccountMapper;

    /**
     * 返回指定登录账号的角色列表。
     *
     * <p>sa-token 约定：查询异常时应抛出异常（中断鉴权链），返回空列表表示
     * 「确实没有任何角色」——这里账号不存在按后者处理，DB 异常向上抛。</p>
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        String userId = String.valueOf(loginId);
        UserAccount account = userAccountMapper.findByUserId(userId);
        if (account == null) {
            logger.warn("[SA_TOKEN] loginId={} 查不到账号，角色列表为空", userId);
            return new ArrayList<>();
        }
        String role = account.getRole();
        List<String> roles = new ArrayList<>();
        if (role != null && !role.isBlank()) {
            roles.add(role.trim().toUpperCase());
        }
        return roles;
    }

    /**
     * 返回指定登录账号的权限列表。
     *
     * <p>当前演示项目按角色粗粒度管控（ADMIN 即全部管理权限），
     * 不细化到按钮级 permission，故统一返回空列表。</p>
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return new ArrayList<>();
    }
}
