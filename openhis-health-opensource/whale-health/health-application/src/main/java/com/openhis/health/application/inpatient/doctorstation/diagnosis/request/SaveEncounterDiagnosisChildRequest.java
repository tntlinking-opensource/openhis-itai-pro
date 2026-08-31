/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.diagnosis.request;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-05 保存批次单行：就诊诊断关联写入载荷（AS-IP-DOR-002）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-就诊诊断保存子项")
public class SaveEncounterDiagnosisChildRequest {

    /** 就诊诊断关联标识；空=新增。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊诊断关联标识；空=新增")
    private Long encounterDiagnosisId;

    /** 诊断定义主键。 */
    @NotNull(message = "诊断定义不能为空")
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊断定义标识", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long conditionDefinitionId;

    /** 诊断编码。 */
    @NotBlank(message = "诊断编码不能为空")
    @Schema(description = "诊断编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String conditionCode;

    /** 诊断定义标准名称（目录）。 */
    @NotBlank(message = "诊断名称不能为空")
    @Schema(description = "诊断定义名称", requiredMode = Schema.RequiredMode.REQUIRED)
    private String conditionDefinitionName;

    /** 验证状态整数码；空则按已确认处理。 */
    @Schema(description = "验证状态整数码；空则按已确认处理")
    private Integer verificationStatusEnum;

    /** 医保编码。 */
    @Schema(description = "医保编码")
    private String ybNo;

    /** 主诊标记：1=主诊断，0=非主诊断。 */
    @Schema(description = "主诊标记：1=主诊断，0=非主诊断")
    private Integer mainDiagnosisFlag;

    /** 诊断排序号。 */
    @Schema(description = "诊断排序号")
    private Integer diagnosisSortNo;

    /** 医疗类型代码（YbMedType code；空=普通住院）。 */
    @Schema(description = "医疗类型代码（YbMedType code；空=普通住院）")
    private String medicalTypeCode;

    /** 诊断描述。 */
    @Schema(description = "诊断描述")
    private String diagnosisDesc;

    /** 患者疾病诊断类型代码（住院须合法 IptPatientDiseaseDiagType code）。 */
    @Schema(description = "患者疾病诊断类型代码")
    private String iptDiseaseTypeCode;

    /** 诊断分类字典码。 */
    @Schema(description = "诊断分类字典码")
    private String categoryCode;

    /** 严重程度字典码。 */
    @Schema(description = "严重程度字典码")
    private String severityCode;
}
