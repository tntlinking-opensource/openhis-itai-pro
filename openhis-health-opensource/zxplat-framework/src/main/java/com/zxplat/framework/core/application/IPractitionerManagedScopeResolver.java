/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application;

import com.zxplat.framework.core.application.dto.PractitionerManagedScope;

import java.util.Optional;

/**
 * 由业务模块（health）提供：按平台用户/医务人员解析 {@code adm_pr_assign} 派生的数据管辖范围。
 */
public interface IPractitionerManagedScopeResolver {

    /**
     * @param userId          {@code zxplat_users.id}
     * @param practitionerId  {@code adm_practitioner.id}；可为 null
     * @param tenantId        当前租户；可为 null
     */
    Optional<PractitionerManagedScope> resolve(Long userId, Long practitionerId, Long tenantId);
}
