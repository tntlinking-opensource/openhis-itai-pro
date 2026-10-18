/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.infrastructure.session;

import com.zxplat.framework.core.infrastructure.security.LoginSessionIdRequestContext;
import com.zxplat.framework.core.infrastructure.security.UserPrincipal;
import com.zxplat.framework.identity.domain.UserLoginSession;
import com.zxplat.framework.identity.infrastructure.repository.UserLoginSessionRepository;
import com.zxplat.framework.identity.infrastructure.session.LoginInstanceScopeStore.LoginInstanceScope;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * 登录会话读写（租户 / 医院 / 科室）。
 *
 * <p>当前请求带 JWT {@code loginSessionId} 时走进程内实例存储，多开浏览器互不覆盖，不改库表。
 * 无会话实例（旧 Token）才回落 {@code zxplat_user_login_session} 一人一行。</p>
 */
@Service
@Transactional
public class UserLoginSessionSupport {

    private final UserLoginSessionRepository repository;
    private final UserLoginSessionCache sessionCache;
    private final LoginInstanceScopeStore instanceScopeStore;

    public UserLoginSessionSupport(
            UserLoginSessionRepository repository,
            UserLoginSessionCache sessionCache,
            LoginInstanceScopeStore instanceScopeStore) {
        this.repository = repository;
        this.sessionCache = sessionCache;
        this.instanceScopeStore = instanceScopeStore;
    }

    /**
     * 登录/代理签发 Token 前在进程内占位本会话实例。
     *
     * @param loginSessionId JWT 会话实例
     * @param userId         用户主键
     */
    public void ensureSession(String loginSessionId, Long userId) {
        // 旧 Token 回落键 u:{userId} 不占实例内存，仍走库表
        if (loginSessionId == null || loginSessionId.isBlank() || loginSessionId.startsWith("u:")) {
            return;
        }
        instanceScopeStore.ensure(loginSessionId, userId);
    }

    /**
     * 向指定会话实例写租户（代理登录不得写到管理员当前请求会话）。
     *
     * @param loginSessionId 目标会话实例
     * @param userId         用户主键
     * @param tenantId       租户主键
     */
    public void putTenantIdOnSession(String loginSessionId, Long userId, Long tenantId) {
        // 代理登录写目标 Token 的实例，不得落到管理员当前请求会话
        if (loginSessionId == null || loginSessionId.isBlank() || loginSessionId.startsWith("u:")) {
            return;
        }
        instanceScopeStore.putTenantId(loginSessionId, userId, tenantId);
    }

    /**
     * 读取当前请求的租户。
     *
     * @param userId 用户主键
     * @return 租户 id
     */
    public Long getTenantId(Long userId) {
        String instanceId = currentInstanceId();
        // 有 JWT 实例则只读本浏览器内存，禁止回落到库表一人一行（否则多开会串科室）
        if (instanceId != null) {
            LoginInstanceScope scope = instanceScopeStore.get(instanceId);
            // 进程刚重启或尚未 selectTenant：本实例无范围
            if (scope == null) {
                return null;
            }
            return scope.tenantId();
        }
        return sessionCache.getSnapshot(userId).tenantId();
    }

    /**
     * 写入当前请求的租户；换租户清空医院与科室。
     *
     * @param userId   用户主键
     * @param tenantId 租户主键
     */
    public void putTenantId(Long userId, Long tenantId) {
        String instanceId = currentInstanceId();
        // 当前请求已绑定 Token 实例：只写本浏览器
        if (instanceId != null) {
            instanceScopeStore.putTenantId(instanceId, userId, tenantId);
            return;
        }
        // 旧 Token 无实例：仍写库表一人一行
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

    /**
     * 登出：有会话实例只删本 Token；否则删一人一行库表。
     *
     * @param userId 用户主键
     */
    public void removeTenant(Long userId) {
        String instanceId = currentInstanceId();
        // 登出只清本 Token，另一浏览器的检验科/住院内科不受影响
        if (instanceId != null) {
            instanceScopeStore.remove(instanceId);
            return;
        }
        if (userId == null) {
            return;
        }
        repository.deleteById(userId);
        sessionCache.invalidate(userId);
    }

    /**
     * 读取当前请求的医院。
     *
     * @param userId   用户主键
     * @param tenantId 租户
     * @return 医院 id
     */
    public Long getHospitalOrganizationId(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return null;
        }
        String instanceId = currentInstanceId();
        if (instanceId != null) {
            LoginInstanceScope scope = instanceScopeStore.get(instanceId);
            // 租户不一致视为未选医院，避免串到上一租户的院区
            if (scope == null || !tenantId.equals(scope.tenantId())) {
                return null;
            }
            return scope.hospitalOrganizationId();
        }
        UserLoginSessionSnapshot snapshot = sessionCache.getSnapshot(userId);
        if (!tenantId.equals(snapshot.tenantId())) {
            return null;
        }
        return snapshot.hospitalOrganizationId();
    }

