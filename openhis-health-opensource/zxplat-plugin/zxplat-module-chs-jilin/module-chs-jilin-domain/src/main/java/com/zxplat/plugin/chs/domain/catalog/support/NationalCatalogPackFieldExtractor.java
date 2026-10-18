/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.support;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;

/**
 * 从 1301 数组型 {@code raw_json} / TXT 列抽出包装与计价单位。
 *
 * <p>下标对齐规范列号 −1：41/42/43/48 → 第 42/43/44/49 列。</p>
 */
public final class NationalCatalogPackFieldExtractor {

    /** 最小包装数量下标 */
    public static final int IDX_MIN_PAC_CNT = 41;

    /** 最小包装单位下标 */
    public static final int IDX_MIN_PACUNT = 42;

    /** 最小制剂单位下标 */
    public static final int IDX_MIN_PREPUNT = 43;

    /** 最小计价单位下标 */
    public static final int IDX_MIN_PRCUNT = 48;

    private NationalCatalogPackFieldExtractor() {
    }

    /**
     * @param minPacCnt  最小包装数量
     * @param minPacunt  最小包装单位
     * @param minPrepunt 最小制剂单位
     * @param minPrcunt  最小计价单位
     */
    public record PackFields(String minPacCnt, String minPacunt, String minPrepunt, String minPrcunt) {
        /** 四字段皆空 */
        public boolean isEmpty() {
            return minPacCnt == null && minPacunt == null && minPrepunt == null && minPrcunt == null;
        }
    }

    /** 空结果单例。 */
    public static final PackFields EMPTY = new PackFields(null, null, null, null);

    /**
     * 从入库 {@code raw_json} 解析；非数组 JSON（如 1312 对象）返回 {@link #EMPTY}。
     *
     * @param rawJson 原始 JSON 文本
     * @return 包装字段；never null
     */
    public static PackFields fromRawJson(String rawJson) {
        if (isEmpty(rawJson)) {
            return EMPTY;
        }
        String text = rawJson.trim();
        if (text.isEmpty() || text.charAt(0) != '[') {
            return EMPTY;
        }
        try {
            JSONArray arr = JSON.parseArray(text);
            if (arr == null || arr.isEmpty()) {
                return EMPTY;
            }
            return new PackFields(
                blankToNull(arr.getString(IDX_MIN_PAC_CNT)),
                blankToNull(arr.getString(IDX_MIN_PACUNT)),
                blankToNull(arr.getString(IDX_MIN_PREPUNT)),
                blankToNull(arr.getString(IDX_MIN_PRCUNT)));
        } catch (Exception ex) {
            return EMPTY;
        }
    }

    /**
     * 从 1301 TXT 拆分列解析（0-based）。
     *
     * @param cols TAB 分隔列
     * @return 包装字段；never null
     */
    public static PackFields fromColumns(String[] cols) {
        if (cols == null || cols.length <= IDX_MIN_PRCUNT) {
            return EMPTY;
        }
        return new PackFields(
            blankToNull(column(cols, IDX_MIN_PAC_CNT)),
            blankToNull(column(cols, IDX_MIN_PACUNT)),
            blankToNull(column(cols, IDX_MIN_PREPUNT)),
            blankToNull(column(cols, IDX_MIN_PRCUNT)));
    }

    private static String column(String[] cols, int index) {
        if (index < 0 || index >= cols.length) {
            return null;
        }
        return cols[index];
    }

    private static String blankToNull(String value) {
        if (isEmpty(value)) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }
}
