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
 * 就诊诊断列表行：对应门诊信息读模型中 {@code diagnosisJson} 数组元素，既可由快照反序列化得到，也可作为保存回显结构。
 *
 * <p>与 {@link com.zxplat.domain.administration.encounter.service.EncounterDomainService} 物化快照字段一一对应；
 * 中西医维度 {@code diagnosisKind} 与药品「用药类别」无关，勿混用术语。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站-就诊诊断行")
public class EncounterDiagnosisRowDto {

    /**
     * 就诊诊断行主键 {@code adm_encounter_diagnosis.id}：已保存行必有；新建草稿场景可能为 null 直至落库。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "就诊诊断行主键")
    private Long encounterDiagnosisId;

    /**
     * 诊断定义（目录）主键：关联标准编码与名称。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "诊断定义标识")
    private Long conditionDefinitionId;

    /**
     * 标准诊断编码，来自目录 {@code conditionCode}。
     */
    @Schema(description = "诊断编码")
    private String conditionCode;

    /**
     * 标准诊断定义名称，来自目录；展示与打印主文案。
     */
    @Schema(description = "诊断定义名称")
    private String conditionDefinitionName;

    /**
     * 中西医维度标签：{@code WESTERN} 或 {@code TCM}，由目录来源枚举分组推导，非药品含义。
     */
    @Schema(description = "诊断中西医维度：WESTERN=西医诊断，TCM=中医诊断")
    private String diagnosisKind;

    /**
     * 中西医维度中文：{@code 西医}/{@code 中医}；可由 {@link com.zxplat.domain.share.utils.DiagnosisKindPresentationUtil} 从标签推导。
     */
    @Schema(description = "诊断中西医维度中文展示")
    private String diagnosisKindText;

    /**
     * 验证状态 UI 整数码（1～6），与医生站表格及快照 JSON 一致；持久化层存 FHIR 风格字符串码。
     */
    @Schema(description = "验证状态整数码")
    private Integer verificationStatusEnum;

    /**
     * 验证状态中文展示；快照可冗余存储，缺省时由整数码在应用层补齐。
     */
    @Schema(description = "验证状态展示文本")
    private String verificationStatusEnumText;

    /**
     * 主诊标记：{@code 1} 表示本就诊下主诊断，{@code 0} 为非主诊；同一就诊保存批次至多一个主诊由应用层校验。
     */
    @Schema(description = "主诊标记：1=主诊断，0=非主诊断")
    private Integer mainDiagnosisFlag;

    /**
     * 列表排序号，映射就诊诊断关联的 {@code rank}；越小越靠前（具体展示以前端为准）。
     */
    @Schema(description = "诊断排序号")
    private Integer diagnosisSortNo;

    /**
     * 医疗类别/医保类别等业务编码，来自就诊诊断关联 {@code medType}。
     */
    @Schema(description = "医疗类型代码")
    private String medicalTypeCode;

    /**
     * 诊断补充说明：来自就诊诊断关联 {@code description}。
     */
    @Schema(description = "诊断描述")
    private String diagnosisDesc;

    /**
     * 患者疾病诊断类型持久化码（住院场景扩展字段）；与 {@link com.zxplat.domain.share.enums.IptPatientDiseaseDiagType} 对齐。
     */
    @Schema(description = "患者疾病诊断类型代码")
    private String iptDiseaseTypeCode;

    /**
     * 患者疾病诊断类型中文展示；由码解析枚举后取 display，未知码时可为 null。
     */
    @Schema(description = "患者疾病诊断类型展示文本")
    private String iptDiseaseTypeText;

    /**
     * 医保目录诊断编码，来自诊断目录 {@code yb_no}。
     */
    @Schema(description = "医保诊断编码")
    private String ybNo;

    /**
     * 中医病证组号：同组病名与证候事实共用，用于列表合并展示与中医 Tab 绑定。
     */
    @Schema(description = "中医病证组号")
    private String tcmSyndromeGroup;

    /**
     * 诊断定义目录来源码（如 {@code tcm_dx}/{@code tcm_syndrome}），用于前端区分病名/证候行。
     */
    @Schema(description = "诊断定义来源分类码")
    private String sourceEnum;
}
