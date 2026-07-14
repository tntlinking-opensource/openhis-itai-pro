/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.infrastructure.repository;

import com.openhis.whale.identity.domain.OrganizationUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 组织单元 Repository（仅派生查询，禁止 {@code @Query}）。
 */
@Repository
public interface OrganizationUnitRepository extends JpaRepository<OrganizationUnit, Long>,
    JpaSpecificationExecutor<OrganizationUnit> {

    Optional<OrganizationUnit> findByCode(String code);

    List<OrganizationUnit> findByParentIdOrderBySortOrderAsc(Long parentId);

    List<OrganizationUnit> findByParentIdIsNullOrderBySortOrderAsc();

    boolean existsByCode(String code);
}
