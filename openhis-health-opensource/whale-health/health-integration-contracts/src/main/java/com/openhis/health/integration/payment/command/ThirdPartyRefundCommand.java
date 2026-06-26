/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.payment.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 退货 R 出站（通道 A · {@code refund}）。 */
@Data
@Accessors(chain = true)
public class ThirdPartyRefundCommand {

    /** HIS 支付单主键。 */
    private Long paymentId;
    /** 原 C 交易 merTradeNo。 */
    private String merTradeNo;
    /** 原 trade_no。 */
    private String tradeNo;
    /** 退货金额（元，scale=2）。 */
    private BigDecimal refundAmount;
    /** 操作员。 */
    private Long operatorId;
    /** 院区（多 mid 路由）。 */
    private Long orgId;
}
