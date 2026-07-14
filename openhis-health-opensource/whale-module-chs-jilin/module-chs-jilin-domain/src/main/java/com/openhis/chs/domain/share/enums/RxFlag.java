/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.share.enums;

/**
 * 进销存 3501/3503/3505 上传字段 {@code rx_flag}（是否处方药）。
 *
 * <p>HIS 品种维护里的「处方药」布尔值，经 {@link #toCode(Boolean)} 转成平台 0/1。</p>
 */
public enum RxFlag implements ChsCodeEnum {

    /** 非处方药（code 0） */
    NO("0", "非处方药"),
    /** 处方药（code 1） */
    YES("1", "处方药");

    private final String code;
    private final String label;

    RxFlag(String code, String label) {
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

    /**
     * HIS 布尔 → 平台 rx_flag。
     *
     * @param rxFlag {@code true} 表示处方药
     * @return {@link #YES} 或 {@link #NO} 的 code
     */
    public static String toCode(Boolean rxFlag) {
        if (Boolean.TRUE.equals(rxFlag)) {
            return YES.getCode();
        }
        return NO.getCode();
    }
}
