/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.application.report.support;

import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 打印/报表数据集文本与数值格式化（adapter 层共用，禁三元）。
 */
public final class ReportDatasetTextSupport {

    private ReportDatasetTextSupport() {
    }

    public static String trimToEmpty(String value) {
        if (value != null) {
            return value.trim();
        }
        return "";
    }

    public static String trimToDash(String value) {
        if (StringUtils.isNotBlank(value)) {
            return value.trim();
        }
        return "—";
    }

    public static String genderCodeOrEmpty(String genderEnum) {
        if (genderEnum != null) {
            return genderEnum;
        }
        return "";
    }

    public static BigDecimal zeroIfNull(BigDecimal value) {
        if (value != null) {
            return value;
        }
        return BigDecimal.ZERO;
    }

    public static String stringOrEmpty(Object raw) {
        if (raw != null) {
            return String.valueOf(raw);
        }
        return "";
    }

    public static String formatDateTimeOrDash(LocalDateTime value) {
        if (value != null) {
            return value.toString();
        }
        return "—";
    }

    public static String formatDateTimeOrDash(DateTimeFormatter formatter, LocalDateTime value) {
        if (value != null) {
            return formatter.format(value);
        }
        return "—";
    }

    public static String formatDateTimeOrEmpty(DateTimeFormatter formatter, LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return formatter.format(value);
    }

    public static String formatDateTime(DateTimeFormatter formatter, LocalDateTime value, LocalDateTime fallback) {
        LocalDateTime effective = value;
        if (effective == null) {
            effective = fallback;
        }
        return formatter.format(effective);
    }

    public static <T> List<T> listOrEmpty(List<T> rows) {
        if (rows != null) {
            return rows;
        }
        return List.of();
    }

    public static long longOrZero(Long value) {
        if (value != null) {
            return value;
        }
        return 0L;
    }

    public static Long coalesceLong(Long primary, Long fallback) {
        if (primary != null) {
            return primary;
        }
        return fallback;
    }

    public static String formatPositiveLongOrDash(long value) {
        if (value > 0) {
            return String.valueOf(value);
        }
        return "—";
    }

    public static String formatLongOrDash(Long value) {
        if (value != null && value > 0) {
            return String.valueOf(value);
        }
        return "—";
    }

    public static String formatObjectOrDash(Object value) {
        if (value != null) {
            return value.toString();
        }
        return "—";
    }

    public static String firstOrDash(List<String> items) {
        if (items != null && !items.isEmpty()) {
            return items.get(0);
        }
        return "—";
    }

    public static String joinPartsOrDash(List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return "—";
        }
        return String.join(" ", parts);
    }

    public static String displayOrFallback(String display, String fallback) {
        if (StringUtils.isNotBlank(display)) {
            return display.trim();
        }
        return fallback.trim();
    }

    public static String ybClearText(Boolean cleared) {
        if (Boolean.TRUE.equals(cleared)) {
            return "已清算";
        }
        return "未清算";
    }

    public static String skinTestText(Boolean skinTestFlag) {
        if (Boolean.TRUE.equals(skinTestFlag)) {
            return "皮试";
        }
        return "";
    }

    public static String orderKindLabel(String orderType) {
        if ("MED_REQUEST".equalsIgnoreCase(StringUtils.trimToEmpty(orderType))) {
            return "药品";
        }
        return "诊疗";
    }

    public static String herbalRxSuffix(boolean herbal) {
        if (herbal) {
            return "t";
        }
        return "w";
    }

    public static String wardBedLabel(String ward, String bedName) {
        if (StringUtils.isNotBlank(bedName)) {
            if (StringUtils.isNotBlank(ward)) {
                return ward.trim() + " " + bedName.trim();
            }
            return bedName.trim();
        }
        return ward;
    }

    public static String valueWithUnit(Object value, String unit) {
        if (StringUtils.isNotBlank(unit)) {
            return value + " " + unit.trim();
        }
        return String.valueOf(value);
    }

    public static String deptBedOrDash(String deptBed) {
        if (StringUtils.isNotBlank(deptBed)) {
            return deptBed;
        }
        return "—";
    }
}
