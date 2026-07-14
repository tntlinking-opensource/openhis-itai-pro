/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application;

import java.util.Optional;

/**
 * HIS {@code adm_organization} 与 Whale {@code OrganizationUnit} 的 id 桥接。
 *
 * <p>两侧主键独立；关联键为 OU {@code code}（通常等于组织 busNo）。业务会话与数据收窄使用 HIS 组织 id，
 * identity 用户-科室关联表使用 Whale OU id。</p>
 */
public interface IOrganizationUnitScopeBridge {

    /**
     * @param businessOrganizationId {@code adm_organization.id}
     * @return 已同步的 Whale OU 主键；未同步时为空
     */
    Optional<Long> findWhaleOrganizationUnitIdByBusinessOrganizationId(Long businessOrganizationId);

    /**
     * @param whaleOrganizationUnitId {@code whale_organization_units.id}
     * @return 对应 HIS 组织主键；无法解析时为空
     */
    Optional<Long> findBusinessOrganizationIdByWhaleOrganizationUnitId(Long whaleOrganizationUnitId);
}
