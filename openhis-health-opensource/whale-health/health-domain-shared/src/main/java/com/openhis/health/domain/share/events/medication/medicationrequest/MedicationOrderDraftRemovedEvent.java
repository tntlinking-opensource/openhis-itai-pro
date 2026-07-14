/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.medication.medicationrequest;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

/**
 * MedicationOrderDraftRemovedEvent：health-domain-shared 跨领域共享模型。
 */
public class MedicationOrderDraftRemovedEvent extends DomainEvent {
    /** 就诊主键（MedicationOrderDraftRemoved 业务上下文）。 */
    private Long encounterId;

    /** 操作人主键（MedicationOrderDraftRemovedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationOrderDraftRemovedEvent() {
        super();
    }

    /**
     * 处理Long id, Long encounterId逻辑。
     * @return 返回结果
     */
    public MedicationOrderDraftRemovedEvent(Long id, Long encounterId, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_REQUEST);
        this.encounterId = encounterId;
        this.operatorId = operatorId;
    }
}
