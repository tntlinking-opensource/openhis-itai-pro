/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.pacsimaging.application.viewer.token;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

/**
 * 阅片短期 Token 签发（应用层，不入领域库）。
 *
 * <p>Token 只绑定 studyInstanceUid，默认有效期 10 分钟。</p>
 */
@Component
public class ViewerTokenIssuer {

    private static final int TTL_MINUTES = 10;

    /**
     * 签发阅片 Token。
     *
     * @param studyInstanceUid Study UID
     * @return 签发结果（opaque token + 过期时刻）
     */
    public IssuedViewerToken issue(String studyInstanceUid) {
        Instant expiresAt = Instant.now().plus(TTL_MINUTES, ChronoUnit.MINUTES);
        String raw = studyInstanceUid + "|" + expiresAt.toEpochMilli() + "|" + UUID.randomUUID();
        String token = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return new IssuedViewerToken(token, expiresAt);
    }

    /**
     * 已签发 Token。
     *
     * @param token opaque token
     * @param expiresAt 过期时刻
     */
    public record IssuedViewerToken(String token, Instant expiresAt) {
    }
}
