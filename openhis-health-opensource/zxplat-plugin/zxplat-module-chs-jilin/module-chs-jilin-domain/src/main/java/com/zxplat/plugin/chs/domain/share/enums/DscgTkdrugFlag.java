/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.share.enums;

/**
 * 2301 费用明细上传字段 {@code dscg_tkdrug_flag}（是否出院带药）。
 *
 * <p>普通在院计费默认 {@link #NO}；出院带药场景按业务传 {@code 1}（待扩展枚举项）。</p>
 */
public enum DscgTkdrugFlag implements ChsCodeEnum {

    /** 非出院带药（code 0；默认） */
    NO("0", "否");

    private final String code;
    private final String label;

    DscgTkdrugFlag(String code, String label) {
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
