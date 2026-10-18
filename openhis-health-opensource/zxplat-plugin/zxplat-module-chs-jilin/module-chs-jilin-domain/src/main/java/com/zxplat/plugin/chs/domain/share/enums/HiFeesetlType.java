/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.share.enums;

/**
 * 进销存 3505 销售上传字段 {@code hi_feesetl_type}（医保费用结算类型）。
 *
 * <p>在 {@link MdtrtSetlType#INSURANCE} 时进一步区分本地/异地/自费口径。</p>
 */
public enum HiFeesetlType implements ChsCodeEnum {

    /** 自费（code 0；与 mdtrt_setl_type=2 搭配） */
    SELF("0", "自费"),
    /** 本地医保（code 1） */
    LOCAL("1", "本地"),
    /** 异地医保（code 2） */
    REMOTE("2", "异地");

    private final String code;
    private final String label;

    HiFeesetlType(String code, String label) {
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
