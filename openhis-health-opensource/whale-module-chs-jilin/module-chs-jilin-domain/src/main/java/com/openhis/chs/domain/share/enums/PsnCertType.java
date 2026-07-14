/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.share.enums;

/**
 * 1101/3505 等报文字段 {@code psn_cert_type}（人员证件类型）。
 *
 * <p>与 {@link MdtrtCertType}（就诊凭证类型）不同：此处是<b>人员身份</b>证件种类，
 * 默认居民身份证 {@link #ID_CARD}。</p>
 */
public enum PsnCertType implements ChsCodeEnum {

    /** 居民身份证（code 01；缺省值） */
    ID_CARD("01", "居民身份证");

    private final String code;
    private final String label;

    PsnCertType(String code, String label) {
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
