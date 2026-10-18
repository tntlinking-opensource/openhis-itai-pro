/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.share.enums;

/**
 * 1312/1901 等接口字段 {@code vali_flag}（记录是否有效）。
 *
 * <p>查询有效目录、有效字典项时传 {@link #VALID}。</p>
 */
public enum ValiFlag implements ChsCodeEnum {

    /** 有效（code 1；查询默认值） */
    VALID("1", "有效");

    private final String code;
    private final String label;

    ValiFlag(String code, String label) {
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
