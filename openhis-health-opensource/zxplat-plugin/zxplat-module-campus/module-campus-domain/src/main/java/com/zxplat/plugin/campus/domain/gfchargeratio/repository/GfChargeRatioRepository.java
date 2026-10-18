/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.gfchargeratio.repository;

import com.zxplat.plugin.campus.domain.gfchargeratio.entity.GfChargeRatio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface GfChargeRatioRepository extends JpaRepository<GfChargeRatio, Long>, JpaSpecificationExecutor<GfChargeRatio> {

    Optional<GfChargeRatio> findByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);

    Optional<GfChargeRatio> findByTenantIdAndCatalogCodeAndChargeModeEnumAndIsDeletedFalse(
        Long tenantId, String catalogCode, String chargeModeEnum);
}
