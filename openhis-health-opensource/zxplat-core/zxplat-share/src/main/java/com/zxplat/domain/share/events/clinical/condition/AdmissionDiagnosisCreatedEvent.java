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
 * 入院诊断已创建事件。
 */
@Getter
@Setter
/**
 * AdmissionDiagnosisCreatedEvent。
 */
public class AdmissionDiagnosisCreatedEvent extends DomainEvent {
    /**
     * definitionId。
     */
    private Long definitionId;
    /**
     * patientId。
     */
    private Long patientId;

    public AdmissionDiagnosisCreatedEvent(Long conditionId, Long definitionId, Long patientId) {
        super(conditionId, AggregateTypeConstants.CONDITION);
        this.definitionId = definitionId;
        this.patientId = patientId;
    }
}
