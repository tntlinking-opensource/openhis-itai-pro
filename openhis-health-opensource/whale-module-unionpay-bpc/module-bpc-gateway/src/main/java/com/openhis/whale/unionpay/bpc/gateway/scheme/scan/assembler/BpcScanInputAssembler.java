/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.gateway.scheme.scan.assembler;

import com.openhis.whale.unionpay.bpc.gateway.scheme.scan.model.BpcScanRequest;

/** 被扫 Command → HTTPS 请求体（占位）。 */
public final class BpcScanInputAssembler {

    private BpcScanInputAssembler() {
    }

    public static BpcScanRequest toRequest(String merTradeNo, long txnAmtFen, String mid, String tid) {
        return new BpcScanRequest()
                .setMerTradeNo(merTradeNo)
                .setTxnAmtFen(txnAmtFen)
                .setMid(mid)
                .setTid(tid);
    }
}
