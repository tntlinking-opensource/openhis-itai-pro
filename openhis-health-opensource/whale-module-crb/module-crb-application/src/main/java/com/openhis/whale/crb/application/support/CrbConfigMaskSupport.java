/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.application.support;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 前置配置脱敏展示辅助（监控/探测只读视图）。
 */
public final class CrbConfigMaskSupport {

    private CrbConfigMaskSupport() {
    }

    /**
     * 脱敏 baseUrl：保留 scheme 与 host，路径以 *** 代替。
     *
     * @param baseUrl 原始 baseUrl
     * @return 脱敏串；空则返回空串
     */
    public static String maskBaseUrl(String baseUrl) {
        String trimmed = trimToNull(baseUrl);
        if (trimmed == null) {
            return "";
        }
        int schemeEnd = trimmed.indexOf("://");
        if (schemeEnd < 0) {
            return "***";
        }
        int pathStart = trimmed.indexOf('/', schemeEnd + 3);
        if (pathStart < 0) {
            return trimmed;
        }
        return trimmed.substring(0, pathStart) + "/***";
    }

    /**
     * 脱敏 license/secret：仅展示末 4 位。
     *
     * @param license 原始 license
     * @return 脱敏串；空则返回空串
     */
    public static String maskLicense(String license) {
        String trimmed = trimToNull(license);
        if (trimmed == null) {
            return "";
        }
        if (trimmed.length() <= 4) {
            return "****";
        }
        return "****" + trimmed.substring(trimmed.length() - 4);
    }
}
