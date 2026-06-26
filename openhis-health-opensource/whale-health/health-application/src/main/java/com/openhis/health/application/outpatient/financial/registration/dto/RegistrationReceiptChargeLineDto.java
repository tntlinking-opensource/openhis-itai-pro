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

/**
 * 挂号小票收费项目行（与 PrintJson {@code chargeItemLines} 列 field 一致）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "挂号小票收费项目行")
public class RegistrationReceiptChargeLineDto {

    @Schema(description = "项目名称")
    private String itemName;

    @Schema(description = "金额展示文本")
    private String amountText;

    @Schema(description = "费用类别展示文本")
    private String chargeCategoryText;
}
