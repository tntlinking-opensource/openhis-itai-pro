/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 挂号支付明细行（多渠道分摊；与 fin_payment_detail 写模型对齐）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "支付明细")
public class PaymentDetailDto {

    /** 支付渠道编码（字典 pay_channel code）。 */
    @Schema(description = "支付渠道编码")
    private String payChannelCode;

    /** 本渠道实付金额。 */
    @Schema(description = "支付金额")
    private BigDecimal payAmount;

    /** 外部支付流水号（微信/支付宝等第三方单号，可空）。 */
    @Schema(description = "外部流水号")
    private String transactionNo;

    /** 患者账户主键 {@code fin_account.id}（预交金/信用账户等）。 */
    @Schema(description = "账户主键")
    private Long accountId;

    /** 账户编码（展示/对账用）。 */
    @Schema(description = "账户编码")
    private String accountCode;
}
