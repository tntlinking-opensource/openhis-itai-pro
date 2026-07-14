/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.share.enums;

/**
 * 进销存 3505/3502 等上传字段 {@code trdn_flag}（是否拆零发药）。
 *
 * <p>整盒发药传 {@link #WHOLE}，拆零发药传 {@link #SPLIT}；与追溯码采集策略联动。</p>
 */
public enum TrdnFlag implements ChsCodeEnum {

    /** 整盒（code 0） */
    WHOLE("0", "整盒"),
    /** 拆零（code 1） */
    SPLIT("1", "拆零");

    private final String code;
    private final String label;

    TrdnFlag(String code, String label) {
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
     * 按编码解析；无效返回 {@code null}。
     */
    public static TrdnFlag fromCodeOptional(String code) {
        if (code == null) {
            return null;
        }
        String trimmed = code.trim();
        for (TrdnFlag item : values()) {
            if (item.code.equals(trimmed)) {
                return item;
            }
        }
        return null;
    }
}
