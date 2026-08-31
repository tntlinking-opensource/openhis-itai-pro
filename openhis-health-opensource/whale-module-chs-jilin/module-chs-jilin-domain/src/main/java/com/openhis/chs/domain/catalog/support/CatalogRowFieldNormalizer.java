/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.support;

/** 国家目录镜像入库前字段规范化（1301 TXT / 1312 JSON 共用） */
public final class CatalogRowFieldNormalizer {

    private static final int CODE_FIELD_MAX_LEN = 16;

    /** 与 {@code yb_national_catalog_item} 列宽一致（V3 迁移） */
    public static final int LEN_HILIST_CODE = 30;
    public static final int LEN_HILIST_NAME = 200;
    public static final int LEN_SPEC = 200;
    public static final int LEN_DOSFORM = 200;
    public static final int LEN_APRVNO = 100;
    public static final int LEN_PRODENTP_NAME = 200;
    public static final int LEN_PINYIN = 100;
    public static final int LEN_MIN_PAC_CNT = 20;
    public static final int LEN_MIN_PACUNT = 30;
    public static final int LEN_MIN_PREPUNT = 30;
    public static final int LEN_MIN_PRCUNT = 50;

    /** 工具类禁止实例化 */
    private CatalogRowFieldNormalizer() {
    }

    /** 1301/1312 入库前截断至列宽；完整行仍保留在 {@code raw_json} */
    public static String truncateVarchar(String raw, int maxLen) {
        String text = NationalCatalog1301FieldExtractor.clean(raw);
        if (text == null) {
            return null;
        }
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen);
    }

    /** 收费项目等级：平台 1301 第 92 列常为「甲/乙类/丙类」或「甲」，统一为 01/02/03 */
    public static String normalizeChrgitmLv(String raw) {
        String text = NationalCatalog1301FieldExtractor.clean(raw);
        if (text == null) {
            return null;
        }
        return switch (text) {
            case "甲", "甲类" -> "01";
            case "乙", "乙类" -> "02";
            case "丙", "丙类" -> "03";
            case "1" -> "01";
            case "2" -> "02";
            case "3" -> "03";
            default -> truncateCode(text);
        };
    }

    /** 有效标志：保留 0/1，其它取首字符（平台偶发带说明文字） */
    public static String normalizeValiFlag(String raw) {
        String text = NationalCatalog1301FieldExtractor.clean(raw);
        if (text == null) {
            return null;
        }
        if ("有效".equals(text) || "1".equals(text)) {
            return "1";
        }
        if ("无效".equals(text) || "0".equals(text)) {
            return "0";
        }
        if (text.length() == 1) {
            return text;
        }
        char first = text.charAt(0);
        if (first == '0' || first == '1') {
            return String.valueOf(first);
        }
        return truncateCode(text);
    }

    /** 限制使用标志：是/限制/否 等文案统一为 0/1 或截断 */
    public static String normalizeLmtUsedFlag(String raw) {
        String text = NationalCatalog1301FieldExtractor.clean(raw);
        if (text == null) {
            return null;
        }
        if ("是".equals(text) || "限制".equals(text) || "限制使用".equals(text)) {
            return "1";
        }
        if ("否".equals(text) || "非限制".equals(text)) {
            return "0";
        }
        return truncateCode(text);
    }

    /** 代码类字段截断至 {@link #CODE_FIELD_MAX_LEN} */
    private static String truncateCode(String text) {
        if (text.length() <= CODE_FIELD_MAX_LEN) {
            return text;
        }
        return text.substring(0, CODE_FIELD_MAX_LEN);
    }
}
