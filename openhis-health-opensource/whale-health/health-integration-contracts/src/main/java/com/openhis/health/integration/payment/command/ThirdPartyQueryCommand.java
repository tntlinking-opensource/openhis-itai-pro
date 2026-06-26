/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.payment.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 查单 G（{@code queryOrder}）。 */
@Data
@Accessors(chain = true)
public class ThirdPartyQueryCommand {

    /** 商户订单号。 */
    private String merTradeNo;
    /** 银联/BPC 交易号。 */
    private String tradeNo;
    /** HIS 支付单主键。 */
    private Long paymentId;
}
