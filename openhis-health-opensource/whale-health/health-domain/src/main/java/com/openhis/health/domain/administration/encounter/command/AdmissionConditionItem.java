/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 入院诊断项：住院就诊诊断关联，直挂诊断目录，不经 {@code cln_condition}。
 */
@Getter
@Setter
public class AdmissionConditionItem {

    /** 已有就诊诊断关联行主键 {@code adm_encounter_diagnosis.id}；更新时可选。 */
    private Long encounterDiagnosisId;

    /** 新开或复用诊断时必填：诊断目录主键 {@code cln_condition_definition.id}。 */
    private Long conditionDefinitionId;

    /** 是否主诊断。 */
    private Boolean primaryFlag;

    /** 诊断说明。 */
    private String description;

    /** 是否中医诊断（中医一病一症成组提交时为 true）。 */
    private Boolean tcmFlag;

    /** 中医病证组号（同组病名与证候共用；可空由编排层分配序列号）。 */
    private String tcmSyndromeGroup;
}
