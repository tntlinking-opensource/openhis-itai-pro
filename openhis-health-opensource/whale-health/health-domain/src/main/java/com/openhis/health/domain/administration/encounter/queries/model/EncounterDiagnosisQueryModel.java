/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 就诊诊断读模型：展示就诊与诊断目录的关联及排序、主诊断标记。
 */
@Getter
@Setter
public class EncounterDiagnosisQueryModel {
    /** 就诊诊断关联主键 {@code adm_encounter_diagnosis.id}。 */
    private Long diagnosisId;
    /** 就诊主键 {@code adm_encounter.id}。 */
    private Long encounterId;
    /** 诊断目录主键 {@code cln_condition_definition.id}。 */
    private Long conditionDefinitionId;
    /** 是否主诊断。 */
    private Boolean primaryFlag;
    /** 排序序号。 */
    private Integer rank;
    /** 诊断描述。 */
    private String description;
    /** 住院病种类型编码（医保/诊疗类别相关）。 */
    private String diagTypeCode;
    /** 医保医疗类别。 */
    private String medType;
    /** 中医病证组号。 */
    private String tcmSyndromeGroup;
    /** 是否中医诊断关联。 */
    private Boolean tcmFlag;
}
