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

package com.openhis.health.application.inpatient.doctorstation.diagnosis.response;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * IF-IP-DOR-04：诊断定义分页行（AS-IP-DOR-002）。
 *
 * <p>由读模型行与术语码表映射装配；仅展示已发布（ACTIVE）定义，中西医维度由目录来源推导。</p>
 */
@Data
@Accessors(chain = true)
/** 医生站-诊断定义分页行。 */
@Schema(description = "医生站-诊断定义分页行")
public class ConditionDefinitionRowResponse {

    /**
     * 诊断定义主键：点选后写入保存批次的 {@code conditionDefinitionId}。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊断定义标识")
    private Long conditionDefinitionId;

    /**
     * 标准诊断编码（ICD/院内码）。
     */
    @Schema(description = "诊断编码")
    private String conditionCode;

    /**
     * 标准诊断定义名称：列表与检索主展示文案。
     */
    @Schema(description = "诊断定义名称")
    private String conditionDefinitionName;

    /**
     * 拼音检索码：供前端模糊检索。
     */
    @Schema(description = "拼音检索码")
    private String pyStr;

    /**
     * 五笔检索码：供前端模糊检索。
     */
    @Schema(description = "五笔检索码")
    private String wbStr;

    /**
     * 中西医维度标签：{@code WESTERN} 或 {@code TCM}。
     */
    @Schema(description = "诊断中西医维度：WESTERN=西医诊断，TCM=中医诊断")
    private String diagnosisKind;

    /**
     * 中西医维度中文：{@code 西医}/{@code 中医}。
     */
    @Schema(description = "诊断中西医维度中文展示：西医/中医")
    private String diagnosisKindText;

    /**
     * 目录来源分类整型展示（术语码解析结果）。
     */
    @Schema(description = "来源分类")
    private Integer sourceEnum;

    /**
     * 目录来源中文名：来自术语 {@code condition_definition_source} 映射。
     */
    @Schema(description = "来源分类展示文本")
    private String sourceEnumText;

    /**
     * 医保目录命中标记：{@code 1} 是，{@code 0} 否。
     */
    @Schema(description = "医保标记：1=是，0=否")
    private Integer ybFlag;

    /**
     * 医保命中中文：是/否。
     */
    @Schema(description = "医保标记展示文本")
    private String ybFlagText;

    /**
     * 发布状态整型展示：分页接口仅返回已启用（ACTIVE）行。
     */
    @Schema(description = "发布状态整数码")
    private Integer statusEnum;
}
