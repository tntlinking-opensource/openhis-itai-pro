/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.report.adapter.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToEmpty;

import com.openhis.health.domain.share.utils.AdministrativeGenderDisplayUtil;
import com.openhis.whale.core.application.util.StringUtils;

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

    /**
     * null 安全 trim，与 whale-core {@code StringUtils.trimToEmpty} 一致。
     *
     * @param value 原始字符串
     * @return 非 null 字符串，空白归一为 {@code ""}
     */
    public static String trimToEmpty(String value) {
        return com.openhis.whale.core.application.util.StringUtils.trimToEmpty(value);
    }

    /**
     * 空白时展示破折号，供报表单元格占位。
     *
     * @param value 单元格原始值
     * @return 非空白 trim 后原文，否则 {@code "—"}
     */
    public static String trimToDash(String value) {
        if (StringUtils.isNotEmpty(value)) {
            return value.trim();
        }
        return "—";
    }

    /**
     * 性别枚举 code；null 归一为 empty 供下游展示工具处理。
     *
     * @param genderEnum 行政性别枚举 code
     * @return 非 null code 或空串
     */
    public static String genderCodeOrEmpty(String genderEnum) {
        if (genderEnum != null) {
            return genderEnum;
        }
        return "";
    }

    /**
     * 行政性别 code → 展示文案；未命中枚举时回落为 code 或空串。
     *
     * @param genderEnum 行政性别枚举 code
     * @return 中文展示文案
     */
    public static String toAdministrativeGenderText(String genderEnum) {
        return AdministrativeGenderDisplayUtil.toDisplayText(genderEnum);
    }

    /**
     * null 金额视为 0，避免聚合 NPE。
     *
     * @param value 金额
     * @return 非 null 金额
     */
    public static BigDecimal zeroIfNull(BigDecimal value) {
        if (value != null) {
            return value;
        }
        return BigDecimal.ZERO;
    }

    /**
     * 任意对象转字符串；null 返回空串。
     *
     * @param raw 任意对象
     * @return {@link String#valueOf} 结果或空串
     */
    public static String stringOrEmpty(Object raw) {
        if (raw != null) {
            return String.valueOf(raw);
        }
        return "";
    }

    /**
     * LocalDateTime 默认格式；null 显示破折号。
     *
     * @param value 时刻
     * @return ISO 本地字符串或 {@code "—"}
     */
    public static String formatDateTimeOrDash(LocalDateTime value) {
        if (value != null) {
            return value.toString();
        }
        return "—";
    }

    /**
     * 按指定 formatter 格式化；null 显示破折号。
     *
     * @param formatter 日期时间格式
     * @param value     时刻
     * @return 格式化结果或 {@code "—"}
     */
    public static String formatDateTimeOrDash(DateTimeFormatter formatter, LocalDateTime value) {
        if (value != null) {
            return formatter.format(value);
        }
        return "—";
    }

    /**
     * 按 formatter 格式化；null 返回空串（表头/副标题用）。
     *
     * @param formatter 日期时间格式
     * @param value     时刻
     * @return 格式化结果或空串
     */
    public static String formatDateTimeOrEmpty(DateTimeFormatter formatter, LocalDateTime value) {
        if (value == null) {
            return "";
        }
        return formatter.format(value);
    }

    /**
     * 主值缺失时用 fallback 时间格式化（打印占位）。
     *
     * @param formatter 日期时间格式
     * @param value     主时刻
     * @param fallback  主值为 null 时的替代时刻
     * @return 格式化后的时间串
     */
    public static String formatDateTime(DateTimeFormatter formatter, LocalDateTime value, LocalDateTime fallback) {
        LocalDateTime effective = value;
        if (effective == null) {
            effective = fallback;
        }
        return formatter.format(effective);
    }

    /**
     * null 列表归为空列表，避免 NPE。
     *
     * @param rows 原始列表
     * @param <T>  元素类型
     * @return 原列表或空不可变列表
     */
    public static <T> List<T> listOrEmpty(List<T> rows) {
        if (rows != null) {
            return rows;
        }
        return List.of();
    }

    /**
     * null Long 视为 0（聚合主键占位）。
     *
     * @param value Long 值
     * @return 非 null long
     */
    public static long longOrZero(Long value) {
        if (value != null) {
            return value;
        }
        return 0L;
    }

    /**
     * 优先取 primary，缺失时用 fallback。
     *
     * @param primary  优先值
     * @param fallback 回退值
     * @return 第一个非 null 值
     */
    public static Long coalesceLong(Long primary, Long fallback) {
        if (primary != null) {
            return primary;
        }
        return fallback;
    }

    /**
     * 正数展示数字串，否则破折号（ID 列常用）。
     *
     * @param value long 值
     * @return 正数时十进制串，否则 {@code "—"}
     */
    public static String formatPositiveLongOrDash(long value) {
        if (value > 0) {
            return String.valueOf(value);
        }
        return "—";
    }

    /**
     * 正数 Long 展示；null 或非正数显示破折号。
     *
     * @param value Long 值
     * @return 正数时十进制串，否则 {@code "—"}
     */
    public static String formatLongOrDash(Long value) {
        if (value != null && value > 0) {
            return String.valueOf(value);
        }
        return "—";
    }

    /**
     * 任意对象 toString；null 显示破折号。
     *
     * @param value 任意对象
     * @return toString 或 {@code "—"}
     */
    public static String formatObjectOrDash(Object value) {
        if (value != null) {
            return value.toString();
        }
        return "—";
    }

    /**
     * 列表首元素；空列表返回破折号。
     *
     * @param items 字符串列表
     * @return 首元素或 {@code "—"}
     */
    public static String firstOrDash(List<String> items) {
        if (items != null && !items.isEmpty()) {
            return items.get(0);
        }
        return "—";
    }

    /**
     * 空格拼接多段文案；无内容时破折号。
     *
     * @param parts 文案段列表
     * @return 拼接结果或 {@code "—"}
     */
    public static String joinPartsOrDash(List<String> parts) {
        if (parts == null || parts.isEmpty()) {
            return "—";
        }
        return String.join(" ", parts);
    }

    /**
     * 展示名优先，缺失用 fallback（科室/病区名回填）。
     *
     * @param display  展示名
     * @param fallback 回退名
     * @return 非空 trim 后的展示名或 fallback
     */
    public static String displayOrFallback(String display, String fallback) {
        if (StringUtils.isNotEmpty(display)) {
            return display.trim();
        }
        return fallback.trim();
    }

    /**
     * 医保清算状态中文展示。
     *
     * @param cleared 是否已清算
     * @return {@code 已清算} 或 {@code 未清算}
     */
    public static String ybClearText(Boolean cleared) {
        if (Boolean.TRUE.equals(cleared)) {
            return "已清算";
        }
        return "未清算";
    }

    /**
     * 皮试标识：true 显示「皮试」。
     *
     * @param skinTestFlag 是否皮试
     * @return {@code 皮试} 或空串
     */
    public static String skinTestText(Boolean skinTestFlag) {
        if (Boolean.TRUE.equals(skinTestFlag)) {
            return "皮试";
        }
        return "";
    }

    /**
     * 医嘱类别：药品 vs 诊疗。
     *
     * @param orderType 医嘱类型 code
     * @return {@code 药品} 或 {@code 诊疗}
     */
    public static String orderKindLabel(String orderType) {
        if ("MED_REQUEST".equalsIgnoreCase(trimToEmpty(orderType))) {
            return "药品";
        }
        return "诊疗";
    }

    /**
     * 中药处方后缀：t=中药，w=西药。
     *
     * @param herbal 是否中药处方
     * @return {@code t} 或 {@code w}
     */
    public static String herbalRxSuffix(boolean herbal) {
        if (herbal) {
            return "t";
        }
        return "w";
    }

    /**
     * 病区 + 床号拼接；仅床号时单独展示。
     *
     * @param ward    病区名
     * @param bedName 床号
     * @return 拼接标签
     */
    public static String wardBedLabel(String ward, String bedName) {
        if (StringUtils.isNotEmpty(bedName)) {
            if (StringUtils.isNotEmpty(ward)) {
                return ward.trim() + " " + bedName.trim();
            }
            return bedName.trim();
        }
        return ward;
    }

    /**
     * 数值与单位拼接；无单位时仅数值。
     *
     * @param value 数值
     * @param unit  单位文案
     * @return 带单位的展示串
     */
    public static String valueWithUnit(Object value, String unit) {
        if (StringUtils.isNotEmpty(unit)) {
            return value + " " + unit.trim();
        }
        return String.valueOf(value);
    }

    /**
     * 科室床位展示；空白时破折号。
     *
     * @param deptBed 科室床位拼接串
     * @return 原文或 {@code "—"}
     */
    public static String deptBedOrDash(String deptBed) {
        if (StringUtils.isNotEmpty(deptBed)) {
            return deptBed;
        }
        return "—";
    }
}
