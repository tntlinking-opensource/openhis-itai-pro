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

package com.openhis.health.application.outpatient.doctorstation.inpatientadmission.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.openhis.health.application.outpatient.doctorstation.inpatientadmission.dto.AdmissionDiagnosisSubmitDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * IF-IP-REQ-01：发起住院申请请求。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-住院申请-发起住院申请请求")
public class RequestInpatientAdmissionRequest {

    /** 当前门诊或急诊就诊锚点。 */
    @NotNull(message = "门诊就诊标识不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "源门诊或急诊就诊主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long ambEncounterId;

    /** 患者主键；传入时须与源就诊头表一致。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "患者主键；可选，传入须与源就诊患者一致")
    private Long patientId;

    /** 意向住院科室。 */
    @NotNull(message = "意向住院科室不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "拟收治科室主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long targetDepartmentId;

    /** 意向病区位置（WARD 形态）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "意向病区位置主键")
    private Long wardId;

    /** 申请医师。 */
    @NotNull(message = "申请医师不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "申请医师主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long requestDoctorId;

    /** 入院来源编码。 */
    @Schema(description = "入院来源编码")
    private String admitSourceCode;

    /** 入院方式编码。 */
    @Schema(description = "入院方式编码")
    private String inWayCode;

    /** 病情优先级编码。 */
    @Schema(description = "病情优先级编码")
    private String priorityCode;

    /** 入院诊断明细（UI 可预填门诊主诊，后台按列表为住院线落库）。 */
    @NotEmpty(message = "入院诊断不能为空")
    @Valid
    @Schema(description = "入院诊断明细列表", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<AdmissionDiagnosisSubmitDto> admissionDiagnoses;

    /** 操作人。 */
    @NotNull(message = "操作人不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "操作人主键", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long operatorId;
}
