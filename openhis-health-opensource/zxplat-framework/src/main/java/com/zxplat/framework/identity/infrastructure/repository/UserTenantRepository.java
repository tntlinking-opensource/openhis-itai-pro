/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.infrastructure.repository;

import com.zxplat.framework.identity.domain.UserTenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户租户关联Repository
 */
@Repository
public interface UserTenantRepository extends JpaRepository<UserTenant, Long> {

    /**
     * 根据用户ID查找所有租户关联
     */
    List<UserTenant> findByUserId(Long userId);

    /**
     * 根据租户ID查找所有用户关联
     */
    List<UserTenant> findByTenantId(Long tenantId);

    /**
     * 检查用户是否关联到指定租户
     */
    boolean existsByUserIdAndTenantId(Long userId, Long tenantId);

    /**
     * 根据用户ID和租户ID查找关联
     */
    UserTenant findByUserIdAndTenantId(Long userId, Long tenantId);

    /**
     * 删除用户的所有租户关联
     */
    void deleteByUserId(Long userId);

    /**
     * 删除指定用户的指定租户关联
     */
    void deleteByUserIdAndTenantId(Long userId, Long tenantId);
}
