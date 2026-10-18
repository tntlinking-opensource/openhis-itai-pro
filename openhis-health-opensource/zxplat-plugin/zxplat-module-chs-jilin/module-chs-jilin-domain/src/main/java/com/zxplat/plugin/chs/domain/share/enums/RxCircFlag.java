/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.share.enums;

/**
 * 2301 费用明细上传字段 {@code rx_circ_flag}（是否外购处方）。
 *
 * <p>院内开具费用默认 {@link #NO}。</p>
 */
public enum RxCircFlag implements ChsCodeEnum {

    /** 非外购处方（code 0；默认） */
    NO("0", "否");

    private final String code;
    private final String label;

    RxCircFlag(String code, String label) {
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
