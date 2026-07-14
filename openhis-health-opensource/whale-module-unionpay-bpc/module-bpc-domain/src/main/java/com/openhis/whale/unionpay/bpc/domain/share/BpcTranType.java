/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.domain.share;

import lombok.Getter;

/**
 * BPC 交易类型（落库 {@code upc_bpc_order.tran_type}）。
 */
@Getter
public enum BpcTranType {

    CONSUMER_SCAN("C"),
    QUERY("G"),
    REFUND("R");

    private final String code;

    BpcTranType(String code) {
        this.code = code;
    }
}
