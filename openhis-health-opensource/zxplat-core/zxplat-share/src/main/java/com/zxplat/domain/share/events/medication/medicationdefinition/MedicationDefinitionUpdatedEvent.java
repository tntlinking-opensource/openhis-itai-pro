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
 * 药品定义更新事件。
 */
@Getter
@Setter
public class MedicationDefinitionUpdatedEvent extends DomainEvent {
    /** 变更字段摘要。 */
    private String changedFields;

    /** 操作人主键（MedicationDefinitionUpdatedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationDefinitionUpdatedEvent() {
        super();
    }

    /**
     * 处理Long id, String changedFields逻辑。
     * @return 返回结果
     */
    public MedicationDefinitionUpdatedEvent(Long id, String changedFields, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_DEFINITION);
        this.changedFields = changedFields;
        this.operatorId = operatorId;
    }
}
