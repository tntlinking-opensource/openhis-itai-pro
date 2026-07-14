/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 正式结算提交的支付明细 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "正式结算-支付明细")
public class PaymentDetailSubmitDto {

    /** 支付渠道编码。 */
    @Schema(description = "支付渠道编码")
    private String payChannelCode;

    /** 金额。 */
    @Schema(description = "金额")
    private BigDecimal amount;
}
