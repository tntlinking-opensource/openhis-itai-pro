/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.financial.registration.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.zxplat.application.shared.inpatient.AdmissionDiagnosisSubmitGate;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 入院诊断提交行 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "入院诊断提交行")
public class AdmissionDiagnosisSubmitDto
    implements AdmissionDiagnosisSubmitGate.AdmissionDiagnosisRowView {

    /** 已有就诊诊断行主键。 */
    @Schema(description = "已有就诊诊断行主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterDiagnosisId;

    /** 诊断定义 ID。 */
    @Schema(description = "诊断定义 ID（新开时必填）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long conditionDefinitionId;

    /** 是否主诊断。 */
    @Schema(description = "是否主诊断", requiredMode = Schema.RequiredMode.REQUIRED)
    private Boolean primaryFlag;

    /** 诊断说明。 */
    @Schema(description = "诊断说明")
    private String description;

    /** 是否中医诊断。 */
    @Schema(description = "是否中医诊断；中医一病一症时两条均为 true")
    private Boolean tcmFlag;

    /** 中医病证组号。 */
    @Schema(description = "中医病证组号（同组病+证共用；可空由后端分配）")
    private String tcmSyndromeGroup;
}
