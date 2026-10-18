/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.prepay.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 住院预交金支付/退现明细 DTO（IF-IP-PREP-03/04）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "预交金支付明细")
public class PrepayPaymentDetailDto {

    /** 支付渠道 code。 */
    @Schema(description = "支付渠道 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String payChannelCode;

    /** 明细金额。 */
    @Schema(description = "明细金额", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;

    /** 个人现金户 ID；缺省由应用层填入登记账户。 */
    @Schema(description = "个人现金户 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
}
