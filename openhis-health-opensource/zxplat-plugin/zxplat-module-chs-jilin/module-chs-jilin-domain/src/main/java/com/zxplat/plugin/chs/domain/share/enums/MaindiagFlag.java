/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.share.enums;

/**
 * 诊断上传字段 {@code maindiag_flag}（是否主诊断）。
 *
 * <p>每条就诊通常仅一条主诊断传 {@link #YES}。</p>
 */
public enum MaindiagFlag implements ChsCodeEnum {

    /** 主诊断（code 1） */
    YES("1", "主诊断");

    private final String code;
    private final String label;

    MaindiagFlag(String code, String label) {
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
