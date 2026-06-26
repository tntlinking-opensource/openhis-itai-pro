package com.openhis.whale.unionpay.bpc.domain.order;

import lombok.Getter;

/**
 * BPC 订单状态（落库 {@code upc_bpc_order.status}）。
 */
@Getter
public enum BpcOrderStatus {

    PENDING("PENDING"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
    REFUNDED("REFUNDED");

    private final String code;

    BpcOrderStatus(String code) {
        this.code = code;
    }
}
