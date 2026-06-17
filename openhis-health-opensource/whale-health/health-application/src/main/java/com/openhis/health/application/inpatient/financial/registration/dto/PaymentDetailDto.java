/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
/** 预交金支付明细。 */
@Schema(description = "预交金支付明细")
public class PaymentDetailDto {

    @Schema(description = "支付渠道 code", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 支付渠道 code（业务字段）。 */
    private String payChannelCode;

    /** 支付金额。 */
    @Schema(description = "支付金额", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 支付金额（业务字段）。 */
    private BigDecimal amount;

    /** 结算账户 ID。 */
    @Schema(description = "结算账户 ID（开户后回填）")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 结算账户 ID（开户后回填）（业务字段）。 */
    private Long accountId;
}
