/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.share.enums;

/**
 * 2203/2401/2402 诊断上传字段 {@code diag_type}（诊断类别）。
 *
 * <p>入院诊断常用 {@link #WESTERN}；出院诊断在 2402 中可能传 {@link #WESTERN} 等。</p>
 */
public enum DiagType implements ChsCodeEnum {

    /** 西医诊断（code 1） */
    WESTERN("1", "西医诊断"),
    /** 中医主病（code 2） */
    TCM_MAIN("2", "中医主病诊断"),
    /** 中医主证（code 3） */
    TCM_SYNDROME("3", "中医主证诊断");

    private final String code;
    private final String label;

    DiagType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getLabel() {
        return label;
    }
}
