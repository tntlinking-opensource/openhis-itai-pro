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
    @Schema(description = "本渠道实退金额", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;
}
