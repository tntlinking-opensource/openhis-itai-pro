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

package com.openhis.health.application.outpatient.nursestation.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 按就诊查询处置单（三列表同构）请求（IF-NUR-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "按就诊查询处置单请求")
public class QueryEncounterTreatmentSheetRequest {

    /**
     * 当前选中就诊主键。
     */
    @NotNull(message = "encounterId 不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "当前选中就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long encounterId;

    /**
     * 诊疗类目过滤码：仅缩小诊疗列表，药品与耗材列表忽略（BR-NUR-009）。
     */
    @Schema(description = "诊疗类目过滤码（仅缩小诊疗列表）")
    private String serviceCategoryCode;
}
