/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.security;

/**
 * 用户已选科室的跨请求持久化存储（权威来源）。
 *
 * <p>生产环境可替换为 Redis 实现；业务代码禁止绕过本接口直接操作 ThreadLocal。</p>
 */
public interface OrganizationUnitSessionStore {

    /**
     * 读取用户在当前租户下已选择的科室。
     */
    Long get(Long userId, Long tenantId);

    /**
     * 保存用户选择的科室。
     */
    void put(Long userId, Long tenantId, Long organizationUnitId);

    /**
     * 清除用户已选科室（登出、切换租户、主动退出科室时调用）。
     */
    void remove(Long userId, Long tenantId);
}
