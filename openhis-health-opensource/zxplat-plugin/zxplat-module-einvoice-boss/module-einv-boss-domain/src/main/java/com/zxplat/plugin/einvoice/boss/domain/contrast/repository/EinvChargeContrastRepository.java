/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.contrast.repository;

import com.zxplat.plugin.einvoice.boss.domain.contrast.entity.EinvChargeContrast;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

/**
 * 院内收费项与博思收费项对照仓储（{@code einv_charge_contrast}）。
 *
 * <p>开票明细组装时按租户+院内编码解析博思 chargeCode/chargeName；
 * 维护接口用 {@link #findByTenantIdAndInternalCodeAndIsDeletedFalseAndIdNot} 做唯一性校验。</p>
 */
public interface EinvChargeContrastRepository extends JpaRepository<EinvChargeContrast, Long>, JpaSpecificationExecutor<EinvChargeContrast> {

    /**
     * 按租户与院内编码查询有效对照。
     *
     * @param tenantId 租户主键
     * @param internalCode 院内内部编码
     * @return 对照（若存在）
     */
    Optional<EinvChargeContrast> findByTenantIdAndInternalCodeAndIsDeletedFalse(Long tenantId, String internalCode);

    /**
     * 按租户与院内编码查询有效对照（排除指定主键，用于更新唯一性校验）。
     *
     * @param tenantId 租户主键
     * @param internalCode 院内内部编码
     * @param id 排除的主键
     * @return 对照（若存在）
     */
    Optional<EinvChargeContrast> findByTenantIdAndInternalCodeAndIsDeletedFalseAndIdNot(
            Long tenantId, String internalCode, Long id);
}
