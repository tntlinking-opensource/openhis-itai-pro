/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.wi.gateway.scheme.shared.support;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** 工伤平台结算时间解析（2206/2207 出参 {@code setl_time}）。 */
public final class WiSetlTimeSupport {

    private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final DateTimeFormatter DASHED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private WiSetlTimeSupport() {
    }

    /**
     * 解析平台结算时间。
     *
     * @param raw 平台原文字段（yyyyMMddHHmmss 或 yyyy-MM-dd HH:mm:ss）
     * @return 解析结果；空或格式错误时 {@code null}
     */
    public static LocalDateTime parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        try {
            if (trimmed.length() == 14 && trimmed.chars().allMatch(Character::isDigit)) {
                return LocalDateTime.parse(trimmed, COMPACT);
            }
            return LocalDateTime.parse(trimmed, DASHED);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    /**
     * 解析平台结算时间；空或格式错误时回落当前时间。
     *
     * @param raw 平台原文字段
     * @return 非空结算时间
     */
    public static LocalDateTime orNow(String raw) {
        LocalDateTime parsed = parse(raw);
        return parsed != null ? parsed : LocalDateTime.now();
    }
}
