/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.gfchargeratioitem.repository;

import com.zxplat.plugin.campus.domain.gfchargeratioitem.entity.GfChargeRatioItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;

public interface GfChargeRatioItemRepository extends JpaRepository<GfChargeRatioItem, Long>, JpaSpecificationExecutor<GfChargeRatioItem> {

    Optional<GfChargeRatioItem> findByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);

    Optional<GfChargeRatioItem> findByTenantIdAndItemCodeAndChargeModeEnumAndIsDeletedFalse(
        Long tenantId, String itemCode, String chargeModeEnum);
}
