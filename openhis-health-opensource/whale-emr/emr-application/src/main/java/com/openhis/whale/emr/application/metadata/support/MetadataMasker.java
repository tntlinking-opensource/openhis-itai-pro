/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.whale.emr.application.metadata.support;

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
