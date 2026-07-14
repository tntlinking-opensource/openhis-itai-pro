/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.registration.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 住院登记预交金支付明细 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "预交金支付明细")
public class PaymentDetailDto {

    /** 支付渠道 code（业务字段）。 */
    @Schema(description = "支付渠道 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String payChannelCode;

    /** 支付金额。 */
    @Schema(description = "支付金额", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;

    /** 结算账户 ID。 */
    @Schema(description = "结算账户 ID（开户后回填）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
}
