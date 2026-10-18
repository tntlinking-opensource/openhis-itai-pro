/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.cache;

/**
 * 缓存Key生成器
 */
public class CacheKeyGenerator {

    /**
     * 生成用户缓存Key
     * @param userId 用户ID
     * @return 缓存Key
     */
    public static String userKey(Long userId) {
        return "user:" + userId;
    }

    /**
     * 生成角色缓存Key
     * @param roleId 角色ID
     * @return 缓存Key
     */
    public static String roleKey(Long roleId) {
        return "role:" + roleId;
    }

    /**
     * 生成权限缓存Key
     * @param userId 用户ID
     * @return 缓存Key
     */
    public static String permissionKey(Long userId) {
        return "permission:user:" + userId;
    }

    /**
     * 生成租户缓存Key
     * @param tenantId 租户ID
     * @return 缓存Key
     */
    public static String tenantKey(Long tenantId) {
        return "tenant:" + tenantId;
    }

    /**
     * 生成组织单元缓存Key
     * @param ouId 组织单元ID
     * @return 缓存Key
     */
    public static String organizationUnitKey(Long ouId) {
        return "ou:" + ouId;
    }

    /**
     * 生成通用缓存Key
     * @param prefix 前缀
     * @param key 键
     * @return 缓存Key
     */
    public static String key(String prefix, Object key) {
        return prefix + ":" + key;
    }
}
