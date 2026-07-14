/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.infrastructure.session;

import com.openhis.whale.identity.domain.UserLoginSession;
import com.openhis.whale.identity.infrastructure.repository.UserLoginSessionRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 用户登录会话读写（租户 / 医院 / 科室）。
 *
 * <p>读路径经 {@link UserLoginSessionCache}：同请求只加载一次，跨请求短 TTL 本地缓存。</p>
 */
@Service
@Transactional
public class UserLoginSessionSupport {

    private final UserLoginSessionRepository repository;
    private final UserLoginSessionCache sessionCache;

    public UserLoginSessionSupport(
            UserLoginSessionRepository repository,
            UserLoginSessionCache sessionCache) {
        this.repository = repository;
        this.sessionCache = sessionCache;
    }

    public Long getTenantId(Long userId) {
        return sessionCache.getSnapshot(userId).tenantId();
    }

    public void putTenantId(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return;
        }
        sessionCache.refreshAfterWrite(userId, () -> {
            UserLoginSession session = getOrCreateEntity(userId);
            if (!Objects.equals(session.getTenantId(), tenantId)) {
                session.setHospitalOrganizationId(null);
                session.setOrganizationUnitId(null);
            }
            session.setTenantId(tenantId);
            touch(session);
            UserLoginSession saved = repository.save(session);
            return UserLoginSessionCache.toSnapshot(saved);
        });
    }

    public void removeTenant(Long userId) {
        if (userId == null) {
            return;
        }
        repository.deleteById(userId);
        sessionCache.invalidate(userId);
    }

    public Long getHospitalOrganizationId(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return null;
        }
        UserLoginSessionSnapshot snapshot = sessionCache.getSnapshot(userId);
        if (!tenantId.equals(snapshot.tenantId())) {
            return null;
        }
        return snapshot.hospitalOrganizationId();
    }

    public void putHospitalOrganizationId(Long userId, Long tenantId, Long hospitalOrganizationId) {
        if (userId == null || tenantId == null || hospitalOrganizationId == null) {
            return;
        }
        sessionCache.refreshAfterWrite(userId, () -> {
            UserLoginSession session = getOrCreateEntity(userId);
            session.setTenantId(tenantId);
            session.setHospitalOrganizationId(hospitalOrganizationId);
            touch(session);
            UserLoginSession saved = repository.save(session);
            return UserLoginSessionCache.toSnapshot(saved);
        });
    }

    public void removeHospitalOrganizationId(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return;
        }
        UserLoginSessionSnapshot snapshot = sessionCache.getSnapshot(userId);
        if (!tenantId.equals(snapshot.tenantId()) || snapshot.hospitalOrganizationId() == null) {
            return;
        }
        sessionCache.refreshAfterWrite(userId, () -> {
            UserLoginSession session = getOrCreateEntity(userId);
            if (tenantId.equals(session.getTenantId())) {
                session.setHospitalOrganizationId(null);
                touch(session);
                UserLoginSession saved = repository.save(session);
                return UserLoginSessionCache.toSnapshot(saved);
            }
            return snapshot.withoutHospitalOrganizationId();
        });
    }

    public Long getOrganizationUnitId(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return null;
        }
        UserLoginSessionSnapshot snapshot = sessionCache.getSnapshot(userId);
        if (!tenantId.equals(snapshot.tenantId())) {
            return null;
        }
        return snapshot.organizationUnitId();
    }

    public void putOrganizationUnitId(Long userId, Long tenantId, Long organizationUnitId) {
        if (userId == null || tenantId == null || organizationUnitId == null) {
            return;
        }
        sessionCache.refreshAfterWrite(userId, () -> {
            UserLoginSession session = getOrCreateEntity(userId);
            session.setTenantId(tenantId);
            session.setOrganizationUnitId(organizationUnitId);
            touch(session);
            UserLoginSession saved = repository.save(session);
            return UserLoginSessionCache.toSnapshot(saved);
        });
    }

    public void removeOrganizationUnitId(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return;
        }
        UserLoginSessionSnapshot snapshot = sessionCache.getSnapshot(userId);
        if (!tenantId.equals(snapshot.tenantId()) || snapshot.organizationUnitId() == null) {
            return;
        }
        sessionCache.refreshAfterWrite(userId, () -> {
            UserLoginSession session = getOrCreateEntity(userId);
            if (tenantId.equals(session.getTenantId())) {
                session.setOrganizationUnitId(null);
                touch(session);
                UserLoginSession saved = repository.save(session);
                return UserLoginSessionCache.toSnapshot(saved);
            }
            return snapshot.withoutOrganizationUnitId();
        });
    }

    private UserLoginSession getOrCreateEntity(Long userId) {
        Optional<UserLoginSession> existing = repository.findById(userId);
        if (existing.isPresent()) {
            return existing.get();
        }
        UserLoginSession session = new UserLoginSession();
        session.setUserId(userId);
        session.setUpdatedAt(LocalDateTime.now());
        try {
            return repository.saveAndFlush(session);
        } catch (DataIntegrityViolationException ex) {
            // 并发或同事务内重复 get-or-create 时，回读已存在行并继续更新
            sessionCache.invalidate(userId);
            return repository.findById(userId).orElseThrow(() -> ex);
        }
    }

    private static void touch(UserLoginSession session) {
        session.setUpdatedAt(LocalDateTime.now());
    }
}
