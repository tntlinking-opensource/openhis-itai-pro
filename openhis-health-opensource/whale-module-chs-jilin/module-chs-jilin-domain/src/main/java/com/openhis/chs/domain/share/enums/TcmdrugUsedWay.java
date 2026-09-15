/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.chs.domain.share.enums;

/**
 * 国家医保上传字段 {@code tcmdrug_used_way}（中药使用方式）— <b>平台权威枚举</b>。
 *
 * <p><b>常见交易</b>：2204 门诊费用明细上传、2301 住院费用明细上传。</p>
 * <p><b>怎么用</b>：报文 {@code tcmdrug_used_way} 填 {@link #getCode()}。
 * 院内按同一处方中草药味数判定：大于 5 味传 {@link #COMPOUND}，小于等于 5 味传 {@link #SINGLE}；
 * 非中草药行不传本字段。</p>
 */
public enum TcmdrugUsedWay implements ChsCodeEnum {

    /** 复方（code 1；同一处方中草药味数大于 5） */
    COMPOUND("1", "复方"),
    /** 单方（code 2；同一处方中草药味数小于等于 5） */
    SINGLE("2", "单方");

    private final String code;
    private final String label;

    TcmdrugUsedWay(String code, String label) {
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
