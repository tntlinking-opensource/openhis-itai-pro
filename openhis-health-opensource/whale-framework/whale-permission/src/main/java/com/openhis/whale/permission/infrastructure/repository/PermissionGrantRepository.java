/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.permission.infrastructure.repository;

import com.openhis.whale.core.infrastructure.transaction.SkipSoftDeleteFilter;
import com.openhis.whale.permission.domain.PermissionGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * 权限授予Repository
 */
@Repository
public interface PermissionGrantRepository extends JpaRepository<PermissionGrant, Long> {

    /**
     * 根据权限名称查找所有授予记录
     */
    List<PermissionGrant> findByPermissionName(String permissionName);

    /**
     * 根据提供者查找所有授予记录
     */
    List<PermissionGrant> findByProviderNameAndProviderKey(String providerName, String providerKey);

    /**
     * 批量按提供者键查找授予记录（用于合并角色授权，避免逐角色查询）
     */
    List<PermissionGrant> findByProviderNameAndProviderKeyIn(String providerName, Collection<String> providerKeys);

    /**
     * 根据权限名称、提供者名称和提供者键查找授予记录
     */
    List<PermissionGrant> findByPermissionNameAndProviderNameAndProviderKey(
            String permissionName, String providerName, String providerKey);

    /**
     * 按三元组查询全部授予记录（含已软删），避免 UNIQUE(permission_name, provider_name, provider_key) 与软删冲突。
     */
    @SkipSoftDeleteFilter
    List<PermissionGrant> findAllByPermissionNameAndProviderNameAndProviderKey(
            String permissionName, String providerName, String providerKey);

    /**
     * 判断权限是否已授予
     */
    boolean existsByPermissionNameAndProviderNameAndProviderKey(
            String permissionName, String providerName, String providerKey);

    /**
     * 删除权限授予
     */
    void deleteByPermissionNameAndProviderNameAndProviderKey(
            String permissionName, String providerName, String providerKey);
}
