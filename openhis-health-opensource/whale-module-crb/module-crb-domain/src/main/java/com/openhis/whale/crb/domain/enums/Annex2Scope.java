/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.enums;

/**
 * 附件二推送范围（持久化为 {@code annex2_scope} 列 code）。
 */
public enum Annex2Scope {

    /** 病原学检验。 */
    PATHOGEN("PATHOGEN"),

    /** 临床生化检验。 */
    CLIN_CHEM("CLIN_CHEM"),

    /** 药敏试验。 */
    DRUG_SENS("DRUG_SENS");

    private final String code;

    Annex2Scope(String code) {
        this.code = code;
    }

    /**
     * 持久化使用的字符串身份。
     *
     * @return 范围 code
     */
    public String getCode() {
        return code;
    }

    /**
     * 判断给定 code 是否属于附件二已知范围内。
     *
     * @param code 待校验 code
     * @return 在已知范围内则 true；null/blank/未知均为 false
     */
    public static boolean isInScope(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (Annex2Scope scope : values()) {
            if (scope.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
