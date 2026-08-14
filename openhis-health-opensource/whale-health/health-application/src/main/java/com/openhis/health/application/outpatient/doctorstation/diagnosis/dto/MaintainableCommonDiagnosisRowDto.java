/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.diagnosis.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 个人/科室常用诊断维护行：含模板行主键，供解绑操作定位。
 *
 * <p>与 {@link CommonDiagnosisRowDto} 区别：本 DTO 面向维护列表，必须带回 {@code templateId}；
 * 快捷树候选无需模板主键。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-可维护常用诊断行")
public class MaintainableCommonDiagnosisRowDto {

    /**
     * 常用模板行主键（cus_condition_template.id），解绑必传。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "常用模板行主键")
    private Long templateId;

    /**
     * 诊断定义主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊断定义标识")
    private Long conditionDefinitionId;

    /**
     * 标准诊断编码。
     */
    @Schema(description = "诊断编码")
    private String conditionCode;

    /**
     * 标准诊断名称。
     */
    @Schema(description = "诊断定义名称")
    private String conditionDefinitionName;

    /**
     * 中西医维度：WESTERN / TCM。
     */
    @Schema(description = "诊断中西医维度")
    private String diagnosisKind;

    /**
     * 中西医维度中文展示。
     */
    @Schema(description = "诊断中西医维度中文展示")
    private String diagnosisKindText;

    /**
     * 医保诊断编码（定义级）。
     */
    @Schema(description = "医保诊断编码")
    private String ybNo;

    /**
     * 排序权重（越小越靠前）。
     */
    @Schema(description = "排序权重")
    private Integer sortWeight;
}
