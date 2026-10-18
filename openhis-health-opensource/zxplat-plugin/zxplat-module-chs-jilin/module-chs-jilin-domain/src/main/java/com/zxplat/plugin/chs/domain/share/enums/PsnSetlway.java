/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.share.enums;

/**
 * 门诊/住院结算报文字段 {@code psn_setlway}（个人结算方式）。
 *
 * <p>2206/2207/2303/2304 的 {@code data.psn_setlway}；当前按项目结算固定 {@link #BY_ITEM}。</p>
 */
public enum PsnSetlway implements ChsCodeEnum {

    /** 按项目结算（code 01；系统默认） */
    BY_ITEM("01", "按项目结算");

    private final String code;
    private final String label;

    PsnSetlway(String code, String label) {
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
