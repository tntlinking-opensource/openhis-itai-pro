/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.unionpay.bpc.domain.query.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 查单 G（queryOrder）。 */
@Data
@Accessors(chain = true)
public class QueryBpcOrderCommand {

    private String merTradeNo;
    private String tradeNo;
    private Long paymentId;
}
