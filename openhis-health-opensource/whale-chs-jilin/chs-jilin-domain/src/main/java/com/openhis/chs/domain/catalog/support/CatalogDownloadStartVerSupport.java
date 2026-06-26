/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.catalog.support;

import com.openhis.chs.api.ChsInfNo;
import com.openhis.chs.domain.catalog.ChsCatalogConstants;
import org.apache.commons.lang3.StringUtils;

/**
 * 13xx 文件型目录下载起始 {@code data.ver}（对齐国家平台接口说明与 OpenHIS 客户端）。
 */
public final class CatalogDownloadStartVerSupport {

    private CatalogDownloadStartVerSupport() {
    }

    /**
     * 无游标、全量重拉时的默认起点。
     *
     * @param infno     交易编号
     * @param verBranch 1305 版本分支：{@code F002} / {@code F003}，其它 infno 可空
     */
    public static String defaultInitialVer(String infno, String verBranch) {
        if (StringUtils.isBlank(infno)) {
            return ChsCatalogConstants.DEFAULT_CATALOG_DOWNLOAD_VER;
        }
        return switch (infno.trim()) {
            case ChsInfNo.MEDICAL_SERVICE_CATALOG_DOWNLOAD -> resolve1305Branch(verBranch);
            case ChsInfNo.SURGERY_CATALOG_DOWNLOAD -> ChsCatalogConstants.INITIAL_VER_SURGERY;
            default -> ChsCatalogConstants.DEFAULT_CATALOG_DOWNLOAD_VER;
        };
    }

    /**
     * 解析本次下载起点：显式 startLocalVer &gt; 游标 &gt; 默认起点。
     */
    public static String resolveStartVer(
            String infno,
            String verBranch,
            boolean forceStartFromZero,
            String startLocalVer,
            String cursorLocalVer) {
        if (StringUtils.isNotBlank(startLocalVer)) {
            return startLocalVer.trim();
        }
        if (forceStartFromZero) {
            return defaultInitialVer(infno, verBranch);
        }
        if (StringUtils.isNotBlank(cursorLocalVer)) {
            return cursorLocalVer.trim();
        }
        return defaultInitialVer(infno, verBranch);
    }

    private static String resolve1305Branch(String verBranch) {
        if (StringUtils.isBlank(verBranch)) {
            return ChsCatalogConstants.INITIAL_VER_SERVICE_F002;
        }
        String branch = verBranch.trim().toUpperCase();
        if (ChsCatalogConstants.INITIAL_VER_SERVICE_F003.equals(branch)) {
            return ChsCatalogConstants.INITIAL_VER_SERVICE_F003;
        }
        return ChsCatalogConstants.INITIAL_VER_SERVICE_F002;
    }
}
