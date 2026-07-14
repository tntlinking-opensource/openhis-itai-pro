/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.domain.scan.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 被扫 C 预下单（prepareConsumerScan）。 */
@Data
@Accessors(chain = true)
public class PrepareConsumerScanCommand {

    private Long paymentId;
    private Integer detailIndex;
    private BigDecimal amount;
    private String fixmedinsCode;
    private Long operatorId;
    private Long orgId;
}
