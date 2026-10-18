/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.diagnosis.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 常用诊断快捷候选行：用于 IF-DOR-06A 四类树（患者历史、医生常用、个人模板、科室模板），仅含目录锚点与展示字段，不含就诊级验证状态等。
 *
 * <p>与 {@link EncounterDiagnosisRowDto} 区别：本 DTO 面向「点选录入」候选，不绑定具体 conditionId；定义 id 来自模板或历史去重集合。</p>
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

    /**
     * 中医证候组号：历史分支按就诊关联反查后回填，供前端病证合并展示与重复校验。
     */
    @Schema(description = "中医证候组号")
    private String tcmSyndromeGroup;

    /**
     * 证候定义主键：仅中医合并历史行返回，点选录入时与病名定义成对使用。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "证候定义标识")
    private Long syndromeDefinitionId;

    /**
     * 条件模板主键：个人/科室常用树节点解绑时回传；历史/医生常用分支为空。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "条件模板标识（个人/科室常用解绑用）")
    private Long templateId;
}
