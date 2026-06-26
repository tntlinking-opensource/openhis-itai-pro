/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.module.shared.diagnosis;

import lombok.Getter;
import lombok.Setter;

/**
 * 患者历史常用诊断读侧行（IF-DOR-06A 历史分支）：西医单条目录；中医按病证组合并。
 */
@Getter
@Setter
public class PatientHistoryCommonDiagnosisRow {

    /** 病名/西医诊断定义主键。 */
    private Long conditionDefinitionId;

    /** 证候定义主键（仅中医合并行）。 */
    private Long syndromeDefinitionId;

    /** 病名/西医诊断编码。 */
    private String conditionCode;

    /** 树节点展示名（中医为病名（证候）合并）。 */
    private String conditionDefinitionName;

    /** 中西医维度标签 {@code WESTERN}/{@code TCM}。 */
    private String diagnosisKind;

    /** 目录来源术语码（如 {@code tcm_dx}）。 */
    private String sourceEnumCode;

    /** 来源整数码展示（兼容旧前端字段）。 */
    private Integer sourceEnum;

    /** 医保目录编码。 */
    private String ybNo;

    /** 中医证候组号（代表性样本，跨就诊去重后仅作参考）。 */
    private String tcmSyndromeGroup;
}