    /**
     * 写入当前请求的医院。
     *
     * @param userId                 用户主键
     * @param tenantId               租户
     * @param hospitalOrganizationId 医院
     */
    public void putHospitalOrganizationId(Long userId, Long tenantId, Long hospitalOrganizationId) {
        if (userId == null || tenantId == null || hospitalOrganizationId == null) {
            return;
        }
        String instanceId = currentInstanceId();
        if (instanceId != null) {
            instanceScopeStore.putHospitalId(instanceId, userId, tenantId, hospitalOrganizationId);
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

    /**
     * 清除当前请求的医院。
     *
     * @param userId   用户主键
     * @param tenantId 租户
     */
    public void removeHospitalOrganizationId(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return;
        }
        String instanceId = currentInstanceId();
        if (instanceId != null) {
            instanceScopeStore.clearHospitalId(instanceId, tenantId);
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

    /**
     * 读取当前请求的登录科室。
     *
     * @param userId   用户主键
     * @param tenantId 租户
     * @return 科室 id
     */
    public Long getOrganizationUnitId(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return null;
        }
        String instanceId = currentInstanceId();
        if (instanceId != null) {
            LoginInstanceScope scope = instanceScopeStore.get(instanceId);
            // 租户不一致视为未选科室，避免串科导致医技队列 EX-MT-EXE-101
            if (scope == null || !tenantId.equals(scope.tenantId())) {
                return null;
            }
            return scope.organizationUnitId();
        }
        UserLoginSessionSnapshot snapshot = sessionCache.getSnapshot(userId);
        if (!tenantId.equals(snapshot.tenantId())) {
            return null;
        }
        return snapshot.organizationUnitId();
    }

    /**
     * 写入当前请求的登录科室。
     *
     * @param userId             用户主键
     * @param tenantId           租户
     * @param organizationUnitId 科室
     */
    public void putOrganizationUnitId(Long userId, Long tenantId, Long organizationUnitId) {
        if (userId == null || tenantId == null || organizationUnitId == null) {
            return;
        }
        String instanceId = currentInstanceId();
        if (instanceId != null) {
            instanceScopeStore.putOrganizationUnitId(instanceId, userId, tenantId, organizationUnitId);
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

    /**
     * 清除当前请求的登录科室。
     *
     * @param userId   用户主键
     * @param tenantId 租户
     */
    public void removeOrganizationUnitId(Long userId, Long tenantId) {
        if (userId == null || tenantId == null) {
            return;
        }
        String instanceId = currentInstanceId();
        if (instanceId != null) {
            instanceScopeStore.clearOrganizationUnitId(instanceId, tenantId);
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

    /**
     * 本请求真实 JWT 会话实例；无 claim 或旧回落键则 {@code null}（走库表一人一行）。
     *
     * @return UUID 会话实例；legacy {@code u:{userId}} 不当作实例隔离键
     */
    private String currentInstanceId() {
        String id = LoginSessionIdRequestContext.get();
        // ThreadLocal 若被入口拦截器误清，从 Principal 回补，避免多浏览器回落到 userId 串科
        if (id == null || id.isBlank()) {
            id = loginSessionIdFromPrincipal();
            if (id != null && !id.isBlank()) {
                LoginSessionIdRequestContext.set(id);
            }
        }
        if (id == null || id.isBlank()) {
            return null;
        }
        // 过滤器对无 claim 的旧 Token 填的 u:{userId}：仍一人一行，不进实例内存
        if (id.startsWith("u:")) {
            return null;
        }
        return id;
    }

    /**
     * 从 SecurityContext 的 {@link UserPrincipal} 取 JWT 会话实例。
     *
     * @return 会话实例；未登录或旧 Principal 则 {@code null}
     */
    private static String loginSessionIdFromPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (!(principal instanceof UserPrincipal userPrincipal)) {
            return null;
        }
        return userPrincipal.getLoginSessionId();
    }

    /**
     * 按 userId 取或建库表会话行（仅旧 Token 无实例 id 时）。
     *
     * @param userId 用户主键
     * @return 实体
     */
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
            sessionCache.invalidate(userId);
            return repository.findById(userId).orElseThrow(() -> ex);
        }
    }

    /**
     * 刷新库表会话更新时间。
     *
     * @param session 实体
     */
    private static void touch(UserLoginSession session) {
        session.setUpdatedAt(LocalDateTime.now());
    }
}
