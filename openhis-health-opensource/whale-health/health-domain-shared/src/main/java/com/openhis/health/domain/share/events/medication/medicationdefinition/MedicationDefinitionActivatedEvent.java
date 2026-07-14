/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.medication.medicationdefinition;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 药品定义重新启用事件。
 */
@Getter
@Setter
public class MedicationDefinitionActivatedEvent extends DomainEvent {
    /** 启用后状态编码。 */
    private String statusEnum;

    /** 操作人主键（MedicationDefinitionActivatedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationDefinitionActivatedEvent() {
        super();
    }

    /**
     * 处理Long id, String statusEnum逻辑。
     * @return 返回结果
     */
    public MedicationDefinitionActivatedEvent(Long id, String statusEnum, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DEFINITION);
        this.statusEnum = statusEnum;
        this.operatorId = operatorId;
    }
}
