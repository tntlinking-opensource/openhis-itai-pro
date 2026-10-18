/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.identity.infrastructure.jwt;

/**
 * JWT Claims 名称（Access / Refresh 共用）。
 */
public final class JwtClaimNames {

    /**
     * 登录会话实例：同一用户多 Token 各自一份租户/医院/科室选择。
     */
    public static final String LOGIN_SESSION_ID = "loginSessionId";

    private JwtClaimNames() {
    }
}
