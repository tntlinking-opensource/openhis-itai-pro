/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
