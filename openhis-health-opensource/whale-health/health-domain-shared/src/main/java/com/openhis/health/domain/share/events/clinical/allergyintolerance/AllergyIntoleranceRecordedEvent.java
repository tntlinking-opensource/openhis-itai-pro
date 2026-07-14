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
 * 过敏记录已保存事件。
 */
@Getter
@Setter
public class AllergyIntoleranceRecordedEvent extends DomainEvent {
    /** 就诊主键（AllergyIntoleranceRecordedEvent）。 */
    private Long encounterId;
    /** 患者主键（AllergyIntoleranceRecordedEvent）。 */
    private Long patientId;
    /** medication主键（AllergyIntoleranceRecordedEvent）。 */
    private Long medicationId;
    /** clinical状态编码（AllergyIntoleranceRecordedEvent）。 */
    private String clinicalStatusEnum;
    /** verification状态编码（AllergyIntoleranceRecordedEvent）。 */
    private String verificationStatusEnum;

    /**
     * 构造事件。
     */
    public AllergyIntoleranceRecordedEvent(Long id, Long encounterId, Long patientId, Long medicationId,
                                           String clinicalStatusEnum, String verificationStatusEnum) {
        super(id, AggregateTypeConstants.ALLERGY_INTOLERANCE);
        this.encounterId = encounterId;
        this.patientId = patientId;
        this.medicationId = medicationId;
        this.clinicalStatusEnum = clinicalStatusEnum;
        this.verificationStatusEnum = verificationStatusEnum;
    }
}
