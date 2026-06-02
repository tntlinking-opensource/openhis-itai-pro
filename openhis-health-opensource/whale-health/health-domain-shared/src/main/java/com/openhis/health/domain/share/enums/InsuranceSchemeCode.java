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

package com.openhis.health.domain.share.enums;

/**
 * 医保对接方案码（技术路由，与 {@code fin_contract} / chs-contract 绑定）。
 */
public enum InsuranceSchemeCode {

    /** 国家平台 · 吉林省本地化（首期） */
    CHS_NATIONAL_JILIN,
    /** 国家平台 · 其他省（占位） */
    CHS_NATIONAL_GENERIC,
    /** 工伤联网（二期） */
    WORK_INJURY,
    /** 市级医保差异方案 */
    CHS_MUNICIPAL,
    /** 未对接，仅院内账户 */
    NONE;

    public String getCode() {
        return name();
    }

    public static InsuranceSchemeCode fromCode(String code) {
        if (code == null || code.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(code.trim());
        } catch (IllegalArgumentException ex) {
            return NONE;
        }
    }
}
