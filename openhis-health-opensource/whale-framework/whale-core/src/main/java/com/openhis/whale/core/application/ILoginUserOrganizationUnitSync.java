/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.application;

/**
 * 登录拉取可登录科室前，按岗位派生结果刷新 {@code whale_user_organization_units}（health 模块实现）。
 */
public interface ILoginUserOrganizationUnitSync {

    /**
     * 将当前登录用户在当前租户下的可登录科室与岗位管辖科室对齐。
     */
    void refreshBeforeLoginDepartmentQuery();
}
