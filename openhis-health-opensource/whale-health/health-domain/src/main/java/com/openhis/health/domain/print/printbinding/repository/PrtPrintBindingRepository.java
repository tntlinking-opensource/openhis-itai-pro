/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.print.printbinding.repository;

import com.openhis.health.domain.print.printbinding.entity.PrtPrintBinding;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 业务场景模板绑定仓储（PrtPrintBinding 聚合）。
 */
public interface PrtPrintBindingRepository extends JpaRepository<PrtPrintBinding, Long> {

    /**
     * 按租户+bindingCode+机构查唯一有效绑定行。
     *
     * @param tenantId       租户
     * @param bindingCode    场景码
     * @param organizationId 机构（租户级为 null）
     * @return 绑定行或 null
     */
    PrtPrintBinding findByTenantIdAndBindingCodeAndOrganizationIdAndIsDeletedFalse(
        Long tenantId, String bindingCode, Long organizationId);

    /**
     * 列出租户下全部未删绑定行。
     *
     * @param tenantId 租户
     * @return 绑定行列表
     */
    List<PrtPrintBinding> findByTenantIdAndIsDeletedFalse(Long tenantId);

    /**
     * 列出租户级 override 绑定（organization_id 为 NULL）。
     *
     * @param tenantId 租户
     * @return 绑定行列表
     */
    List<PrtPrintBinding> findByTenantIdAndOrganizationIdIsNullAndIsDeletedFalse(Long tenantId);
}
