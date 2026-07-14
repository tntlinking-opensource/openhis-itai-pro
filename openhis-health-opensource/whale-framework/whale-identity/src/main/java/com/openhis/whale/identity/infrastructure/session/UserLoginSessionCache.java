/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.infrastructure.session;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.openhis.whale.core.infrastructure.web.RequestScopeCleaner;
import com.openhis.whale.identity.domain.UserLoginSession;
import com.openhis.whale.identity.infrastructure.repository.UserLoginSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 登录会话二级缓存 + 请求内单次加载，避免每个 API 多次 hit 库。
 */
@Component
public class UserLoginSessionCache implements RequestScopeCleaner {

    private final LoadingCache<Long, UserLoginSessionSnapshot> l2Cache;
    private final UserLoginSessionRepository repository;
    private final boolean cacheEnabled;

    private static final ThreadLocal<RequestSessionHolder> REQUEST_HOLDER =
            ThreadLocal.withInitial(RequestSessionHolder::new);

    public UserLoginSessionCache(
            UserLoginSessionRepository repository,
            @Value("${app.session-store.cache.enabled:true}") boolean cacheEnabled,
            @Value("${app.session-store.cache.expire-after-write-minutes:2}") long expireMinutes,
            @Value("${app.session-store.cache.maximum-size:5000}") long maximumSize) {
        this.repository = repository;
        this.cacheEnabled = cacheEnabled;
        this.l2Cache = Caffeine.newBuilder()
                .expireAfterWrite(Math.max(1, expireMinutes), TimeUnit.MINUTES)
                .maximumSize(Math.max(100, maximumSize))
                .build(this::loadFromDatabase);
    }

    public UserLoginSessionSnapshot getSnapshot(Long userId) {
        if (userId == null) {
            return UserLoginSessionSnapshot.absent(null);
        }
        RequestSessionHolder holder = REQUEST_HOLDER.get();
        if (holder.loaded && userId.equals(holder.userId) && holder.snapshot != null) {
            return holder.snapshot;
        }
        UserLoginSessionSnapshot snapshot = cacheEnabled
                ? l2Cache.get(userId)
                : loadFromDatabase(userId);
        holder.userId = userId;
        holder.snapshot = snapshot;
        holder.loaded = true;
        return snapshot;
    }

    public UserLoginSessionSnapshot refreshAfterWrite(Long userId, Supplier<UserLoginSessionSnapshot> writer) {
        UserLoginSessionSnapshot snapshot = writer.get();
        if (userId == null) {
            return snapshot;
        }
        if (cacheEnabled) {
            l2Cache.put(userId, snapshot);
        }
        RequestSessionHolder holder = REQUEST_HOLDER.get();
        holder.userId = userId;
        holder.snapshot = snapshot;
        holder.loaded = true;
        return snapshot;
    }

    public void invalidate(Long userId) {
        if (userId == null) {
            return;
        }
        if (cacheEnabled) {
            l2Cache.invalidate(userId);
        }
        RequestSessionHolder holder = REQUEST_HOLDER.get();
        if (userId.equals(holder.userId)) {
            holder.loaded = false;
            holder.snapshot = null;
            holder.userId = null;
        }
    }

    @Override
    public void clearRequestScope() {
        REQUEST_HOLDER.remove();
    }

    private UserLoginSessionSnapshot loadFromDatabase(Long userId) {
        return repository.findById(userId)
                .map(UserLoginSessionCache::toSnapshot)
                .orElseGet(() -> UserLoginSessionSnapshot.absent(userId));
    }

    static UserLoginSessionSnapshot toSnapshot(UserLoginSession session) {
        return new UserLoginSessionSnapshot(
                session.getUserId(),
                session.getTenantId(),
                session.getHospitalOrganizationId(),
                session.getOrganizationUnitId());
    }

    private static final class RequestSessionHolder {
        private Long userId;
        private UserLoginSessionSnapshot snapshot;
        private boolean loaded;
    }
}
