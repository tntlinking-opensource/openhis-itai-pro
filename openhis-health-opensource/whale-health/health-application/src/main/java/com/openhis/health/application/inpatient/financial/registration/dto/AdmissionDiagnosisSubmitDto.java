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

package com.openhis.health.application.inpatient.financial.registration.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 入院诊断提交行 DTO。
 */
@Data
@Accessors(chain = true)
/** 入院诊断提交行。 */
@Schema(description = "入院诊断提交行")
public class AdmissionDiagnosisSubmitDto {

    @Schema(description = "已有诊断事实 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 已有诊断事实 ID（业务字段）。 */
    private Long conditionId;

    /** 诊断定义 ID。 */
    @Schema(description = "诊断定义 ID（新开时必填）")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 诊断定义 ID（新开时必填）（业务字段）。 */
    private Long conditionDefinitionId;

    /** 是否主诊断。 */
    @Schema(description = "是否主诊断", requiredMode = Schema.RequiredMode.REQUIRED)
    /** 是否主诊断（业务字段）。 */
    private Boolean primaryFlag;

    /** 诊断说明。 */
    @Schema(description = "诊断说明")
    /** 诊断说明（业务字段）。 */
    private String description;
}
