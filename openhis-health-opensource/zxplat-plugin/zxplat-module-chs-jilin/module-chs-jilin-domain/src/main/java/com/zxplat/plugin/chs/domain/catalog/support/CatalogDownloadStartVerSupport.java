/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.catalog.support;

import static com.zxplat.framework.core.application.util.StringUtils.isEmpty;
import static com.zxplat.framework.core.application.util.StringUtils.isNotEmpty;
import com.zxplat.plugin.chs.domain.share.constants.ChsCatalogConstants;

/**
 * 13xx 文件型目录下载起 {@code data.ver}（对齐国家平台接口说明与历史 客户端）。
 *
 * <p>1305 存在 F002 / F003 两条独立版本链：游标须按分支前缀匹配，不得混用。</p>
 */
public final class CatalogDownloadStartVerSupport {

    /** 工具类禁止实例化 */
    private CatalogDownloadStartVerSupport() {
    }

    /**
     * 无游标、全量重拉时的默认起点
     *
     * @param infno     交易编号
     * @param verBranch 1305 版本分支：{@code F002} / {@code F003}，其它 infno 可空
     */
    public static String defaultInitialVer(String infno, String verBranch) {
        if (isEmpty(infno)) {
            return ChsCatalogConstants.DEFAULT_CATALOG_DOWNLOAD_VER;
        }
        return switch (infno.trim()) {
            case "1305" -> canonical1305Branch(verBranch);
            case "1308" -> ChsCatalogConstants.INITIAL_VER_SURGERY;
            default -> ChsCatalogConstants.DEFAULT_CATALOG_DOWNLOAD_VER;
        };
    }

    /**
     * 解析本次下载起点：显式 startLocalVer &gt; 全量重拉默认 &gt; 游标 &gt; 默认起点。
     *
     * <p>调用方传入的 {@code cursorLocalVer} 须已按分支过滤（1305）；本方法不再二次校验前缀。</p>
     */
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

    /**
     * 1305 版本分支规范值：{@code F003} 或默认 {@code F002}。
     *
     * @param verBranch 前端/入参分支，可空
     */
    public static String canonical1305Branch(String verBranch) {
        if (isEmpty(verBranch)) {
            return ChsCatalogConstants.INITIAL_VER_SERVICE_F002;
        }
        String branch = verBranch.trim().toUpperCase();
        if (ChsCatalogConstants.INITIAL_VER_SERVICE_F003.equals(branch)) {
            return ChsCatalogConstants.INITIAL_VER_SERVICE_F003;
        }
        return ChsCatalogConstants.INITIAL_VER_SERVICE_F002;
    }

    /**
     * 判断 local_ver 是否属于指定 1305 分支（前缀匹配：{@code F002} / {@code F002_…}）。
     */
    public static boolean matches1305Branch(String localVer, String verBranch) {
        if (isEmpty(localVer)) {
            return false;
        }
        String branch = canonical1305Branch(verBranch);
        String ver = localVer.trim().toUpperCase();
        return ver.equals(branch) || ver.startsWith(branch + "_");
    }
}
