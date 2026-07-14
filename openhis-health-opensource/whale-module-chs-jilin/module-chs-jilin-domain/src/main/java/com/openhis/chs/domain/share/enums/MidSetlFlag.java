/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.share.enums;

/**
 * 住院【2303】预结算字段 {@code mid_setl_flag}（是否中途结算）。
 *
 * <p>在院期间分段结算传 {@link #YES}；出院全结传 {@link #NO}。</p>
 */
public enum MidSetlFlag implements ChsCodeEnum {

    /** 非中途结算 / 出院全结（code 0） */
    NO("0", "否"),
    /** 中途结算（code 1） */
    YES("1", "是");

    private final String code;
    private final String label;

    MidSetlFlag(String code, String label) {
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
