/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.gffreepercent.repository;

import com.openhis.campus.domain.gffreepercent.entity.GfFreePercent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GfFreePercentRepository extends JpaRepository<GfFreePercent, Long> {

    List<GfFreePercent> findByTenantIdAndChargeModeEnumAndIsDeletedFalseOrderByClassEnumAsc(
        Long tenantId, String chargeModeEnum);

    Optional<GfFreePercent> findByTenantIdAndClassEnumAndChargeModeEnumAndIsDeletedFalse(
        Long tenantId, String classEnum, String chargeModeEnum);
}
