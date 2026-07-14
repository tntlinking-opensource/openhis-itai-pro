/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.gateway.scheme.refund.assembler;

import com.openhis.whale.unionpay.bpc.gateway.scheme.refund.model.BpcRefundRequest;

/** 退款 Command → HTTPS 请求体（占位）。 */
public final class BpcRefundInputAssembler {

    private BpcRefundInputAssembler() {
    }

    public static BpcRefundRequest toRequest(String merTradeNo, String refundMerTradeNo, Long refundAmtFen) {
        return new BpcRefundRequest()
                .setMerTradeNo(merTradeNo)
                .setRefundMerTradeNo(refundMerTradeNo)
                .setRefundAmtFen(refundAmtFen);
    }
}
