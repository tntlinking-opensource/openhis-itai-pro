package com.openhis.chs.domain.catalog.support;

import org.apache.commons.lang3.StringUtils;

/**
 * 目录下载版本号处理。
 *
 * <p>与 OpenHIS {@code StartUpdate} 一致：1301 入参 {@code data.ver} 使用 TXT「版本名称」整串，不做 20 位截断。
 * 游标表 {@code local_ver} 仍截断至 64（V5 列长）。</p>
 */
public final class CatalogDownloadVersionSupport {

    /** 游标表 local_ver 列长度（V5 扩列后） */
    public static final int CURSOR_VER_MAX_LEN = 64;

    private CatalogDownloadVersionSupport() {
    }

    /** 写入 yb_catalog_download_cursor.local_ver */
    public static String normalizeForCursor(String raw) {
        return truncate(raw, CURSOR_VER_MAX_LEN);
    }

    /**
     * 组装 1301 请求 {@code data.ver}（对齐 OpenHIS，原样传递 version_name，仅 trim）。
     */
    public static String normalizeForPlatformRequest(String raw) {
        if (StringUtils.isBlank(raw)) {
            return "0";
        }
        return raw.trim();
    }

    private static String truncate(String raw, int maxLen) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String text = raw.trim();
        if (text.length() <= maxLen) {
            return text;
        }
        return text.substring(0, maxLen);
    }
}
