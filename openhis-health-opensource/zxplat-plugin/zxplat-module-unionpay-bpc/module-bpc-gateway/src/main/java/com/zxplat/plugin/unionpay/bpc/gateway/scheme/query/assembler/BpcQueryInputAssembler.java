/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.gateway.scheme.query.assembler;

import com.zxplat.plugin.unionpay.bpc.gateway.scheme.query.model.BpcQueryRequest;

/** 查单 Command → HTTPS 请求体（占位）。 */
public final class BpcQueryInputAssembler {

    private BpcQueryInputAssembler() {
    }

    public static BpcQueryRequest toRequest(String merTradeNo, String tradeNo) {
        return new BpcQueryRequest()
                .setMerTradeNo(merTradeNo)
                .setTradeNo(tradeNo);
    }
}
