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

    /** 诊断定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊断定义标识")
    private Long conditionDefinitionId;

    /** 诊断编码。 */
    @Schema(description = "诊断编码")
    private String conditionCode;

    /** 诊断定义标准名称（目录）。 */
    @Schema(description = "诊断定义名称")
    private String conditionDefinitionName;

    /** 拼音检索码。 */
    @Schema(description = "拼音检索码")
    private String pyStr;

    /** 五笔检索码。 */
    @Schema(description = "五笔检索码")
    private String wbStr;

    /** 诊断中西医维度。 */
    @Schema(description = "诊断中西医维度：WESTERN=西医诊断，TCM=中医诊断")
    private String diagnosisKind;

    /** 诊断中西医维度中文展示。 */
    @Schema(description = "诊断中西医维度中文展示：西医/中医")
    private String diagnosisKindText;

    /** 来源分类（整型展示）。 */
    @Schema(description = "来源分类")
    private Integer sourceEnum;

    /** 来源分类文本。 */
    @Schema(description = "来源分类展示文本")
    private String sourceEnumText;

    /** 医保标记：1 是，0 否。 */
    @Schema(description = "医保标记：1=是，0=否")
    private Integer ybFlag;

    /** 医保标记文本。 */
    @Schema(description = "医保标记展示文本")
    private String ybFlagText;

    /** 发布状态整数码（仅返回已启用）。 */
    @Schema(description = "发布状态整数码")
    private Integer statusEnum;
}
