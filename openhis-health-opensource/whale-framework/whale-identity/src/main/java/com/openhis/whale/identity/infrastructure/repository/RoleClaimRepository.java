/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.infrastructure.repository;

import com.openhis.whale.identity.domain.RoleClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 角色声明Repository
 */
@Repository
public interface RoleClaimRepository extends JpaRepository<RoleClaim, Long>, JpaSpecificationExecutor<RoleClaim> {

    /**
     * 根据角色ID查找
     */
    List<RoleClaim> findByRoleId(Long roleId);

    /**
     * 根据角色ID和声明类型查找
     */
    List<RoleClaim> findByRoleIdAndClaimType(Long roleId, String claimType);

    /**
     * 根据角色ID、声明类型和声明值查找
     */
    Optional<RoleClaim> findByRoleIdAndClaimTypeAndClaimValue(Long roleId, String claimType, String claimValue);

    /**
     * 删除角色的所有声明
     */
    void deleteByRoleId(Long roleId);

    /**
     * 删除角色的指定类型声明
     */
    void deleteByRoleIdAndClaimType(Long roleId, String claimType);

    /**
     * 删除角色声明
     */
    void deleteByRoleIdAndClaimTypeAndClaimValue(Long roleId, String claimType, String claimValue);
}
