/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.upload.hqms;

/**
 * HQMS 导出渠道字面量（文件名、MIME、批上限、付费方式出站等）。
 */
public final class HqmsConstants {

    /** CSV 字符集名（对齐国基 HQMS {@code charset=GBK}）。 */
    public static final String CSV_CHARSET_NAME = "GBK";

    /** CSV MIME（GBK）。 */
    public static final String CSV_CONTENT_TYPE = "text/csv;charset=GBK";

    /** 文件名前缀（对齐国基 {@code hqmsts_}）。 */
    public static final String FILE_PREFIX = "hqmsts_";

    /** 文件后缀。 */
    public static final String FILE_SUFFIX = ".csv";

    /** 单次导出病案主键上限。 */
    public static final int MAX_EXPORT_CASE_RECORDS = 200;

    /** HQMS 空值占位（部分必填空字段国基写 {@code -}）。 */
    public static final String DASH = "-";

    /** 付费方式：其他。 */
    public static final String PAY_WAY_OTHER = "9";

    /** 禁止实例化。 */
    private HqmsConstants() {
    }

    /**
     * 行政区划码（国标省码前两位）→ HQMS {@code A23C}（RC036）。
     *
     * <p>真源：《HQMS 2.0 住院病案首页采集接口标准【2022版】》RC036；
     * 入参取籍贯级联第一级或任意下级码（按前两位定省）。</p>
     *
     * @param adminDivisionCode 如 {@code 220000} / {@code 220102}；空或无法识别→空串
     * @return RC036 值（{@code 1}～{@code 34}）；未识别空串
     */
    public static String toRc036FromAdminDivisionCode(String adminDivisionCode) {
        if (adminDivisionCode == null || adminDivisionCode.isBlank()) {
            return "";
        }
        String raw = adminDivisionCode.trim();
        // 单段内若含斜杠，只取第一段作省码前缀
        int slash = raw.indexOf('/');
        if (slash > 0) {
            raw = raw.substring(0, slash).trim();
        }
        String digits = leadingDigits(raw);
        if (digits.length() < 2) {
            return "";
        }
        String prefix = digits.substring(0, 2);
        if ("11".equals(prefix)) {
            return "1";
        }
        if ("12".equals(prefix)) {
            return "2";
        }
        if ("13".equals(prefix)) {
            return "3";
        }
        if ("14".equals(prefix)) {
            return "4";
        }
        if ("15".equals(prefix)) {
            return "5";
        }
        if ("21".equals(prefix)) {
            return "6";
        }
        if ("22".equals(prefix)) {
            return "7";
        }
        if ("23".equals(prefix)) {
            return "8";
        }
        if ("31".equals(prefix)) {
            return "9";
        }
        if ("32".equals(prefix)) {
            return "10";
        }
        if ("33".equals(prefix)) {
            return "11";
        }
        if ("34".equals(prefix)) {
            return "12";
        }
        if ("35".equals(prefix)) {
            return "13";
        }
        if ("36".equals(prefix)) {
            return "14";
        }
        if ("37".equals(prefix)) {
            return "15";
        }
        if ("41".equals(prefix)) {
            return "16";
        }
        if ("42".equals(prefix)) {
            return "17";
        }
        if ("43".equals(prefix)) {
            return "18";
        }
        if ("44".equals(prefix)) {
            return "19";
        }
        if ("45".equals(prefix)) {
            return "20";
        }
        if ("46".equals(prefix)) {
            return "21";
        }
        // 四川 51→22、重庆 50→23（与 RC036 序号一致，非国标码大小序）
        if ("51".equals(prefix)) {
            return "22";
        }
        if ("50".equals(prefix)) {
            return "23";
        }
        if ("52".equals(prefix)) {
            return "24";
        }
        if ("53".equals(prefix)) {
            return "25";
        }
        if ("54".equals(prefix)) {
            return "26";
        }
        if ("61".equals(prefix)) {
            return "27";
        }
        if ("62".equals(prefix)) {
            return "28";
        }
        if ("63".equals(prefix)) {
            return "29";
        }
        if ("64".equals(prefix)) {
            return "30";
        }
        if ("65".equals(prefix)) {
            return "31";
        }
        if ("71".equals(prefix)) {
            return "32";
        }
        if ("81".equals(prefix)) {
            return "33";
        }
        if ("82".equals(prefix)) {
            return "34";
        }
        return "";
    }

    /**
     * 截取前缀连续数字（区划码）。
     *
     * @param raw 原串
     * @return 数字前缀
     */
    private static String leadingDigits(String raw) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c < '0' || c > '9') {
                break;
            }
            out.append(c);
        }
        return out.toString();
    }

    /**
     * 院内首页付费方式码（1～9）→ HQMS {@code A46C}（RC032 口径）。
     *
     * <p>ZXPlat 语义：1职工 2居民 3新农合 4贫困救助 5商业 6全公费 7全自费 8其他社保 9其他。
     * 职工/居民/新农合出站带小数点细分码（1.1/2.1/3.1）。</p>
     *
     * @param homepagePayWayCode 首页 {@code pay_way_code}
     * @return HQMS 付费码；空则 {@link #PAY_WAY_OTHER}
     */
    public static String toHqmsPayWay(String homepagePayWayCode) {
        if (homepagePayWayCode == null || homepagePayWayCode.isBlank()) {
            return PAY_WAY_OTHER;
        }
        String code = homepagePayWayCode.trim();
        if ("1".equals(code)) {
            return "1.1";
        }
        if ("2".equals(code)) {
            return "2.1";
        }
        if ("3".equals(code)) {
            return "3.1";
        }
        if ("4".equals(code)) {
            return "4";
        }
        if ("5".equals(code)) {
            return "5";
        }
        if ("6".equals(code)) {
            return "6";
        }
        if ("7".equals(code)) {
            return "7";
        }
        if ("8".equals(code)) {
            return "8";
        }
        if ("9".equals(code)) {
            return PAY_WAY_OTHER;
        }
        return PAY_WAY_OTHER;
    }
}
