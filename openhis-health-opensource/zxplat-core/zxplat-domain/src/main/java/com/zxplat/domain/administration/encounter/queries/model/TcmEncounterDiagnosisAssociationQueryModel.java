/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.queries.model;

import lombok.Getter;
import lombok.Setter;

/**
 * 就诊中医诊断关联读模型（IF-DOR-30/32：按 {@code tcm_syndrome_group} 聚合病证组）。
 */
@Getter
@Setter
public class TcmEncounterDiagnosisAssociationQueryModel {
    /** 就诊诊断关联主键 {@code adm_encounter_diagnosis.id}。 */
    private Long encounterDiagnosisId;
    /** 诊断目录主键 {@code cln_condition_definition.id}。 */
    private Long conditionDefinitionId;
    /** 中医证候组号（同组病名与证候共享）。 */
    private String tcmSyndromeGroup;
    /** 是否主诊病证组。 */
    private Boolean primaryFlag;
    /** 组级排序号。 */
    private Integer rank;
    /** 医保/诊疗医疗类别。 */
    private String medType;
    /** 组级诊断描述。 */
    private String description;
}
