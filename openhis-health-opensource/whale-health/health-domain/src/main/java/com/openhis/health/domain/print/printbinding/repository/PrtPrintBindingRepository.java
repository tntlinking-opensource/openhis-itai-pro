/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
