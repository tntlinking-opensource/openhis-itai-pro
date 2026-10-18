/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.platform.identity;

import com.zxplat.framework.core.application.ILoginUserOrganizationUnitSync;
import com.zxplat.framework.core.infrastructure.security.ICurrentUser;
import com.zxplat.framework.core.infrastructure.web.RequestScopeCleaner;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 登录选科室前，按 {@code adm_pr_assign} / 默认登录科室刷新 {@code zxplat_user_organization_units}。
 *
 * <p>同一 HTTP 请求内只同步一次：{@code getAvailableHospitals} 与 {@code getAvailableOrganizationUnits}
 * 可能连续触发刷新，重复跑岗位派生无收益。</p>
 */
@Component
@RequiredArgsConstructor
public class HealthLoginUserOrganizationUnitSync implements ILoginUserOrganizationUnitSync, RequestScopeCleaner {

    private final UserLoginOrganizationSyncService userLoginOrganizationSyncService;
    private final ICurrentUser currentUser;

    /**
     * 本请求是否已对当前用户执行过登录科室同步。
     *
     * <p>挂 ThreadLocal：单例 Bean 在线程池复用时不得串读上一请求标记。</p>
     */
    private static final ThreadLocal<Long> SYNCED_USER_ID = new ThreadLocal<>();

    /**
     * 将当前登录用户在当前租户下的可登录科室与岗位管辖科室对齐。
     *
     * <p>由 {@code AuthAppService} 在拉取医院/科室列表前调用；未登录时不执行。</p>
     */
    @Override
    public void refreshBeforeLoginDepartmentQuery() {
        Long userId = currentUser.getCurrentUserId();
        if (userId == null) {
            return;
        }
        // 本请求已同步过同一用户：跳过，避免医院+科室列表连续刷库
        Long syncedUserId = SYNCED_USER_ID.get();
        if (userId.equals(syncedUserId)) {
            return;
        }
        userLoginOrganizationSyncService.syncForUserId(userId);
        SYNCED_USER_ID.set(userId);
    }

    /**
     * 清理本请求内登录科室同步标记（须与 {@link ICurrentUser#clearRequestCache()} 同时机调用）。
     */
    @Override
    public void clearRequestCache() {
        SYNCED_USER_ID.remove();
    }

    /**
     * 请求结束时释放登录科室同步 ThreadLocal（与科室拦截器 {@link RequestScopeCleaner} 链对齐）。
     */
    @Override
    public void clearRequestScope() {
        this.clearRequestCache();
    }
}
