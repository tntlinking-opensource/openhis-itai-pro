/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.module.shared.identity;

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
