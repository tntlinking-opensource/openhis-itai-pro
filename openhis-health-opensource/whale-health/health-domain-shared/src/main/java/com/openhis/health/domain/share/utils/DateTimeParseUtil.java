/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

/* (C)2026 */
package com.openhis.health.domain.share.utils;

import com.openhis.health.domain.share.constants.GeneralSharedConstants;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 日期时间字符串解析工具，供查询条件、前端传入时间串等场景统一解析为 LocalDateTime。
 *
 */
public final class DateTimeParseUtil {

    private static final String[] DATE_TIME_PATTERNS = {
        GeneralSharedConstants.DATE_TIME_PATTERN,
        "yyyy-MM-dd HH:mm",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd'T'HH:mm"
    };

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    private DateTimeParseUtil() {
    }

    /**
     * 将常见日期时间字符串解析为 LocalDateTime。
     * <p>支持格式：yyyy-MM-dd HH:mm:ss、yyyy-MM-dd HH:mm、yyyy-MM-dd'T'HH:mm:ss、yyyy-MM-dd'T'HH:mm、yyyy-MM-dd。
     * 仅日期时按当天 00:00:00；解析失败返回 null。</p>
     *
     * @param s 时间字符串，null 或空白返回 null
     * @return LocalDateTime 或 null
     */
    public static LocalDateTime parseLocalDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        s = s.trim();
        for (String pattern : DATE_TIME_PATTERNS) {
            try {
                return LocalDateTime.parse(s, DateTimeFormatter.ofPattern(pattern));
            } catch (DateTimeParseException ignored) {
                // continue
            }
        }
        try {
            return LocalDate.parse(s, GeneralSharedConstants.DATE_DISPLAY_FORMATTER).atStartOfDay();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }
}
