/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.gateway.scheme.scan.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 被扫支付 HTTPS 请求体（占位）。 */
@Data
@Accessors(chain = true)
public class BpcScanRequest {

    private String merTradeNo;
    private Long txnAmtFen;
    private String mid;
    private String tid;
}
