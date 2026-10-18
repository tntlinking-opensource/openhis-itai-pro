/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 缓存管理器（程序化访问缓存，非 Spring CacheManager）
 * 使用 zxplatCacheManager 避免与 Spring Boot Caffeine 自动配置的 cacheManager 冲突
 */
@Component("zxplatCacheManager")
public class CacheManager {

    // 权限缓存：5分钟，最大5000
    private final Cache<String, Boolean> permissionCache = Caffeine.newBuilder()
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .maximumSize(5000)
            .build();

    // 用户信息缓存：15分钟，最大2000
    private final Cache<String, Object> userCache = Caffeine.newBuilder()
            .expireAfterWrite(15, TimeUnit.MINUTES)
            .maximumSize(2000)
            .build();

    // 租户信息缓存：30分钟，最大100
    private final Cache<String, Object> tenantCache = Caffeine.newBuilder()
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .maximumSize(100)
            .build();

    // 应用设置缓存：10分钟，最大1000
    private final Cache<String, Object> settingCache = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .maximumSize(1000)
            .build();

    /**
     * 获取权限缓存
     */
    public Cache<String, Boolean> getPermissionCache() {
        return permissionCache;
    }

    /**
     * 获取用户信息缓存
     */
    public Cache<String, Object> getUserCache() {
        return userCache;
    }

    /**
     * 获取租户信息缓存
     */
    public Cache<String, Object> getTenantCache() {
        return tenantCache;
    }

    /**
     * 获取应用设置缓存
     */
    public Cache<String, Object> getSettingCache() {
        return settingCache;
    }

    /**
     * 获取缓存值
     */
    public <T> T get(Cache<String, T> cache, String key) {
        return cache.getIfPresent(key);
    }

    /**
     * 设置缓存值
     */
    public <T> void put(Cache<String, T> cache, String key, T value) {
        cache.put(key, value);
    }

    /**
     * 删除缓存
     */
    public void evict(Cache<String, ?> cache, String key) {
        cache.invalidate(key);
    }

    /**
     * 清空缓存
     */
    public void clear(Cache<String, ?> cache) {
        cache.invalidateAll();
    }
}
