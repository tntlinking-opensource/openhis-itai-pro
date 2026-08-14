/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.share.enums;

/**
 * 住院【2303】预结算字段 {@code mid_setl_flag}（是否中途结算）。
 *
 * <p>平台入参统一传 {@link #NO}（部分地市拒绝 {@code 1}）。
 * 本地 {@code MID_SETTLE} 仅表示试算跳过 2402，不映射为 {@link #YES}。</p>
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
