/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.medication.medicationdefinition;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 药品定义停用事件。
 */
@Getter
@Setter
public class MedicationDefinitionRetiredEvent extends DomainEvent {
    /** 停用后状态编码。 */
    private String statusEnum;

    /** 操作人主键（MedicationDefinitionRetiredEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationDefinitionRetiredEvent() {
        super();
    }

    /**
     * 处理Long id, String statusEnum逻辑。
     * @return 返回结果
     */
    public MedicationDefinitionRetiredEvent(Long id, String statusEnum, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DEFINITION);
        this.statusEnum = statusEnum;
        this.operatorId = operatorId;
    }
}
