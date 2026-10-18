/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.campuscollege.repository;

import com.zxplat.plugin.campus.domain.campuscollege.entity.CampusCollege;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CampusCollegeRepository
    extends JpaRepository<CampusCollege, Long>, JpaSpecificationExecutor<CampusCollege> {

    Optional<CampusCollege> findByTenantIdAndCollegeCodeAndIsDeletedFalse(Long tenantId, String collegeCode);

    Optional<CampusCollege> findByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);

    List<CampusCollege> findByTenantIdAndActiveFlagTrueAndIsDeletedFalseOrderBySortOrderAscIdAsc(Long tenantId);

    List<CampusCollege> findByTenantIdAndActiveFlagTrueAndCampusSiteIdIsNullAndIsDeletedFalseOrderBySortOrderAscIdAsc(
        Long tenantId);

    List<CampusCollege> findByTenantIdAndActiveFlagTrueAndCampusSiteIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(
        Long tenantId, Long campusSiteId);
}
