/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.payment.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 查单 G（{@code queryOrder}）。 */
@Data
@Accessors(chain = true)
public class ThirdPartyQueryExtensionIn {

    /** 商户订单号。 */
    private String merTradeNo;
    /** 银联/BPC 交易号。 */
    private String tradeNo;
    /** HIS 支付单主键。 */
    private Long paymentId;
}
