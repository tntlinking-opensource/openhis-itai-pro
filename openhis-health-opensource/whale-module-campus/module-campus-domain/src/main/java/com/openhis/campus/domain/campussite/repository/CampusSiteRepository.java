/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.domain.campussite.repository;

import com.openhis.campus.domain.campussite.entity.CampusSite;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface CampusSiteRepository
    extends JpaRepository<CampusSite, Long>, JpaSpecificationExecutor<CampusSite> {

    Optional<CampusSite> findByTenantIdAndCampusCodeAndIsDeletedFalse(Long tenantId, String campusCode);

    Optional<CampusSite> findByIdAndTenantIdAndIsDeletedFalse(Long id, Long tenantId);

    List<CampusSite> findByTenantIdAndActiveFlagTrueAndIsDeletedFalseOrderBySortOrderAscIdAsc(Long tenantId);

    List<CampusSite> findByTenantIdAndIsPrimaryTrueAndIsDeletedFalse(Long tenantId);
}
