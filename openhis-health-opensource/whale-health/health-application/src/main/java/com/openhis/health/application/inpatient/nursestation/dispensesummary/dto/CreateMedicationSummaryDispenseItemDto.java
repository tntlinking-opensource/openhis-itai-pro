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

package com.openhis.health.application.inpatient.nursestation.dispensesummary.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 生成汇总领药 · 勾选发放行（IF-IP-SUM-04）。
 */
@Data
@Accessors(chain = true)
/** 汇总写路径勾选发放行。 */
@Schema(description = "汇总写路径勾选发放行")
public class CreateMedicationSummaryDispenseItemDto {

    /** 发放行主键。 */
    @NotNull(message = "发放行不能为空")
    @Schema(description = "发放行主键", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long dispenseId;

    /** 就诊主键（可见性交叉校验）。 */
    @NotNull(message = "就诊不能为空")
    /** 就诊主键。 */
    @Schema(description = "就诊主键", required = true)
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;
}
