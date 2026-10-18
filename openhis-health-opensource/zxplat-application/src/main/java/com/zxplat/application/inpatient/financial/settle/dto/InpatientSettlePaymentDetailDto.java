/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.settle.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 住院结算支付渠道明细 DTO（预结算占位 / 正式结算实付）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院结算支付渠道明细", requiredProperties = {"payChannelCode", "amount"})
public class InpatientSettlePaymentDetailDto {

    /** 支付渠道 code。 */
    @NotNull(message = "支付渠道不能为空")
    @Schema(description = "支付渠道 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String payChannelCode;

    /** 明细金额。 */
    @NotNull(message = "支付金额不能为空")
    /** 明细金额。 */
    @Schema(description = "明细金额", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;
}
