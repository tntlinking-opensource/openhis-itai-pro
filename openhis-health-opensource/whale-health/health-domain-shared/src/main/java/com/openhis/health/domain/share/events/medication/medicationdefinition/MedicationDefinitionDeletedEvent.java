/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.medication.medicationdefinition;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;

/**
 * 药品定义废弃事件。
 */
public class MedicationDefinitionDeletedEvent extends DomainEvent {

    /** 操作人主键（MedicationDefinitionDeletedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationDefinitionDeletedEvent() {
        super();
    }

    /**
     * 处理Long id逻辑。
     * @return 返回结果
     */
    public MedicationDefinitionDeletedEvent(Long id, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DEFINITION);
        this.operatorId = operatorId;
    }
}
