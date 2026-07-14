/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.domain.scan.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** applyClientScanResult 结果。 */
@Data
@Accessors(chain = true)
public class ApplyClientScanResult {

    private boolean success;
    private Long orderId;
    private String merTradeNo;
    private String tradeNo;
    private String payType;
    private String channelCode;
    private String respMsg;
}
