/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.infrastructure.repository;

import com.openhis.whale.core.infrastructure.transaction.SkipSoftDeleteFilter;
import com.openhis.whale.identity.domain.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 用户角色关联Repository
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

    /**
     * 根据用户ID查找所有角色关联
     */
    List<UserRole> findByUserId(Long userId);

    /**
     * 根据角色ID查找所有用户关联
     */
    List<UserRole> findByRoleId(Long roleId);

    /**
     * 删除用户的所有角色关联
     */
    void deleteByUserId(Long userId);

    /**
     * 删除角色的所有用户关联
     */
    void deleteByRoleId(Long roleId);

    /**
     * 判断用户是否拥有指定角色
     */
    boolean existsByUserIdAndRoleId(Long userId, Long roleId);

    /**
     * 按用户查询全部角色关联（含已软删），用于避免 UNIQUE(user_id, role_id) 与软删冲突。
     */
    @SkipSoftDeleteFilter
    List<UserRole> findAllByUserId(Long userId);

    /**
     * 按用户与角色查询（含已软删）。
     */
    @SkipSoftDeleteFilter
    Optional<UserRole> findByUserIdAndRoleId(Long userId, Long roleId);
}
