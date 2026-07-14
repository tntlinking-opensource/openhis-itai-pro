/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.medication.medicationrequest;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 药品医嘱取消事件。
 */
@Getter
@Setter
public class MedicationOrderCancelledEvent extends DomainEvent {
    /** 取消原因。 */
    private String cancelReason;

    /** 操作人主键（MedicationOrderCancelledEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationOrderCancelledEvent() {
        super();
    }

    /**
     * 处理Long id逻辑。
     * @return 返回结果
     */
    public MedicationOrderCancelledEvent(Long id, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_REQUEST);
        this.operatorId = operatorId;
    }

    /**
     * 处理Long id, String cancelReason逻辑。
     * @return 返回结果
     */
    public MedicationOrderCancelledEvent(Long id, String cancelReason) {
        this(id, cancelReason, null);
    }

    public MedicationOrderCancelledEvent(Long id, String cancelReason, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_REQUEST);
        this.cancelReason = cancelReason;
        this.operatorId = operatorId;
    }
}
