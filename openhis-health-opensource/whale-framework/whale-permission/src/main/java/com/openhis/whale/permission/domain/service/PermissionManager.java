/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.permission.domain.service;

import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.permission.domain.PermissionGrant;
import com.openhis.whale.permission.infrastructure.repository.PermissionGrantRepository;
import com.openhis.whale.permission.infrastructure.repository.PermissionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 权限管理服务（Domain Service）
 */
@Service
public class PermissionManager {

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PermissionGrantRepository permissionGrantRepository;

    /**
     * 授予权限
     */
    public void grantPermission(String permissionName, String providerName, String providerKey) {
        // 检查权限是否存在
        permissionRepository.findByName(permissionName)
                .orElseThrow(() -> new ValidationException("权限定义不存在: " + permissionName));

        // 检查是否已授予
        if (permissionGrantRepository.existsByPermissionNameAndProviderNameAndProviderKey(
                permissionName, providerName, providerKey)) {
            throw new BusinessException("权限已授予");
        }

        // 创建权限授予
        PermissionGrant grant = new PermissionGrant();
        grant.setPermissionName(permissionName);
        grant.setProviderName(providerName);
        grant.setProviderKey(providerKey);

        permissionGrantRepository.save(grant);
    }

    /**
     * 撤销权限
     */
    public void revokePermission(String permissionName, String providerName, String providerKey) {
        // 检查权限授予是否存在
        if (!permissionGrantRepository.existsByPermissionNameAndProviderNameAndProviderKey(
                permissionName, providerName, providerKey)) {
            throw new BusinessException("权限授予不存在");
        }

        // 删除权限授予
        permissionGrantRepository.deleteByPermissionNameAndProviderNameAndProviderKey(
                permissionName, providerName, providerKey);
    }

    /**
     * 检查权限是否已授予
     */
    public boolean isGranted(String permissionName, String providerName, String providerKey) {
        return permissionGrantRepository.existsByPermissionNameAndProviderNameAndProviderKey(
                permissionName, providerName, providerKey);
    }
}
