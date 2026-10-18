/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.mrms.upload.weistat;

/**
 * 卫统导出渠道字面量（文件名、MIME、批上限、付费方式出站等）。
 */
public final class WeistatN041Constants {

    /** CSV 字符集名（中文 Windows / 直报侧常用 GBK；与国基 HQMS 写出一致）。 */
    public static final String CSV_CHARSET_NAME = "GBK";

    /** CSV MIME（GBK）。 */
    public static final String CSV_CONTENT_TYPE = "text/csv;charset=GBK";

    /** 文件名前缀（表号 N041）。 */
    public static final String FILE_PREFIX = "N041_";

    /** 文件后缀。 */
    public static final String FILE_SUFFIX = ".csv";

    /** 单次导出病案主键上限（防一次 loadPage 过多）。 */
    public static final int MAX_EXPORT_CASE_RECORDS = 200;

    /** 付费方式：其他（N041 YLFKFS）。 */
    public static final String PAY_WAY_OTHER = "99";

    /** 禁止实例化。 */
    private WeistatN041Constants() {
    }

    /**
     * 院内首页付费方式码（1～9，见前端 PAY_WAY_OPTIONS）→ 卫统 N041 {@code YLFKFS}。
     *
     * <p>与国基老 HIS 的 1/2/4/6 码表不同；ZXPlat 已用国标语义 1～9，出站补零，9→99。</p>
     *
     * @param homepagePayWayCode 首页 {@code pay_way_code}
     * @return 两位 N041 码；空则 {@link #PAY_WAY_OTHER}
     */
    public static String toN041PayWay(String homepagePayWayCode) {
        if (homepagePayWayCode == null || homepagePayWayCode.isBlank()) {
            return PAY_WAY_OTHER;
        }
        String code = homepagePayWayCode.trim();
        if ("1".equals(code)) {
            return "01";
        }
        if ("2".equals(code)) {
            return "02";
        }
        if ("3".equals(code)) {
            return "03";
        }
        if ("4".equals(code)) {
            return "04";
        }
        if ("5".equals(code)) {
            return "05";
        }
        if ("6".equals(code)) {
            return "06";
        }
        if ("7".equals(code)) {
            return "07";
        }
        if ("8".equals(code)) {
            return "08";
        }
        if ("9".equals(code)) {
            return PAY_WAY_OTHER;
        }
        // 已是两位码则原样（兼容手填 01…）
        if (code.length() == 2) {
            return code;
        }
        return PAY_WAY_OTHER;
    }
}
