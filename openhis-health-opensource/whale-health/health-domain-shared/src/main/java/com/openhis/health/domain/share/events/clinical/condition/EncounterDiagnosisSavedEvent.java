/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.clinical.condition;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 就诊诊断已保存事件。
 */
@Getter
@Setter
/**
 * EncounterDiagnosisSavedEvent。
 */
public class EncounterDiagnosisSavedEvent extends DomainEvent {
    /**
     * patientId。
     */
    private Long patientId;
    /**
     * definitionId。
     */
    private Long definitionId;
    /**
     * verificationStatusEnum。
     */
    private String verificationStatusEnum;
    /**
     * clinicalStatusEnum。
     */
    private String clinicalStatusEnum;

    public EncounterDiagnosisSavedEvent(Long conditionId, Long patientId, Long definitionId,
                                        String verificationStatusEnum, String clinicalStatusEnum) {
        super(conditionId, AggregateTypeConstants.CONDITION);
        this.patientId = patientId;
        this.definitionId = definitionId;
        this.verificationStatusEnum = verificationStatusEnum;
        this.clinicalStatusEnum = clinicalStatusEnum;
    }
}
