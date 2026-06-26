/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.payment.command;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 被扫 C 出站（通道 A · {@code consumerScan}）。 */
@Data
@Accessors(chain = true)
public class ThirdPartyScanCommand {

    /** HIS 支付单主键。 */
    private Long paymentId;
    /** 混合支付明细序号。 */
    private Integer detailIndex;
    /** 交易金额（元，scale=2）。 */
    private BigDecimal amount;
    /** 付款码。 */
    private String scanCode;
    /** 操作员。 */
    private Long operatorId;
    /** 院区（多 mid 路由）。 */
    private Long orgId;
}
