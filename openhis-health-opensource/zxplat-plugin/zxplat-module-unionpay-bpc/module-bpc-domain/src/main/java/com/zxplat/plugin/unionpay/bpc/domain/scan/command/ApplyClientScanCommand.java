/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.domain.scan.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 确认 Win 被扫回传（applyClientScanResult）。 */
@Data
@Accessors(chain = true)
public class ApplyClientScanCommand {

    private Long paymentId;
    private Integer detailIndex;
    private BigDecimal amount;
    private String merTradeNo;
    private String tradeNo;
    private String payType;
    private String respCode;
    private String respMsg;
    private Long txnAmtFen;
    private Long operatorId;
    private Long orgId;
}
