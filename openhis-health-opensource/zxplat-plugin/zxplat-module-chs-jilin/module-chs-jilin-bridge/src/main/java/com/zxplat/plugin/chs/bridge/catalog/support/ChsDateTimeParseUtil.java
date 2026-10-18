/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.bridge.catalog.support;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 医保目录 TXT 日期时间解析工具。
 * <p>支持多种常见格式，避免 chs-application 模块依赖 health-domain-shared。</p>
 */
public final class ChsDateTimeParseUtil {

    private static final DateTimeFormatter[] FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd")
    };

    private ChsDateTimeParseUtil() {
    }

    /**
     * 将平台日期时间字符串解析为 {@link LocalDateTime}。
     * <p>依次尝试 {@code yyyy-MM-dd HH:mm:ss}、ISO 本地日期时间与纯日期格式；
     * 纯日期补全为当天 00:00:00。解析失败返回 null。</p>
     *
     * @param s 原始日期时间字符串
     * @return 解析结果，空或无法识别时返回 null
     */
    public static LocalDateTime parseLocalDateTime(String s) {
        if (isEmpty(s)) {
            return null;
        }
        String text = s.trim();
        for (DateTimeFormatter formatter : FORMATTERS) {
            try {
                if (formatter.toString().contains("HH")) {
                    return LocalDateTime.parse(text, formatter);
                }
                return LocalDateTime.parse(text + " 00:00:00", FORMATTERS[0]);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }
}
