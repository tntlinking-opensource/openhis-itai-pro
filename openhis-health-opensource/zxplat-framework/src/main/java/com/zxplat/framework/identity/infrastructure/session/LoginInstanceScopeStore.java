/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.infrastructure.session;

import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 按 JWT 登录会话实例隔离的租户/医院/科室（进程内）。
 *
 * <p>不改库表：一人一行的 {@code zxplat_user_login_session} 无法表达多浏览器。
 * 前端进页会重新 {@code selectTenant}/{@code selectOrganizationUnit}，进程重启后可恢复。</p>
 */
@Component
public class LoginInstanceScopeStore {

    private final ConcurrentHashMap<String, LoginInstanceScope> store = new ConcurrentHashMap<>();

    /**
     * 登录签发 Token 时占位，后续 select* 写同一实例。
     *
     * @param loginSessionId JWT 会话实例
     * @param userId         用户主键
     */
    public void ensure(String loginSessionId, Long userId) {
        if (loginSessionId == null || loginSessionId.isBlank() || userId == null) {
            return;
        }
        store.putIfAbsent(loginSessionId, LoginInstanceScope.empty(userId));
    }

    /**
     * 读取实例范围；无记录则 {@code null}。
     *
     * @param loginSessionId 会话实例
     * @return 范围快照
     */
    public LoginInstanceScope get(String loginSessionId) {
        if (loginSessionId == null || loginSessionId.isBlank()) {
            return null;
        }
        return store.get(loginSessionId);
    }

    /**
     * 写入租户；换租户时清空医院与科室，避免串到上一院的科。
     *
     * @param loginSessionId 会话实例
     * @param userId         用户主键
     * @param tenantId       租户
     */
    public void putTenantId(String loginSessionId, Long userId, Long tenantId) {
        if (loginSessionId == null || loginSessionId.isBlank() || userId == null || tenantId == null) {
            return;
        }
        store.compute(loginSessionId, (key, previous) -> {
            Long hospitalId = null;
            Long organizationUnitId = null;
            // 同一租户可保留院/科；换租户则清空，避免串院串科
            if (previous != null && Objects.equals(previous.tenantId(), tenantId)) {
                hospitalId = previous.hospitalOrganizationId();
                organizationUnitId = previous.organizationUnitId();
            }
            return new LoginInstanceScope(userId, tenantId, hospitalId, organizationUnitId);
        });
    }

    /**
     * 写入医院。
     *
     * @param loginSessionId         会话实例
     * @param userId                 用户
     * @param tenantId               租户
     * @param hospitalOrganizationId 医院
     */
    public void putHospitalId(String loginSessionId, Long userId, Long tenantId, Long hospitalOrganizationId) {
        if (loginSessionId == null || loginSessionId.isBlank()
                || userId == null || tenantId == null || hospitalOrganizationId == null) {
            return;
        }
        store.compute(loginSessionId, (key, previous) -> {
            Long organizationUnitId = null;
            if (previous != null) {
                organizationUnitId = previous.organizationUnitId();
            }
            return new LoginInstanceScope(userId, tenantId, hospitalOrganizationId, organizationUnitId);
        });
    }

    /**
     * 写入登录科室。
     *
     * @param loginSessionId     会话实例
     * @param userId             用户
     * @param tenantId           租户
     * @param organizationUnitId 科室
     */
    public void putOrganizationUnitId(
            String loginSessionId, Long userId, Long tenantId, Long organizationUnitId) {
        if (loginSessionId == null || loginSessionId.isBlank()
                || userId == null || tenantId == null || organizationUnitId == null) {
            return;
        }
        store.compute(loginSessionId, (key, previous) -> {
            Long hospitalId = null;
            if (previous != null) {
                hospitalId = previous.hospitalOrganizationId();
            }
            return new LoginInstanceScope(userId, tenantId, hospitalId, organizationUnitId);
        });
    }

    /**
     * 清除医院。
     *
     * @param loginSessionId 会话实例
     * @param tenantId       租户（不一致则不改）
     */
    public void clearHospitalId(String loginSessionId, Long tenantId) {
        if (loginSessionId == null || tenantId == null) {
            return;
        }
        store.computeIfPresent(loginSessionId, (key, previous) -> {
            // 租户不一致不改，防止误清其它上下文
            if (!tenantId.equals(previous.tenantId())) {
                return previous;
            }
            return new LoginInstanceScope(
                    previous.userId(), previous.tenantId(), null, previous.organizationUnitId());
        });
    }

    /**
     * 清除科室。
     *
     * @param loginSessionId 会话实例
     * @param tenantId       租户
     */
    public void clearOrganizationUnitId(String loginSessionId, Long tenantId) {
        if (loginSessionId == null || tenantId == null) {
            return;
        }
        store.computeIfPresent(loginSessionId, (key, previous) -> {
            // 租户不一致不改，防止误清其它上下文
            if (!tenantId.equals(previous.tenantId())) {
                return previous;
            }
            return new LoginInstanceScope(
                    previous.userId(),
                    previous.tenantId(),
                    previous.hospitalOrganizationId(),
                    null);
        });
    }

    /**
     * 登出：删除本 Token 的范围，不影响同一用户其它浏览器。
     *
     * @param loginSessionId 会话实例
     */
    public void remove(String loginSessionId) {
        if (loginSessionId == null || loginSessionId.isBlank()) {
            return;
        }
        store.remove(loginSessionId);
    }

    /**
     * 一个 Token 上的租户/医院/科室。
     *
     * @param userId                 用户
     * @param tenantId               租户
     * @param hospitalOrganizationId 医院
     * @param organizationUnitId     科室
     */
    public record LoginInstanceScope(
            Long userId,
            Long tenantId,
            Long hospitalOrganizationId,
            Long organizationUnitId) {

        /**
         * 刚登录、尚未 select* 的空范围。
         *
         * @param userId 用户主键
         * @return 空选择
         */
        public static LoginInstanceScope empty(Long userId) {
            return new LoginInstanceScope(userId, null, null, null);
        }
    }
}
