/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.medication.medicationrequest;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 门诊收费完成后药品医嘱置「已完成」事件。
 *
 * <p>与住院核对产生的 {@link MedicationOrderVerifiedEvent} 区分：门诊收费链路直接将已发送医嘱推进为 COMPLETED。</p>
 */
@Getter
@Setter
public class MedicationOrderCompletedAfterOutpatientChargeEvent extends DomainEvent {

    /** 操作人主键（MedicationOrderCompletedAfterOutpatientChargeEvent）。 */
    private Long operatorId;

    /** completed时间（MedicationOrderCompletedAfterOutpatientChargeEvent）。 */
    private LocalDateTime completedTime;

    public MedicationOrderCompletedAfterOutpatientChargeEvent() {
        super();
    }

    public MedicationOrderCompletedAfterOutpatientChargeEvent(Long medicationRequestId, Long operatorId, LocalDateTime completedTime) {
        super(medicationRequestId, AggregateTypeConstants.MEDICATION_REQUEST);
        this.operatorId = operatorId;
        this.completedTime = completedTime;
    }
}
