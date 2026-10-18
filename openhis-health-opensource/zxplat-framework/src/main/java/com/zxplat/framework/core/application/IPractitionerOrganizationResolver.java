/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application;

import com.zxplat.framework.core.application.dto.PractitionerOrganizationIds;

import java.util.Optional;

/**
 * 由业务模块（如 health）提供：根据平台用户解析医务人员与机构主键，供登录与应用配置展示。
 */
public interface IPractitionerOrganizationResolver {

    /**
     * @param userId   {@code zxplat_users.id}
     * @param tenantId 当前租户（登录流程中可能已解析；解析器可按需使用，可为 null）
     */
    Optional<PractitionerOrganizationIds> resolve(Long userId, Long tenantId);
}
