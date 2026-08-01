/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.encounter;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 就诊诊断关联维护事件：供门诊信息/挂号读模型投影诊断快照。
 */
@Getter
@Setter
public class EncounterDiagnosisAssociationUpsertedEvent extends DomainEvent {

    /** 就诊诊断关联行主键 {@code adm_encounter_diagnosis.id}。 */
    private Long encounterDiagnosisId;
    /** 诊断目录主键 {@code cln_condition_definition.id}。 */
    private Long conditionDefinitionId;
    /** 是否主诊断。 */
    private Boolean primaryFlag;
    /** 排序序号。 */
    private Integer rank;

    /**
     * 就诊维度诊断列表快照 JSON（数组），供门诊信息/挂号读模型投影覆盖 {@code diagnosis_json}。
     */
    private String diagnosisJson;

    /**
     * 主诊断定义名称摘要（挂号列表等展示）；无主诊时取首条诊断定义名称。
     */
    private String conditionDefinitionName;

    /** 无参构造，供序列化框架使用。 */
    public EncounterDiagnosisAssociationUpsertedEvent() {
        super();
    }

    /**
     * 构造就诊诊断关联 upsert 事件载荷。
     *
     * @param encounterId 就诊主键
     * @param encounterDiagnosisId 就诊诊断关联行主键
     * @param conditionDefinitionId 诊断目录主键
     * @param primaryFlag 是否主诊断
     * @param rank 排序序号
     */
    public EncounterDiagnosisAssociationUpsertedEvent(
        Long encounterId,
        Long encounterDiagnosisId,
        Long conditionDefinitionId,
        Boolean primaryFlag,
        Integer rank) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.encounterDiagnosisId = encounterDiagnosisId;
        this.conditionDefinitionId = conditionDefinitionId;
        this.primaryFlag = primaryFlag;
        this.rank = rank;
    }
}
