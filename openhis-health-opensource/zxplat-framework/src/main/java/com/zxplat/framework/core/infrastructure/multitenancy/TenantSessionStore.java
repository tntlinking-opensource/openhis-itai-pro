/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.multitenancy;

/**
 * 当前登录 Token 已选租户的跨请求持久化存储（权威来源）。
 *
 * <p>键为 JWT 登录会话实例，不是 userId。生产可换 Redis；禁止绕过本接口直接操作 ThreadLocal。</p>
 */
public interface TenantSessionStore {

    /**
     * 读取当前登录会话的租户。
     *
     * @param userId 用户主键（无会话实例时回落历史键）
     * @return 租户 id
     */
    Long get(Long userId);

    /**
     * 保存当前登录会话选择的租户。
     *
     * @param userId   用户主键
     * @param tenantId 租户主键
     */
    void put(Long userId, Long tenantId);

    /**
     * 清除当前登录会话（登出时删整行，含医院/科室）。
     *
     * @param userId 用户主键
     */
    void remove(Long userId);
}
