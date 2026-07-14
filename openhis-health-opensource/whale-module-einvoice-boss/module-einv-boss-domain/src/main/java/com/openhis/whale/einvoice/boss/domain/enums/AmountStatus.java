/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.einvoice.boss.domain.enums;

/**
 * 电子票据单据金额一致性状态（einv_boss_document.amount_status）。
 */
public enum AmountStatus {

    /** 明细合计等于开票总额。 */
    MATCH("MATCH"),

    /** 明细合计与开票总额不一致。 */
    MISMATCH("MISMATCH"),

    /** 经人工调额后仍待复核或已对齐。 */
    ADJUSTED("ADJUSTED");

    private final String code;

    AmountStatus(String code) {
        this.code = code;
    }

    /**
     * 落库使用的状态码。
     *
     * @return amount_status 字符串
     */
    public String getCode() {
        return code;
    }
}
