/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.medication.medicationrequest;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;


/**
 * HerbalMedicationOrderDraftCreatedEvent：health-domain-shared 跨领域共享模型。
 */
public class HerbalMedicationOrderDraftCreatedEvent extends DomainEvent {
    /** 操作人主键（HerbalMedicationOrderDraftCreated 业务上下文）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public HerbalMedicationOrderDraftCreatedEvent() {
        super();
    }

    /**
     * 处理Long id逻辑。
     * @return 返回结果
     */
    public HerbalMedicationOrderDraftCreatedEvent(Long id, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_REQUEST);
        this.operatorId = operatorId;
    }
}
