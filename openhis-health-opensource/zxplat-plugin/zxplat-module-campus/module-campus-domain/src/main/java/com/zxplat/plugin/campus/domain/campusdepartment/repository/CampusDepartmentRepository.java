/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.domain.campusdepartment.repository;

import com.zxplat.plugin.campus.domain.campusdepartment.entity.CampusDepartment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CampusDepartmentRepository
    extends JpaRepository<CampusDepartment, Long>, JpaSpecificationExecutor<CampusDepartment> {

    Optional<CampusDepartment> findByTenantIdAndDepartmentCodeAndIsDeletedFalse(Long tenantId, String departmentCode);

    Optional<CampusDepartment> findByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);

    List<CampusDepartment> findByTenantIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(Long tenantId);

    List<CampusDepartment> findByTenantIdAndActiveFlagTrueAndIsDeletedFalseOrderBySortOrderAscIdAsc(Long tenantId);

    List<CampusDepartment> findByTenantIdAndCampusSiteIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(
        Long tenantId, Long campusSiteId);

    List<CampusDepartment> findByTenantIdAndCollegeIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(
        Long tenantId, Long collegeId);

    List<CampusDepartment> findByTenantIdAndActiveFlagTrueAndCampusSiteIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(
        Long tenantId, Long campusSiteId);

    List<CampusDepartment> findByTenantIdAndActiveFlagTrueAndCollegeIdAndIsDeletedFalseOrderBySortOrderAscIdAsc(
        Long tenantId, Long collegeId);
}
