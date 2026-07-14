/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.refund.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 退费确认窗渠道实退明细（IF-REF-05）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "退费渠道实退明细")
public class RefundPaymentDetailSubmitDto {

    /** 退款渠道编码。 */
    @NotBlank(message = "payChannelCode 不能为空")
    @Schema(description = "退款渠道编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String payChannelCode;

    /** 本渠道实退金额。 */
    @NotNull(message = "amount 不能为空")
    /** 本渠道实退金额。 */
    @Schema(description = "本渠道实退金额", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;
}
