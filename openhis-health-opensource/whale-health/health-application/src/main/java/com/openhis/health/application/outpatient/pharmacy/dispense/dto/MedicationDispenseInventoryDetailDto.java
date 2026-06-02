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

package com.openhis.health.application.outpatient.pharmacy.dispense.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 候选库房×批号可用库存明细行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "候选库房×批号可用库存明细")
public class MedicationDispenseInventoryDetailDto {

    /** 候选发放库房 ID。 */
    @Schema(description = "候选发放库房主键", requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long pharmacyOrganizationId;

    /** 库房名称。 */
    @Schema(description = "库房展示名称")
    private String pharmacyDisplayName;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 可用数量。 */
    @Schema(description = "可用数量", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal availableQuantity;

    /** 计量单位码。 */
    @Schema(description = "计量单位码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String unitCode;

    /** 单位展示。 */
    @Schema(description = "单位展示文案")
    private String unitText;
}
