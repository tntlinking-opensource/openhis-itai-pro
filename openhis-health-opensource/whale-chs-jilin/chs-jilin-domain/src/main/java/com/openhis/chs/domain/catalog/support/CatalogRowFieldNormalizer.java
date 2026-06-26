package com.openhis.chs.domain.catalog.support;

import org.apache.commons.lang3.StringUtils;

/**
 * 国家目录镜像入库前字段规范化（1301 TXT / 1312 JSON 共用）。
 */
public final class CatalogRowFieldNormalizer {

    private static final int CODE_FIELD_MAX_LEN = 16;

    private CatalogRowFieldNormalizer() {
    }

    /**
     * 收费项目等级：平台 1301 第 92 列常为「甲类/乙类/丙类」或「甲」，统一为 01/02/03。
     */
    public static String normalizeChrgitmLv(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String text = raw.trim();
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

    /** 有效标志：保留 0/1，其它取首字符（平台偶发带说明文字）。 */
    public static String normalizeValiFlag(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String text = raw.trim();
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

    public static String normalizeLmtUsedFlag(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String text = raw.trim();
        if ("是".equals(text) || "限制".equals(text) || "限制使用".equals(text)) {
            return "1";
        }
        if ("否".equals(text) || "非限制".equals(text)) {
            return "0";
        }
        return truncateCode(text);
    }

    private static String truncateCode(String text) {
        if (text.length() <= CODE_FIELD_MAX_LEN) {
            return text;
        }
        return text.substring(0, CODE_FIELD_MAX_LEN);
    }
}
