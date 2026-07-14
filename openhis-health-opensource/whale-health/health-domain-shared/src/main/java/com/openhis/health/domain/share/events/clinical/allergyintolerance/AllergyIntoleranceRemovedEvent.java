/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.clinical.allergyintolerance;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 过敏记录已删除事件。
 */
@Getter
@Setter
public class AllergyIntoleranceRemovedEvent extends DomainEvent {
    /** 就诊主键（AllergyIntoleranceRemovedEvent）。 */
    private Long encounterId;
    /** 患者主键（AllergyIntoleranceRemovedEvent）。 */
    private Long patientId;

    /**
     * 构造事件。
     */
    public AllergyIntoleranceRemovedEvent(Long id, Long encounterId, Long patientId) {
        super(id, AggregateTypeConstants.ALLERGY_INTOLERANCE);
        this.encounterId = encounterId;
        this.patientId = patientId;
    }
}
