/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.share.enums;

/**
 * 1101/2201 等报文字段 {@code mdtrt_cert_type}（就诊凭证类型）。
 *
 * <p>表示患者用什么介质读卡：电子凭证、身份证、社保卡；决定 1101 入参哪些字段必填。</p>
 */
public enum MdtrtCertType implements ChsCodeEnum {

    /** 医保电子凭证（code 01；吉林要求带 psnName） */
    ELECTRONIC("01", "电子凭证"),
    /** 居民身份证（code 02） */
    ID_CARD("02", "居民身份证"),
    /** 社会保障卡（code 03；需 cardSn） */
    SOCIAL_SECURITY_CARD("03", "社会保障卡");

    private final String code;
    private final String label;

    MdtrtCertType(String code, String label) {
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
