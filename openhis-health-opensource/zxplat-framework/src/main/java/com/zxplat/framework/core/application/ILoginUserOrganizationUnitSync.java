/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.application;

/**
 * 登录拉取可登录科室前，按岗位派生结果刷新 {@code zxplat_user_organization_units}（health 模块实现）。
 */
public interface ILoginUserOrganizationUnitSync {

    /**
     * 将当前登录用户在当前租户下的可登录科室与岗位管辖科室对齐。
     */
    void refreshBeforeLoginDepartmentQuery();

    /**
     * 清理本请求内「已刷新可登录科室」标记，防止 Tomcat 线程复用串用户。
     *
     * <p>由请求拦截器在入口与结束时调用；默认空实现。</p>
     */
    default void clearRequestCache() {
    }
}
