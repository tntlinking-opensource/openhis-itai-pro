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

package com.openhis.health.application.inpatient.financial.settle.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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
    @Schema(description = "明细金额", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;
}
