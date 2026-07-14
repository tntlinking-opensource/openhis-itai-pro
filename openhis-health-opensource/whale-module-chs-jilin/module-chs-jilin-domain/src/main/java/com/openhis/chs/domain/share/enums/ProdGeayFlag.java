/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.share.enums;

/**
 * 进销存【3503】采购上传字段 {@code prod_geay_flag}（是否赠品入库）。
 *
 * <p>正常采购入库传 {@link #NO}；赠品场景按局点文档扩展。</p>
 */
public enum ProdGeayFlag implements ChsCodeEnum {

    /** 非赠品（code 0；当前业务唯一使用值） */
    NO("0", "非赠品");

    private final String code;
    private final String label;

    ProdGeayFlag(String code, String label) {
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
