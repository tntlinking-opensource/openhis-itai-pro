/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.identity.infrastructure.jwt;

/**
 * JWT 中携带的代理登录上下文（被代理用户身份 + 代理者信息）。
 *
 * @param impersonatorUserId 发起代理的管理员用户 ID
 * @param impersonatorUserName 发起代理的管理员用户名
 * @param impersonatorTenantId 代理者当时所在租户 ID，可为 null
 */
public record ImpersonationInfo(
        Long impersonatorUserId,
        String impersonatorUserName,
        Long impersonatorTenantId) {
}
