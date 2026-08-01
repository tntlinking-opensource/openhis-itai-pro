/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.physicalexam.financial.charge.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 体检正式结算支付明细提交 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体检结算支付明细")
public class PhysicalExamPaymentDetailSubmitDto {

    /** 支付渠道 code。 */
    @Schema(description = "支付渠道 code")
    private String payChannelCode;

    /** 实付金额。 */
    @Schema(description = "实付金额")
    private BigDecimal amount;
}
