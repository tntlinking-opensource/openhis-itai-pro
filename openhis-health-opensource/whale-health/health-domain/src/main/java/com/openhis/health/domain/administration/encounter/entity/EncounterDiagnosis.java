/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.entity;

import com.openhis.whale.core.domain.BaseAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * 就诊诊断行：本次就诊绑定的诊断目录及医疗类别等扩展字段。
 *
 * <p>直挂 {@code condition_definition_id}（{@code cln_condition_definition.id}），不再经 {@code cln_condition}。</p>
 */
@Getter
@Setter
@Entity
@Table(name = "adm_encounter_diagnosis")
public class EncounterDiagnosis extends BaseAuditedEntity {

    /** 就诊主键。 */
    @Column(name = "encounter_id", nullable = false)
    private Long encounterId;

    /** 诊断目录主键（{@code cln_condition_definition.id}）。 */
    @Column(name = "condition_definition_id", nullable = false)
    private Long conditionDefinitionId;

    /** 是否主诊断。 */
    @Column(name = "primary_flag")
    private Boolean primaryFlag;

    /** 排序序号。 */
    @Column(name = "rank")
    private Integer rank;

    /** 诊断描述。 */
    @Column(name = "description")
    private String description;

    /** 诊断类型码（术语 code 字符串，与 ipt_dise_type_code 对齐，不做整型转换）。 */
    @Column(name = "diag_type_code", length = 32)
    private String diagTypeCode;

    /** 诊断编码（历史列，可空；病种码以目录为准）。 */
    @Column(name = "diag_code")
    private Integer diagCode;

    /** 病情编码（历史列，可空；病种码以目录为准）。 */
    @Column(name = "condition_code")
    private Integer conditionCode;

    /** 医保或诊疗类别。 */
    @Column(name = "med_type")
    private String medType;

    /** 是否最高诊断。 */
    @Column(name = "highest_diag_flag")
    private Boolean highestDiagFlag;

    /** 中医证候组。 */
    @Column(name = "tcm_syndrome_group")
    private String tcmSyndromeGroup;

    /** 是否中医诊断。 */
    @Column(name = "tcm_flag")
    private Boolean tcmFlag;
}
