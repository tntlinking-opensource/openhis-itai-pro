/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.security;

/**
 * 当前登录 Token 已选科室的跨请求持久化存储（权威来源）。
 *
 * <p>存储键为 JWT {@code loginSessionId}（经 {@link LoginSessionStoreKeys}），
 * 同一用户多浏览器互不覆盖。生产环境可替换为 Redis；禁止绕过本接口直接操作 ThreadLocal。</p>
 */
public interface OrganizationUnitSessionStore {

    /**
     * 读取当前登录会话在该租户下已选择的科室。
     *
     * @param userId   用户主键（无会话实例时回落历史键）
     * @param tenantId 租户主键
     * @return 科室 id
     */
    Long get(Long userId, Long tenantId);

    /**
     * 保存当前登录会话选择的科室。
     *
     * @param userId             用户主键
     * @param tenantId           租户主键
     * @param organizationUnitId 科室主键
     */
    void put(Long userId, Long tenantId, Long organizationUnitId);

    /**
     * 清除当前登录会话已选科室（登出、切换租户、主动退出科室时调用）。
     *
     * @param userId   用户主键
     * @param tenantId 租户主键
     */
    void remove(Long userId, Long tenantId);
}
