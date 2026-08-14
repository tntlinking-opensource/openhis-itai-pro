/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.authorization;

/**
 * 病案管理员鉴权：门诊/住院「归档病例 / 解档病例」须持有 {@code ROLE_MRMS_ADMIN}。
 *
 * <p>实现由 {@code health-infrastructure/platform/authorization} 装配；
 * AppService 经本接口委托，禁止直查用户表。</p>
 */
public interface MrmsAdminAuthorizer {

    /**
     * 当前登录用户是否具备病案管理员角色。
     *
     * @return 持有 {@code ROLE_MRMS_ADMIN} 时为 {@code true}
     */
    boolean hasMrmsAdminRole();

    /**
     * 断言当前用户为病案管理员（归档/解档病例入口）。
     *
     * @throws com.openhis.whale.core.application.exception.BusinessException 无角色时
     */
    void assertMrmsAdminRole();
}
