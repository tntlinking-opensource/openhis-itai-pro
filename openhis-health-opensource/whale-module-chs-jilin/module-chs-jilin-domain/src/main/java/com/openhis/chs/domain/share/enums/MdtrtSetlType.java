/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.share.enums;

/**
 * 进销存 3505 销售上传字段 {@code mdtrt_setl_type}（就诊结算类型）。
 *
 * <p>区分本条销售明细按医保记账还是纯自费；与 {@link HiFeesetlType} 组合使用。</p>
 */
public enum MdtrtSetlType implements ChsCodeEnum {

    /** 医保结算（code 1） */
    INSURANCE("1", "医保"),
    /** 自费结算（code 2） */
    SELF_PAY("2", "自费");

    private final String code;
    private final String label;

    MdtrtSetlType(String code, String label) {
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
