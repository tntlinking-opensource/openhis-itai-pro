/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.share.enums;

/**
 * 结算报文字段 {@code acct_used_flag}（是否使用医保个人账户余额）。
 *
 * <p>2206/2207/2303/2304 预/正式结算；{@link #YES} 表示允许扣个账。</p>
 */
public enum AcctUsedFlag implements ChsCodeEnum {

    /** 不使用个账（code 0） */
    NO("0", "不使用"),
    /** 使用个账（code 1；默认） */
    YES("1", "使用");

    private final String code;
    private final String label;

    AcctUsedFlag(String code, String label) {
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
