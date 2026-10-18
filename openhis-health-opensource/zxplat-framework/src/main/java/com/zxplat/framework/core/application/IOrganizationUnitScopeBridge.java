/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/**
 * HIS {@code adm_organization} 与 ZXPlat {@code OrganizationUnit} 的 id 桥接。
 *
 * <p>两侧主键独立；关联键为 OU {@code code}（通常等于组织 busNo）。业务会话与数据收窄使用 HIS 组织 id，
 * identity 用户-科室关联表使用 ZXPlat OU id。</p>
 */
public interface IOrganizationUnitScopeBridge {

    /**
     * @param businessOrganizationId {@code adm_organization.id}
     * @return 已同步的 ZXPlat OU 主键；未同步时为空
     */
    Optional<Long> findZxplatOrganizationUnitIdByBusinessOrganizationId(Long businessOrganizationId);

    /**
     * @param zxplatOrganizationUnitId {@code zxplat_organization_units.id}
     * @return 对应 HIS 组织主键；无法解析时为空
     */
    Optional<Long> findBusinessOrganizationIdByZxplatOrganizationUnitId(Long zxplatOrganizationUnitId);

    /**
     * 由已加载的 ZXPlat OU {@code code} 反查 HIS 组织主键（登录科室列表批量装配，避免再按 OU id 查库）。
     *
     * @param organizationUnitCode ZXPlat OU code；空白时 empty
     * @return {@code adm_organization.id}；无法解析时 empty
     */
    Optional<Long> findBusinessOrganizationIdByOrganizationUnitCode(String organizationUnitCode);

    /**
     * 批量：OU code → HIS 组织主键（{@code org-} 前缀内存解析 + busNo 一次批量查）。
     *
     * @param organizationUnitCodes OU code 集合；{@code null}/空返回空映射
     * @return code → {@code adm_organization.id}
     */
    Map<String, Long> mapBusinessOrganizationIdsByOrganizationUnitCodes(Collection<String> organizationUnitCodes);
}
