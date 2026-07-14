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
 * 就诊诊断关联维护事件。
 */
@Getter
@Setter
public class EncounterDiagnosisAssociationUpsertedEvent extends DomainEvent {

    /** condition主键（EncounterDiagnosisAssociationUpsertedEvent）。 */
    private Long conditionId;
    /** primaryFlag（EncounterDiagnosisAssociationUpsertedEvent）。 */
    private Boolean primaryFlag;
    /** rank（EncounterDiagnosisAssociationUpsertedEvent）。 */
    private Integer rank;

    /**
     * 就诊维度诊断列表快照 JSON（数组），供门诊信息/挂号读模型投影覆盖 {@code diagnosis_json}。
     */
    private String diagnosisJson;

    /**
     * 主诊断定义名称摘要（挂号列表等展示）；无主诊时取首条诊断定义名称。
     */
    private String conditionDefinitionName;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public EncounterDiagnosisAssociationUpsertedEvent() {
        super();
    }

    /**
     * 处理Long encounterId, Long conditionId, Boolean primaryFlag, Integer rank逻辑。
     * @return 返回结果
     */
    public EncounterDiagnosisAssociationUpsertedEvent(Long encounterId, Long conditionId, Boolean primaryFlag, Integer rank) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.conditionId = conditionId;
        this.primaryFlag = primaryFlag;
        this.rank = rank;
    }
}
