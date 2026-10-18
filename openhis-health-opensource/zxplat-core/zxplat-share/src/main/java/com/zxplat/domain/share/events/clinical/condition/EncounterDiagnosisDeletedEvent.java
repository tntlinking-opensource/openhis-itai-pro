/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.clinical.condition;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 就诊诊断已删除事件。
 */
@Getter
@Setter
/**
 * EncounterDiagnosisDeletedEvent。
 */
public class EncounterDiagnosisDeletedEvent extends DomainEvent {
    /**
     * patientId。
     */
    private Long patientId;

    public EncounterDiagnosisDeletedEvent(Long conditionId, Long patientId) {
        super(conditionId, AggregateTypeConstants.CONDITION);
        this.patientId = patientId;
    }
}
