/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.diagnosis.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * IF-DOR-06B 扩展：慢病备案未匹配点击后的可导入定义。
 */
@Data
@Schema(description = "确保慢病备案病种目录可导入响应")
public class EnsureOpspFilingDiagnosisResponse {

    /** 门慢门特病种目录代码。 */
    @Schema(description = "门慢门特病种目录代码")
    private String opspDiseCode;

    /** 门慢门特病种名称。 */
    @Schema(description = "门慢门特病种名称")
    private String opspDiseName;

    /** 诊断定义主键。 */
    @Schema(description = "诊断定义标识")
    private Long conditionDefinitionId;

    /** 诊断目录编码。 */
    @Schema(description = "诊断目录编码")
    private String conditionCode;

    /** 诊断定义名称。 */
    @Schema(description = "诊断定义名称")
    private String conditionDefinitionName;

    /** 中西医维度。 */
    @Schema(description = "中西医维度")
    private String diagnosisKind;

    /**
     * 医保医疗类别码（门诊慢病 140104 / 门诊特病 140201）。
     */
    @Schema(description = "医保医疗类别码")
    private String medicalTypeCode;

    /**
     * true 表示本次对 {@code chronic_spec} 发生了写操作（新建或启用）；
     * false 表示复用已匹配/已启用目录，未写库。
     */
    @Schema(description = "是否本次写慢病目录（新建或启用）")
    private Boolean created;
}
