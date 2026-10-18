/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.elep.application.catalog.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zxplat.plugin.elep.domain.config.ElepContractConfig;
import com.zxplat.plugin.elep.domain.config.ElepContractConfigLoader;
import com.zxplat.plugin.elep.domain.catalog.model.ElepCatalogItem;
import com.zxplat.plugin.elep.domain.catalog.model.ElepCatalogPageResult;
import com.zxplat.plugin.elep.domain.catalog.repository.ElepNationalCatalogReadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

/** 目录 L1/L2 Caffeine 缓存（key 含 catalogEpoch + configEpoch）。 */
@Component
@RequiredArgsConstructor
public class ElepCatalogCacheManager {

    private final ElepNationalCatalogReadRepository catalogReadRepository;

    private final Cache<String, ElepCatalogItem> codeCache = Caffeine.newBuilder()
            .maximumSize(20_000)
            .build();

    private final Cache<String, ElepCatalogPageResult> searchCache = Caffeine.newBuilder()
            .maximumSize(5_000)
            .build();

    private final Cache<String, Long> catalogEpochCache = Caffeine.newBuilder()
            .expireAfterWrite(60, TimeUnit.SECONDS)
            .maximumSize(500)
            .build();

    private volatile long lastConfigEpoch = -1L;

    public CacheContext resolveContext(Long contractId, Long tenantId, String contractBusNo) {
        long configEpoch = ElepContractConfigLoader.getConfigEpoch();
        if (configEpoch != lastConfigEpoch) {
            invalidateAll();
            lastConfigEpoch = configEpoch;
        }
        String epochKey = contractId + ":" + tenantId;
        long catalogEpoch = catalogEpochCache.get(epochKey, k ->
                catalogReadRepository.resolveCatalogEpochMillis(contractId, tenantId));
        ElepContractConfig config = contractBusNo == null
                ? null
                : ElepContractConfigLoader.getByBusNo(contractBusNo);
        boolean cacheEnabled = config == null || config.isCatalogCacheEnabled();
        int ttlMinutes = config == null ? 30 : config.getCatalogCacheTtlMinutes();
        return new CacheContext(configEpoch, catalogEpoch, cacheEnabled, ttlMinutes);
    }

    public Optional<ElepCatalogItem> getCode(CacheContext ctx, String cacheKind, String codeKey) {
        if (!ctx.cacheEnabled()) {
            return Optional.empty();
        }
        return Optional.ofNullable(codeCache.getIfPresent(buildCodeKey(ctx, cacheKind, codeKey)));
    }

    public void putCode(CacheContext ctx, String cacheKind, String codeKey, ElepCatalogItem item) {
        if (!ctx.cacheEnabled() || item == null) {
            return;
        }
        codeCache.put(buildCodeKey(ctx, cacheKind, codeKey), item);
    }

    public Optional<ElepCatalogPageResult> getSearch(CacheContext ctx, String searchKind, String searchKey) {
        if (!ctx.cacheEnabled()) {
            return Optional.empty();
        }
        return Optional.ofNullable(searchCache.getIfPresent(buildSearchKey(ctx, searchKind, searchKey)));
    }

    public void putSearch(CacheContext ctx, String searchKind, String searchKey, ElepCatalogPageResult page) {
        if (!ctx.cacheEnabled() || page == null) {
            return;
        }
        searchCache.put(buildSearchKey(ctx, searchKind, searchKey), page);
    }

    public void invalidateAll() {
        codeCache.invalidateAll();
        searchCache.invalidateAll();
        catalogEpochCache.invalidateAll();
    }

    private static String buildCodeKey(CacheContext ctx, String cacheKind, String codeKey) {
        return ctx.configEpoch() + ":" + ctx.catalogEpoch() + ":code:" + cacheKind + ":" + codeKey;
    }

    private static String buildSearchKey(CacheContext ctx, String searchKind, String searchKey) {
        return ctx.configEpoch() + ":" + ctx.catalogEpoch() + ":search:" + searchKind + ":" + searchKey;
    }

    public record CacheContext(long configEpoch, long catalogEpoch, boolean cacheEnabled, int ttlMinutes) {
    }
}
