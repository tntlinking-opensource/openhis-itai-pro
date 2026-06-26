package com.openhis.chs.application.catalog.support;

import org.apache.commons.lang3.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/** 日期时间解析（避免 chs-application 依赖 health-domain-shared）。 */
public final class ChsDateTimeParseUtil {

    private static final DateTimeFormatter[] FORMATTERS = {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd")
    };

    private ChsDateTimeParseUtil() {
    }

    public static LocalDateTime parseLocalDateTime(String s) {
        if (StringUtils.isBlank(s)) {
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
