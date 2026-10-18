/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.einvoice.boss.domain.enums;

/**
 * 财务日结核对日期维度。
 */
public enum ReconcileDateMode {

    /** 按业务发生日期（busDate / 支付 billDate）。 */
    BUS_DATE("BUS_DATE"),

    /** 按开票日期（issuedAt / ivcDate）。 */
    IVC_DATE("IVC_DATE");

    private final String code;

    ReconcileDateMode(String code) {
        this.code = code;
    }

    /**
     * 落库与 API 使用的模式码。
     *
     * @return 模式码
     */
    public String getCode() {
        return code;
    }
}
