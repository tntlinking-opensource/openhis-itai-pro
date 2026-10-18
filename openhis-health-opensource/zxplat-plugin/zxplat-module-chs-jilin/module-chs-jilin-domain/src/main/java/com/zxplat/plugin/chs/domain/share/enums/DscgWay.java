/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.share.enums;

/**
 * 住院【2402】出院办理字段 {@code dscg_way}（离院方式）。
 *
 * <p>无特殊离院方式时默认 {@link #ORDERED}（医嘱离院）。</p>
 */
public enum DscgWay implements ChsCodeEnum {

    /** 医嘱离院（code 1；默认） */
    ORDERED("1", "医嘱离院");

    private final String code;
    private final String label;

    DscgWay(String code, String label) {
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
