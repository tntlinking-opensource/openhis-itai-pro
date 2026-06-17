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

package com.openhis.health.application.inpatient.financial.prepay.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 退预交金响应（IF-IP-PREP-04）。
 */
@Data
@Accessors(chain = true)
/** 退预交金响应。 */
@Schema(description = "退预交金响应")
public class RefundPrepayAdvanceDepositResponse {

    @Schema(description = "退款支付流水 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long paymentId;

    /** 结算后账户余额。 */
    @Schema(description = "结算后账户余额（展示口径，动态计算）")
    private BigDecimal accountBalance;
}
