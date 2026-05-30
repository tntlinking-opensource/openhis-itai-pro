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

package com.openhis.health.application.outpatient.financial.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

@Data
@Accessors(chain = true)
@Schema(description = "支付明细")
/**
 * PaymentDetailDto。
 */
public class PaymentDetailDto {

    /** 支付渠道编码。 */
    @Schema(description = "支付渠道编码")
    private String payChannelCode;

    /** 支付金额。 */
    @Schema(description = "支付金额")
    private BigDecimal payAmount;

    /** 外部流水号。 */
    @Schema(description = "外部流水号")
    private String transactionNo;

    /** 账户主键。 */
    @Schema(description = "账户主键")
    private Long accountId;

    /** 账户编码。 */
    @Schema(description = "账户编码")
    private String accountCode;
}
