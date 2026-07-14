/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.multitenancy;

import java.util.List;

/**
 * 平台租户访问策略：平台 admin 可跨租户；普通用户仅当前会话租户。
 */
public interface PlatformTenantAccessPolicy {

    /**
     * 是否平台管理员（如绑定 {@code admin} 角色）。
     */
    boolean isPlatformAdmin(Long userId);

    /**
     * 非平台 admin 时可见的租户 id；平台 admin 返回 {@code null} 表示不限制。
     */
    List<Long> resolveVisibleTenantIds(Long userId);

    /**
     * 校验是否允许对目标租户执行管理操作（查询用户、分配用户等）。
     */
    void assertCanManageTenant(Long userId, Long targetTenantId);

    /**
     * 校验是否允许创建/删除/全局维护租户定义。
     */
    void assertCanMaintainTenantCatalog(Long userId);
}
