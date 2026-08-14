/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.platform.authorization;

import com.openhis.health.domain.share.authorization.MrmsAdminAuthorizer;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.health.domain.share.constants.WhalePlatformRoleCodeConstants;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import com.openhis.whale.identity.application.UserAppService;
import com.openhis.whale.identity.application.dto.RoleDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 病案管理员鉴权：仅当登录用户在 {@code whale_user_roles} 中持有
 * {@link WhalePlatformRoleCodeConstants#ROLE_MRMS_ADMIN} 时放行归档/解档病例。
 */
@Component
@RequiredArgsConstructor
public class MrmsAdminRoleGate implements MrmsAdminAuthorizer {

    /** 当前登录会话。 */
    private final ICurrentUser currentUser;

    /** 用户角色读侧。 */
    private final UserAppService userAppService;

    /**
     * 当前登录用户是否具备病案管理员角色。
     *
     * @return 持有 {@code ROLE_MRMS_ADMIN} 时为 {@code true}
     */
    @Override
    public boolean hasMrmsAdminRole() {
        if (!this.currentUser.isAuthenticated()) {
            return false;
        }
        Long userId = this.currentUser.getCurrentUserId();
        if (userId == null) {
            return false;
        }
        return this.hasMrmsAdminUserRole(userId);
    }

    /**
     * 断言当前用户为病案管理员。
     *
     * @throws BusinessException 无权限时
     */
    @Override
    public void assertMrmsAdminRole() {
        if (this.hasMrmsAdminRole()) {
            return;
        }
        throw new BusinessException(MrmsErrorCodes.CLINICAL_ARCHIVE_ROLE_REQUIRED, "仅病案管理员可归档或解档病例");
    }

    /**
     * 用户是否拥有病案管理员平台角色。
     *
     * @param userId 用户主键
     * @return true 表示有权限
     */
    private boolean hasMrmsAdminUserRole(Long userId) {
        List<RoleDto> roles = this.userAppService.getRoles(userId);
        for (RoleDto role : roles) {
            if (role == null) {
                continue;
            }
            // 优先按平台 code 判定
            if (role.getCode() != null
                    && WhalePlatformRoleCodeConstants.ROLE_MRMS_ADMIN.equalsIgnoreCase(role.getCode().trim())) {
                return true;
            }
            // 兼容：登录配置 roles 用 whale_roles.name（mrms_admin）回显/绑定时的判定
            if (role.getName() != null && "mrms_admin".equalsIgnoreCase(role.getName().trim())) {
                return true;
            }
        }
        return false;
    }
}
