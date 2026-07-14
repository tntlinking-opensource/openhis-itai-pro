/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.platform.identity;

import com.openhis.whale.core.application.ILoginUserOrganizationUnitSync;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 登录选科室前，按 {@code adm_pr_assign} 刷新 {@code whale_user_organization_units}。
 */
@Component
@RequiredArgsConstructor
public class HealthLoginUserOrganizationUnitSync implements ILoginUserOrganizationUnitSync {

    private final UserLoginOrganizationSyncService userLoginOrganizationSyncService;
    private final ICurrentUser currentUser;

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
        userLoginOrganizationSyncService.syncForUserId(userId);
    }
}
