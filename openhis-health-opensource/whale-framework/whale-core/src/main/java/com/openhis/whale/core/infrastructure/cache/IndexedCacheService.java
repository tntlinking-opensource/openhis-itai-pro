/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 通用的二级索引缓存服务
 *
 * <p>支持：</p>
 * <ul>
 *   <li>精确查询：根据主键直接命中（O(1)）</li>
 *   <li>索引查询：根据索引键查询所有相关数据</li>
 *   <li>模糊查询：根据主键模糊匹配</li>
 * </ul>
 *
 * @param <T> 缓存对象类型
 */
@Slf4j
@Component
public class IndexedCacheService<T> {

    /**
     * 主缓存：indexKey:itemKey -> Object
     * 例如：medication_category_code:01 -> Concept
     */
    private final Cache<String, T> itemCache;

    /**
     * 索引缓存：indexKey -> Set<itemKey>
     * 例如：medication_category_code -> ["01", "02", "03"]
     */
    private final Cache<String, Set<String>> indexCache;

    public IndexedCacheService() {
        // 术语概念全量预热可达数千～上万条；过小会导致 item 被驱逐而 index 仍残留，getAll 返回残缺列表
        this.itemCache = Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(50000)
            .build();

        this.indexCache = Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(5000)
            .build();
    }

    /**
     * 自定义过期时间和大小的构造函数
     */
    public IndexedCacheService(long expireHours, long maxItemSize, long maxIndexSize) {
        this.itemCache = Caffeine.newBuilder()
            .expireAfterWrite(expireHours, TimeUnit.HOURS)
            .maximumSize(maxItemSize)
            .build();

        this.indexCache = Caffeine.newBuilder()
            .expireAfterWrite(expireHours, TimeUnit.HOURS)
            .maximumSize(maxIndexSize)
            .build();
    }

    /**
     * 获取单个对象（精确查询，O(1)）
     *
     * @param indexKey 索引键（如：编码系统名称）
     * @param itemKey 项键（如：编码）
     * @return 缓存对象，不存在返回 null
     */
    public T get(String indexKey, String itemKey) {
        String cacheKey = buildCacheKey(indexKey, itemKey);
        return itemCache.getIfPresent(cacheKey);
    }

    /**
     * 获取索引下的所有对象
     *
     * @param indexKey 索引键
     * @return 对象列表
     */
    public List<T> getAll(String indexKey) {
        Set<String> itemKeys = indexCache.getIfPresent(indexKey);

        if (itemKeys == null || itemKeys.isEmpty()) {
            return new ArrayList<>();
        }

        List<T> result = itemKeys.stream()
            .map(itemKey -> itemCache.getIfPresent(buildCacheKey(indexKey, itemKey)))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
        // index 仍在但 item 已被 maximumSize 驱逐 → 视为未命中，迫使调用方回源全量重载
        if (result.size() != itemKeys.size()) {
            log.warn(
                "IndexedCache partial eviction detected, indexKey={}, indexSize={}, itemHit={}",
                indexKey,
                itemKeys.size(),
                result.size());
            return new ArrayList<>();
        }
        return result;
    }

    /**
     * 模糊查询（根据 itemKey 模糊匹配）
     *
     * @param indexKey 索引键
     * @param itemKeyPattern 项键模糊匹配
     * @return 匹配的对象列表
     */
    public List<T> findByItemKeyLike(String indexKey, String itemKeyPattern) {
        Set<String> itemKeys = indexCache.getIfPresent(indexKey);

        if (itemKeys == null || itemKeys.isEmpty()) {
            return new ArrayList<>();
        }

        return itemKeys.stream()
            .filter(itemKey -> itemKey.contains(itemKeyPattern))
            .map(itemKey -> itemCache.getIfPresent(buildCacheKey(indexKey, itemKey)))
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    /**
     * 放入缓存
     *
     * @param indexKey 索引键
     * @param itemKey 项键
     * @param item 对象
     */
    public void put(String indexKey, String itemKey, T item) {
        String cacheKey = buildCacheKey(indexKey, itemKey);

        // 1. 缓存对象
        itemCache.put(cacheKey, item);

        // 2. 更新索引
        Set<String> itemKeys = indexCache.get(indexKey, k -> new HashSet<>());
        itemKeys.add(itemKey);
        indexCache.put(indexKey, itemKeys);

        log.debug("缓存已更新: {} -> {}", cacheKey, item.getClass().getSimpleName());
    }

    /**
     * 批量放入缓存
     *
     * @param indexKey 索引键
     * @param items 对象列表
     * @param itemKeyExtractor 从对象提取 itemKey 的函数
     */
    public void putAll(String indexKey, List<T> items, Function<T, String> itemKeyExtractor) {
        if (items == null || items.isEmpty()) {
            return;
        }

        Set<String> itemKeys = new HashSet<>();

        for (T item : items) {
            String itemKey = itemKeyExtractor.apply(item);
            String cacheKey = buildCacheKey(indexKey, itemKey);

            itemCache.put(cacheKey, item);
            itemKeys.add(itemKey);
        }

        indexCache.put(indexKey, itemKeys);

        log.debug("批量缓存已更新: {} -> {} items", indexKey, items.size());
    }

    /**
     * 移除单个对象
     *
     * @param indexKey 索引键
     * @param itemKey 项键
     */
    public void evict(String indexKey, String itemKey) {
        String cacheKey = buildCacheKey(indexKey, itemKey);

        // 1. 移除对象缓存
        itemCache.invalidate(cacheKey);

        // 2. 更新索引
        Set<String> itemKeys = indexCache.getIfPresent(indexKey);
        if (itemKeys != null) {
            itemKeys.remove(itemKey);
            if (itemKeys.isEmpty()) {
                indexCache.invalidate(indexKey);
            } else {
                indexCache.put(indexKey, itemKeys);
            }
        }

        log.debug("缓存已移除: {}", cacheKey);
    }

    /**
     * 移除索引下的所有对象
     *
     * @param indexKey 索引键
     */
    public void evictAll(String indexKey) {
        Set<String> itemKeys = indexCache.getIfPresent(indexKey);

        if (itemKeys != null) {
            itemKeys.forEach(itemKey -> {
                String cacheKey = buildCacheKey(indexKey, itemKey);
                itemCache.invalidate(cacheKey);
            });
        }

        indexCache.invalidate(indexKey);

        log.debug("索引缓存已清除: {}", indexKey);
    }

    /**
     * 清空所有缓存
     */
    public void clear() {
        itemCache.invalidateAll();
        indexCache.invalidateAll();
        log.debug("所有缓存已清空");
    }

    /**
     * 获取缓存统计信息
     */
    public CacheStats getStats() {
        return new CacheStats(
            itemCache.estimatedSize(),
            indexCache.estimatedSize()
        );
    }

    /**
     * 构建缓存 key
     */
    private String buildCacheKey(String indexKey, String itemKey) {
        return indexKey + ":" + itemKey;
    }

    /**
     * 缓存统计信息
     */
    public static class CacheStats {
        public final long itemCacheSize;
        public final long indexCacheSize;

        public CacheStats(long itemCacheSize, long indexCacheSize) {
            this.itemCacheSize = itemCacheSize;
            this.indexCacheSize = indexCacheSize;
        }

        @Override
        public String toString() {
            return String.format("ItemCache: %d, IndexCache: %d", itemCacheSize, indexCacheSize);
        }
    }
}
