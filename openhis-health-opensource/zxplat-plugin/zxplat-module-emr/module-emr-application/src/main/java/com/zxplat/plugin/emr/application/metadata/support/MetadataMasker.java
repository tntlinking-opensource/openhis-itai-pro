/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.emr.application.metadata.support;

import org.springframework.util.StringUtils;

/**
 * 元数据展示脱敏。
 */
public final class MetadataMasker {

    private MetadataMasker() {
    }

    public static String mask(String value, String maskRule) {
        if (!StringUtils.hasText(value) || !StringUtils.hasText(maskRule) || "NONE".equalsIgnoreCase(maskRule)) {
            if (value != null) {
                return value;
            }
            return "";
        }
        return switch (maskRule.toUpperCase()) {
            case "ID_CARD" -> maskIdCard(value);
            case "PHONE" -> maskPhone(value);
            default -> value;
        };
    }

    private static String maskIdCard(String value) {
        String v = value.trim();
        if (v.length() <= 8) {
            return "****";
        }
        return v.substring(0, 4) + "**********" + v.substring(v.length() - 4);
    }

    private static String maskPhone(String value) {
        String v = value.trim();
        if (v.length() <= 7) {
            return "***";
        }
        return v.substring(0, 3) + "****" + v.substring(v.length() - 4);
    }
}
