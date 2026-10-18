/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.inpatientadmission.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.shared.inpatient.AdmissionDiagnosisSubmitGate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 住院申请提交的入院诊断明细行（IF-IP-REQ-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊医生站-住院申请-入院诊断提交行")
public class AdmissionDiagnosisSubmitDto
    implements AdmissionDiagnosisSubmitGate.AdmissionDiagnosisRowView {

    /** 已有就诊诊断行主键；更新时携带。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "已有就诊诊断行主键；更新时携带")
    private Long encounterDiagnosisId;

    /** 新开诊断时必填：诊断目录主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 诊断目录主键；引用已有就诊诊断时可空。 */
    @Schema(description = "诊断目录主键；引用已有就诊诊断时可空")
    private Long conditionDefinitionId;

    /** 是否主诊断。 */
    @NotNull(message = "是否主诊断不能为空")
    /** 是否主诊断。 */
    @Schema(description = "是否主诊断", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean primaryFlag;

    /** 诊断说明。 */
    @Schema(description = "诊断说明")
    private String description;

    /** 是否中医诊断。 */
    @Schema(description = "是否中医诊断；中医一病一症时两条均为 true")
    /** 是否中医诊断；中医一病一症时两条均为 true。 */
    private Boolean tcmFlag;

    /** 中医病证组号。 */
    @Schema(description = "中医病证组号（同组病+证共用；可空由后端分配）")
    /** 中医病证组号（同组病+证共用；可空由后端分配）。 */
    private String tcmSyndromeGroup;
}
