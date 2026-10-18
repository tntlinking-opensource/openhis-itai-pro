/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.medication.medicationrequest;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 药品医嘱草稿修改事件。
 */
@Getter
@Setter
public class MedicationOrderDraftEditedEvent extends DomainEvent {
    /** 变更字段摘要。 */
    private String changedFields;

    /** 操作人主键（MedicationOrderDraftEditedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationOrderDraftEditedEvent() {
        super();
    }

    /**
     * 处理Long id逻辑。
     * @return 返回结果
     */
    public MedicationOrderDraftEditedEvent(Long id, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_REQUEST);
        this.operatorId = operatorId;
    }

    /**
     * 处理Long id, String changedFields逻辑。
     * @return 返回结果
     */
    public MedicationOrderDraftEditedEvent(Long id, String changedFields) {
        this(id, changedFields, null);
    }

    public MedicationOrderDraftEditedEvent(Long id, String changedFields, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_REQUEST);
        this.changedFields = changedFields;
        this.operatorId = operatorId;
    }
}
