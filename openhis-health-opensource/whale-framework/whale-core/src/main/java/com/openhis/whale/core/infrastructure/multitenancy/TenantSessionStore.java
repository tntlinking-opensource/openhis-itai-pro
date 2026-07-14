/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.core.infrastructure.multitenancy;

/**
 * 用户已选租户的跨请求持久化存储（权威来源）。
 *
 * <p>生产环境可替换为 Redis 实现；业务代码禁止绕过本接口直接操作 ThreadLocal。</p>
 */
public interface TenantSessionStore {

    /**
     * 读取用户当前会话租户。
     */
    Long get(Long userId);

    /**
     * 保存用户选择的租户。
     */
    void put(Long userId, Long tenantId);

    /**
     * 清除用户已选租户（登出、切换租户时调用）。
     */
    void remove(Long userId);
}
