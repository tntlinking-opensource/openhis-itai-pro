/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.domain.refund.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** applyClientRefundResult 结果。 */
@Data
@Accessors(chain = true)
public class ApplyClientRefundResult {

    private boolean success;
    private Long orderId;
    private String merTradeNo;
    private String refundMerTradeNo;
    private String respMsg;
}
