/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.gateway.scheme.refund.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 被扫退款 HTTPS 请求体（占位）。 */
@Data
@Accessors(chain = true)
public class BpcRefundRequest {

    private String merTradeNo;
    private String refundMerTradeNo;
    private Long refundAmtFen;
}
