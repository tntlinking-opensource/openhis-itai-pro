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

package com.openhis.health.application.inpatient.doctorstation.diagnosis.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 常用诊断快捷候选行（AS-IP-DOR-002 / IF-IP-DOR-03）：四类树节点（患者历史、医生常用、个人模板、科室模板）。
 *
 * <p>仅含目录锚点与展示字段，不含就诊级验证状态；与 {@link EncounterDiagnosisRowDto} 区别为本 DTO 面向点选录入，
 * 不绑定具体 {@code conditionId}，定义主键来自模板展开或历史去重集合。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-常用诊断行")
public class CommonDiagnosisRowDto {

    /**
     * 诊断定义主键：写入保存批次时作为 {@code conditionDefinitionId} 来源。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊断定义标识")
    private Long conditionDefinitionId;

    /**
     * 标准诊断编码，来自启用中的目录行。
     */
    @Schema(description = "诊断编码")
    private String conditionCode;

    /**
     * 标准诊断定义名称，供快捷树节点展示。
     */
    @Schema(description = "诊断定义名称")
    private String conditionDefinitionName;

    /**
     * 中西医维度标签（{@code WESTERN}/{@code TCM}），由目录 {@code sourceEnum} 分组推导。
     */
    @Schema(description = "诊断中西医维度：WESTERN=西医诊断，TCM=中医诊断")
    private String diagnosisKind;

    /**
     * 中西医维度中文展示，可与标签同时返回以减少前端分支。
     */
    @Schema(description = "诊断中西医维度中文展示")
    private String diagnosisKindText;

    /**
     * 目录来源分类的数值化展示（若术语存字符串码则可能为解析后的整数）；与 {@code sourceEnumText} 成对使用。
     */
    @Schema(description = "来源分类")
    private Integer sourceEnum;

    /**
     * 目录来源中文名，来自术语服务 {@code condition_definition_source} 映射；缺失时可为 null。
     */
    @Schema(description = "来源分类展示文本")
    private String sourceEnumText;

    /**
     * 医保目录诊断编码（定义级），便于常用树节点展示对码状态。
     */
    @Schema(description = "医保诊断编码")
    private String ybNo;
}
