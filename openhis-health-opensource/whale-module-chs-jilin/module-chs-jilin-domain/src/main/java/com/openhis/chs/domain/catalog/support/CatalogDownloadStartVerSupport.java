/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.catalog.support;

import static com.openhis.whale.core.application.util.StringUtils.isEmpty;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import com.openhis.chs.domain.share.constants.ChsCatalogConstants;

/**
 * 13xx 文件型目录下载起{@code data.ver}（对齐国家平台接口说明与 OpenHIS 客户端） */
public final class CatalogDownloadStartVerSupport {

    /** 工具类禁止实例化 */
    private CatalogDownloadStartVerSupport() {
    }

    /**
     * 无游标、全量重拉时的默认起点     *
     * @param infno     交易编号
     * @param verBranch 1305 版本分支：{@code F002} / {@code F003}，其按 infno 可空 */
    public static String defaultInitialVer(String infno, String verBranch) {
        if (isEmpty(infno)) {
            return ChsCatalogConstants.DEFAULT_CATALOG_DOWNLOAD_VER;
        }
        return switch (infno.trim()) {
            case "1305" -> resolve1305Branch(verBranch);
            case "1308" -> ChsCatalogConstants.INITIAL_VER_SURGERY;
            default -> ChsCatalogConstants.DEFAULT_CATALOG_DOWNLOAD_VER;
        };
    }

    /**
     * 解析本次下载起点：显startLocalVer &gt; 游标 &gt; 默认起点 */
    public static String resolveStartVer(
            String infno,
            String verBranch,
            boolean forceStartFromZero,
            String startLocalVer,
            String cursorLocalVer) {
        if (isNotEmpty(startLocalVer)) {
            return startLocalVer.trim();
        }
        if (forceStartFromZero) {
            return defaultInitialVer(infno, verBranch);
        }
        if (isNotEmpty(cursorLocalVer)) {
            return cursorLocalVer.trim();
        }
        return defaultInitialVer(infno, verBranch);
    }

    /** 1305 版本分支 F002/F003 解析为规范初始 ver */
    private static String resolve1305Branch(String verBranch) {
        if (isEmpty(verBranch)) {
            return ChsCatalogConstants.INITIAL_VER_SERVICE_F002;
        }
        String branch = verBranch.trim().toUpperCase();
        if (ChsCatalogConstants.INITIAL_VER_SERVICE_F003.equals(branch)) {
            return ChsCatalogConstants.INITIAL_VER_SERVICE_F003;
        }
        return ChsCatalogConstants.INITIAL_VER_SERVICE_F002;
    }
}
