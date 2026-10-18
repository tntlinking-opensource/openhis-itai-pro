/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.application.order.response;

import com.zxplat.plugin.unionpay.bpc.domain.order.entity.BpcOrder;

/** BPC 订单摘要（AppService 出参）。 */
public record BpcOrderSummaryResponse(
        Long orderId,
        Long paymentId,
        Integer detailIndex,
        String merTradeNo,
        String tradeNo,
        String tranType,
        Long txnAmtFen,
        Long paidAmtFen,
        String payType,
        String status,
        String respCode,
        String respMsg,
        String posNo) {

    public static BpcOrderSummaryResponse from(BpcOrder order) {
        return new BpcOrderSummaryResponse(
                order.getId(),
                order.getPaymentId(),
                order.getDetailIndex(),
                order.getMerTradeNo(),
                order.getTradeNo(),
                order.getTranType(),
                order.getTxnAmtFen(),
                order.getPaidAmtFen(),
                order.getPayType(),
                order.getStatus(),
                order.getRespCode(),
                order.getRespMsg(),
                order.getPosNo());
    }
}
