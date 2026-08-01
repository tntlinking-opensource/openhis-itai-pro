/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.crb.domain.enums;

/**
 * 检验结果形态（持久化为 {@code result_shape} 列 code）。
 */
public enum LabResultShape {

    /** 定性结果。 */
    QUAL("QUAL"),

    /** 定量结果。 */
    QUANT("QUANT");

    private final String code;

    LabResultShape(String code) {
        this.code = code;
    }

    /**
     * 持久化使用的字符串身份。
     *
     * @return 形态 code
     */
    public String getCode() {
        return code;
    }

    /**
     * 判断给定 code 是否为已知形态枚举值。
     *
     * @param code 待校验 code
     * @return 是已知形态则 true
     */
    public static boolean isKnownCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        for (LabResultShape shape : values()) {
            if (shape.code.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
