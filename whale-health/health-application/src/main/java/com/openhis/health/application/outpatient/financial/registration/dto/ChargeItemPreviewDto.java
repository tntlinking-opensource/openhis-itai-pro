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
@Schema(description = "预结算收费项")
/**
 * ChargeItemPreviewDto。
 */
public class ChargeItemPreviewDto {

    /** 收费项编码。 */
    @Schema(description = "收费项编码")
    private String chargeItemCode;

    /** 收费项名称。 */
    @Schema(description = "收费项名称")
    private String chargeItemName;

    /** 单价。 */
    @Schema(description = "单价")
    private BigDecimal unitPrice;

    /** 数量。 */
    @Schema(description = "数量")
    private BigDecimal quantity;

    /** 金额。 */
    @Schema(description = "金额")
    private BigDecimal amount;
}
