/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.domain.commonresult.repository;

import com.zxplat.plugin.peis.domain.commonresult.entity.PeisCommonResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

/**
 * peis_common_result 仓储。
 */
public interface PeisCommonResultRepository
    extends JpaRepository<PeisCommonResult, Long>, JpaSpecificationExecutor<PeisCommonResult> {

    List<PeisCommonResult> findByObservationDefinitionIdAndStatusEnumAndIsDeletedFalse(
        Long observationDefinitionId, String statusEnum);

    boolean existsByTenantIdAndObservationDefinitionIdAndDeptOrgIdAndDisplayAndStatusEnumAndIsDeletedFalse(
        Long tenantId, Long observationDefinitionId, Long deptOrgId, String display, String statusEnum);

    Optional<PeisCommonResult> findByIdAndIsDeletedFalse(Long id);
}
