/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.infrastructure.repository;

import com.zxplat.framework.core.infrastructure.transaction.SkipSoftDeleteFilter;
import com.zxplat.framework.identity.domain.RoleClaim;
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
     * 按角色与声明类型查询全部行（含已软删），避免 UNIQUE(role_id, claim_type, claim_value) 与软删冲突。
     */
    @SkipSoftDeleteFilter
    List<RoleClaim> findAllByRoleIdAndClaimType(Long roleId, String claimType);

    /**
     * 根据角色ID、声明类型和声明值查找
     */
    Optional<RoleClaim> findByRoleIdAndClaimTypeAndClaimValue(Long roleId, String claimType, String claimValue);

    /**
     * 按角色、类型、值查询（含已软删），供恢复避免 uk_zxplat_rc_role_type_value 重复插入。
     */
    @SkipSoftDeleteFilter
    Optional<RoleClaim> findFirstByRoleIdAndClaimTypeAndClaimValue(Long roleId, String claimType, String claimValue);

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
