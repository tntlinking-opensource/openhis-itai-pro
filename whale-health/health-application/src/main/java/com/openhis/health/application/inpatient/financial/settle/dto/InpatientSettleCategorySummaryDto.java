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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 住院结算费用大类汇总 DTO（IF-IP-STL-04/07）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院结算费用大类汇总")
public class InpatientSettleCategorySummaryDto {

    @Schema(description = "大类 code", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoryCode;

    @Schema(description = "大类名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String categoryDisplay;

    @Schema(description = "该大类待结算金额合计", requiredMode = Schema.RequiredMode.REQUIRED)
    private BigDecimal amount;

    @Schema(description = "明细条数", requiredMode = Schema.RequiredMode.REQUIRED)
    private Integer lineCount;
}
