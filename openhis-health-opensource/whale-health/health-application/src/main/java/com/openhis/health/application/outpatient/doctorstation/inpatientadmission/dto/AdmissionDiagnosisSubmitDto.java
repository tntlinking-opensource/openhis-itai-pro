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

package com.openhis.health.application.outpatient.doctorstation.inpatientadmission.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院申请提交的入院诊断明细行（IF-IP-REQ-01）。
 */
@Data
@Accessors(chain = true)
/** 门诊医生站-住院申请-入院诊断提交行。 */
@Schema(description = "门诊医生站-住院申请-入院诊断提交行")
public class AdmissionDiagnosisSubmitDto {

    /** 引用已有病情事实主键（可与门诊同 ICD，仍须为住院线新建关联）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "已有病情事实主键；新开诊断时可空")
    private Long conditionId;

    /** 新开诊断时必填：诊断目录主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 诊断目录主键；引用已有病情时可空。 */
    @Schema(description = "诊断目录主键；引用已有病情时可空")
    private Long conditionDefinitionId;

    /** 是否主诊断。 */
    @NotNull(message = "是否主诊断不能为空")
    /** 是否主诊断。 */
    @Schema(description = "是否主诊断", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean primaryFlag;

    /** 诊断说明。 */
    @Schema(description = "诊断说明")
    private String description;
}
