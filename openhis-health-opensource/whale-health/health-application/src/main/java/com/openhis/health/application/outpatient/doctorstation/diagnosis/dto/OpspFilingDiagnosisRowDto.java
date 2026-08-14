/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.diagnosis.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * IF-DOR-06B：慢特病备案可导入诊断行。
 */
@Data
@Schema(description = "慢特病备案可导入诊断行")
public class OpspFilingDiagnosisRowDto {

    /** 门慢门特病种目录代码。 */
    @Schema(description = "门慢门特病种目录代码")
    private String opspDiseCode;

    /** 门慢门特病种名称。 */
    @Schema(description = "门慢门特病种名称")
    private String opspDiseName;

    /** 备案开始日期。 */
    @Schema(description = "备案开始日期")
    private String beginDate;

    /** 备案结束日期。 */
    @Schema(description = "备案结束日期")
    private String endDate;

    /** 是否在有效期内。 */
    @Schema(description = "是否在有效期内")
    private Boolean valid;

    /**
     * 映射状态：matched / unmatched / expired / alreadyOnEncounter / ambiguous。
     */
    @Schema(description = "映射状态")
    private String matchStatus;

    /** 未匹配/歧义/过期提示。 */
    @Schema(description = "提示文案")
    private String matchHint;

    /** 映射成功时的诊断定义主键。 */
    @Schema(description = "诊断定义标识")
    private Long conditionDefinitionId;

    /** 诊断目录编码。 */
    @Schema(description = "诊断目录编码")
    private String conditionCode;

    /** 诊断定义名称。 */
    @Schema(description = "诊断定义名称")
    private String conditionDefinitionName;

    /** 命中列。 */
    @Schema(description = "命中列")
    private String matchField;

    /** 中西医维度标签。 */
    @Schema(description = "中西医维度")
    private String diagnosisKind;

    /**
     * 医保医疗类别码（门诊慢病 140104 / 门诊特病 140201；5301 缺省 140104，5302A 可覆盖）。
     */
    @Schema(description = "医保医疗类别码")
    private String medicalTypeCode;
}
