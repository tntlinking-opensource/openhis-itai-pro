package com.openhis.whale.unionpay.bpc.domain.order;

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
