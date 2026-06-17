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

package com.openhis.health.application.inpatient.financial.prepay.dto;

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
/** 预交金支付明细。 */
@Schema(description = "预交金支付明细")
public class PrepayPaymentDetailDto {

    @Schema(description = "支付渠道 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String payChannelCode;

    @Schema(description = "明细金额", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;

    /** 个人现金户 ID；缺省由应用层填入登记账户。 */
    @Schema(description = "个人现金户 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long accountId;
}
