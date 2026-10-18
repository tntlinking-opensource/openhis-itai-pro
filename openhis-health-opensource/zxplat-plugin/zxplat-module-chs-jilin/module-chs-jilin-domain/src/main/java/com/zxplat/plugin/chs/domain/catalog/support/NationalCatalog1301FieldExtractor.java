/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.support;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;

/**
 * 从 1301 TXT 列 / 数组型 {@code raw_json} 抽出展示字段，并消化平台占位符。
 *
 * <p>下标对齐规范列号 −1。通用名为 {@code --}/{@code null} 时回退注册名称、商品名；
 * 开始/结束日期为空时回退第 96/97 列生效/失效时间。</p>
 */
public final class NationalCatalog1301FieldExtractor {

    /** 第 2 列药品商品名 */
    public static final int IDX_TRADE_NAME = 1;

    /** 第 4 列药品通用名 */
    public static final int IDX_GENERIC_NAME = 3;

    /** 第 8 列注册名称 */
    public static final int IDX_REG_NAME = 7;

    /** 第 37 列开始日期 */
    public static final int IDX_BEGNDATE = 36;

    /** 第 38 列结束日期 */
    public static final int IDX_ENDDATE = 37;

    /** 第 51 列拼音助记码 */
    public static final int IDX_PINYIN = 50;

    /** 第 58 列限制使用标志 */
    public static final int IDX_LMT_USED_FLAG = 57;

    /** 第 92 列国家医保药品目录甲乙类标识 */
    public static final int IDX_CHRGITM_LV = 91;

    /** 第 96 列生效时间 */
    public static final int IDX_EFFECTIVE_TIME = 95;

    /** 第 97 列失效时间 */
    public static final int IDX_EXPIRE_TIME = 96;

    private NationalCatalog1301FieldExtractor() {
    }

    /**
     * @param hilistName  目录名称（通用名 → 注册名称 → 商品名）
     * @param chrgitmLv   甲乙类原文
     * @param lmtUsedFlag 限制使用标志
     * @param begndate    开始/生效时间原文
     * @param enddate     结束/失效时间原文
     * @param pinyin      拼音助记码
     */
    public record DisplayFields(
            String hilistName,
            String chrgitmLv,
            String lmtUsedFlag,
            String begndate,
            String enddate,
            String pinyin) {
    }

    /** 空结果。 */
    public static final DisplayFields EMPTY =
        new DisplayFields(null, null, null, null, null, null);

    /**
     * 从 TAB 拆分列解析。
     *
     * @param cols 0-based 列
     * @return 展示字段；never null
     */
    public static DisplayFields fromColumns(String[] cols) {
        if (cols == null || cols.length == 0) {
            return EMPTY;
        }
        return new DisplayFields(
            resolveName(cols),
            clean(column(cols, IDX_CHRGITM_LV)),
            clean(column(cols, IDX_LMT_USED_FLAG)),
            firstDate(column(cols, IDX_BEGNDATE), column(cols, IDX_EFFECTIVE_TIME)),
            firstDate(column(cols, IDX_ENDDATE), column(cols, IDX_EXPIRE_TIME)),
            clean(column(cols, IDX_PINYIN)));
    }

    /**
     * 名称回退：通用名 → 注册名称 → 商品名（商品名「无」视为空）。
     *
     * @param cols 0-based 列
     * @return 可用名称；皆空时 null
     */
    public static String resolveName(String[] cols) {
        String generic = clean(column(cols, IDX_GENERIC_NAME));
        if (generic != null) {
            return generic;
        }
        String registered = clean(column(cols, IDX_REG_NAME));
        if (registered != null) {
            return registered;
        }
        return cleanTradeName(column(cols, IDX_TRADE_NAME));
    }

    /**
     * 国家局 TXT 空字段常写作 {@code null}/{@code --}，不能当有效名称或等级入库。
     *
     * @param raw 原始单元格
     * @return 清洗后文本；占位符返回 null
     */
    public static String clean(String raw) {
        if (isEmpty(raw)) {
            return null;
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return null;
        }
        if ("null".equalsIgnoreCase(text) || "--".equals(text) || "-".equals(text)) {
            return null;
        }
        return text;
    }

    private static String cleanTradeName(String raw) {
        String text = clean(raw);
        if (text == null || "无".equals(text)) {
            return null;
        }
        return text;
    }

    private static String firstDate(String primary, String fallback) {
        String first = clean(primary);
        if (first != null) {
            return first;
        }
        return clean(fallback);
    }

    private static String column(String[] cols, int index) {
        if (index < 0 || index >= cols.length) {
            return null;
        }
        return cols[index];
    }
}
