/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.framework.core.infrastructure.security;

import java.util.UUID;

/**
 * 登录会话存储键：优先 JWT 会话实例，缺省时回落到历史「一用户一行」键。
 */
public final class LoginSessionStoreKeys {

    private static final String LEGACY_PREFIX = "u:";

    private LoginSessionStoreKeys() {
    }

    /**
     * 签发新登录时生成会话实例 id（写入 Access/Refresh JWT {@code loginSessionId}）。
     *
     * @return 非空 UUID 字符串
     */
    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /**
     * 无 JWT 会话实例时的历史键：按用户一份，旧 Token 仍可能互相覆盖。
     *
     * @param userId 用户主键
     * @return {@code u:} 前缀键；userId 空则 {@code null}
     */
    public static String legacyKey(Long userId) {
        if (userId == null) {
            return null;
        }
        return LEGACY_PREFIX + userId;
    }

    /**
     * 解析本请求应读写的会话存储键。
     *
     * <p>有 {@link LoginSessionIdRequestContext} 则用之（多开浏览器互不覆盖）；
     * 否则回落 {@link #legacyKey(Long)}，兼容尚未带 claim 的旧 Token。</p>
     *
     * @param userId 用户主键（仅缺会话实例时使用）
     * @return 存储键；均不可用时 {@code null}
     */
    public static String resolve(Long userId) {
        String fromRequest = LoginSessionIdRequestContext.get();
        if (fromRequest != null) {
            String trimmed = fromRequest.trim();
            if (!trimmed.isEmpty()) {
                return trimmed;
            }
        }
        return legacyKey(userId);
    }
}
