/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.unionpay.bpc.gateway.scheme.query.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 查单 HTTPS 请求体（占位）。 */
@Data
@Accessors(chain = true)
public class BpcQueryRequest {

    private String merTradeNo;
    private String tradeNo;
}
