/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.diagnosis.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-DOR-07：诊断定义分页行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-诊断定义分页行")
public class ConditionDefinitionRowResponse {

    /** 诊断定义标识。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊断定义标识")
    private Long conditionDefinitionId;

    /** 诊断编码。 */
    @Schema(description = "诊断编码")
    private String conditionCode;

    /** 诊断定义名称。 */
    @Schema(description = "诊断定义名称")
    private String conditionDefinitionName;

    /** 拼音检索码。 */
    @Schema(description = "拼音检索码")
    private String pyStr;

    /** 五笔检索码。 */
    @Schema(description = "五笔检索码")
    private String wbStr;

    /** 诊断中西医维度：WESTERN=西医诊断，TCM=中医诊断。 */
    @Schema(description = "诊断中西医维度：WESTERN=西医诊断，TCM=中医诊断")
    private String diagnosisKind;

    /** 诊断中西医维度中文展示：西医/中医。 */
    @Schema(description = "诊断中西医维度中文展示：西医/中医")
    private String diagnosisKindText;

    /** 来源分类。 */
    @Schema(description = "来源分类")
    private Integer sourceEnum;

    /** 来源分类展示文本。 */
    @Schema(description = "来源分类展示文本")
    private String sourceEnumText;

    /** 医保标记：1=是，0=否。 */
    @Schema(description = "医保标记：1=是，0=否")
    private Integer ybFlag;

    /** 医保标记展示文本。 */
    @Schema(description = "医保标记展示文本")
    private String ybFlagText;

    /** 发布状态整数码。 */
    @Schema(description = "发布状态整数码")
    private Integer statusEnum;
}
