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
 * 药品医嘱重开事件。
 */
@Getter
@Setter
public class MedicationOrderReopenedEvent extends DomainEvent {
    /** 被重开的原始医嘱标识。 */
    private Long originRequestId;

    /** 操作人主键（MedicationOrderReopenedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public MedicationOrderReopenedEvent() {
        super();
    }

    /**
     * 处理Long id逻辑。
     * @return 返回结果
     */
    public MedicationOrderReopenedEvent(Long id, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_REQUEST);
        this.operatorId = operatorId;
    }

    /**
     * 处理Long id, Long originRequestId, Long operatorId逻辑。
     * @return 返回结果
     */
    public MedicationOrderReopenedEvent(Long id, Long originRequestId, Long operatorId) {
        super(id, AggregateTypeConstants.MEDICATION_REQUEST);
        this.originRequestId = originRequestId;
        this.operatorId = operatorId;
    }
}
