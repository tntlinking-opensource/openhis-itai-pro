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
 * 就诊诊断关联移除事件：供读模型覆盖 {@code diagnosis_json}。
 */
@Getter
@Setter
public class EncounterDiagnosisAssociationRemovedEvent extends DomainEvent {

    /** 被移除的就诊诊断关联行主键 {@code adm_encounter_diagnosis.id}。 */
    private Long encounterDiagnosisId;

    /**
     * 删除后的就诊维度诊断列表快照 JSON（数组），供读模型覆盖 {@code diagnosis_json}。
     */
    private String diagnosisJson;

    /**
     * 主诊断定义名称摘要；无记录时可为空。
     */
    private String conditionDefinitionName;

    /** 无参构造，供序列化框架使用。 */
    public EncounterDiagnosisAssociationRemovedEvent() {
        super();
    }

    /**
     * 构造就诊诊断关联移除事件载荷。
     *
     * @param encounterId 就诊主键
     * @param encounterDiagnosisId 被软删的就诊诊断关联行主键
     */
    public EncounterDiagnosisAssociationRemovedEvent(Long encounterId, Long encounterDiagnosisId) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.encounterDiagnosisId = encounterDiagnosisId;
    }
}
