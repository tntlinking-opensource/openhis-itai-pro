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
/** 预结算收费项。 */
@Schema(description = "预结算收费项")
public class ChargeItemPreviewDto {

    /** 收费项编码。 */
    @Schema(description = "收费项编码")
    /** 收费项编码（业务字段）。 */
    private String chargeItemCode;

    /** 收费项名称。 */
    @Schema(description = "收费项名称")
    /** 收费项名称（业务字段）。 */
    private String chargeItemName;

    /** 单价。 */
    @Schema(description = "单价")
    /** 单价（业务字段）。 */
    private BigDecimal unitPrice;

    /** 数量。 */
    @Schema(description = "数量")
    /** 数量（业务字段）。 */
    private BigDecimal quantity;

    /** 金额。 */
    @Schema(description = "金额")
    /** 金额（业务字段）。 */
    private BigDecimal amount;
}
