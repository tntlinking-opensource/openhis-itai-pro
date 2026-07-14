/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.infrastructure.repository;

import com.openhis.whale.identity.domain.UserOrganizationUnit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户组织单元关联Repository
 */
@Repository
public interface UserOrganizationUnitRepository extends JpaRepository<UserOrganizationUnit, Long>, JpaSpecificationExecutor<UserOrganizationUnit> {

    /**
     * 根据用户ID查找
     */
    List<UserOrganizationUnit> findByUserId(Long userId);

    /**
     * 根据组织单元ID查找
     */
    List<UserOrganizationUnit> findByOrganizationUnitId(Long organizationUnitId);

    /**
     * 查找用户是否在组织单元中
     */
    boolean existsByUserIdAndOrganizationUnitId(Long userId, Long organizationUnitId);

    /**
     * 删除用户的所有组织单元关联
     */
    void deleteByUserId(Long userId);

    /**
     * 删除组织单元的所有用户关联
     */
    void deleteByOrganizationUnitId(Long organizationUnitId);

    /**
     * 删除用户组织单元关联
     */
    void deleteByUserIdAndOrganizationUnitId(Long userId, Long organizationUnitId);

    /**
     * 根据用户ID和组织单元ID查找
     */
    Optional<UserOrganizationUnit> findByUserIdAndOrganizationUnitId(Long userId, Long organizationUnitId);

    /**
     * 根据用户ID和租户ID查找用户关联的组织单元
     */
    List<UserOrganizationUnit> findByUserIdAndTenantId(Long userId, Long tenantId);
}
