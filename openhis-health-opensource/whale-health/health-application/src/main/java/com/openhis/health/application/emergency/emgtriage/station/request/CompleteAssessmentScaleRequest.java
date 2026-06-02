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

package com.openhis.health.application.emergency.emgtriage.station.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.domain.emergency.emgtriageassessment.model.TriageAssessmentFormLineDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-EMG-12：完成评分量表。
 */
@Data
@Accessors(chain = true)
@Schema(description = "急诊分诊-量表完成请求")
public class CompleteAssessmentScaleRequest {

    @NotNull
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "分诊主键（载荷归属）", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long emgTriageId;

    @NotBlank
    @Schema(description = "评估类型", requiredMode = Schema.RequiredMode.REQUIRED)
    private String assessmentType;

    @NotEmpty
    @Schema(description = "量表作答行", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<TriageAssessmentFormLineDto> scaleInputs;

    @Schema(description = "患者年龄（岁），REMS 等依赖")
    private Integer patientAgeYears;

    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "评估人")
    private Long assessedBy;
}
