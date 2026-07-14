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

@Getter
@Setter

/**
 * LongTermMedicationOrderStoppedEvent：health-domain-shared 跨领域共享模型。
 */
public class LongTermMedicationOrderStoppedEvent extends DomainEvent {
    /** effectiveDoseEnd（LongTermMedicationOrderStopped 业务上下文）。 */
    private LocalDateTime effectiveDoseEnd;

    /** 操作人主键（LongTermMedicationOrderStoppedEvent）。 */
    private Long operatorId;

    /** 停嘱后请求状态（{@link com.openhis.health.domain.share.enums.RequestStatus#getCode()}，如 STOPPED）。 */
    private String statusEnum;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public LongTermMedicationOrderStoppedEvent() {
        super();
    }

    /**
     * 处理Long id, LocalDateTime effectiveDoseEnd逻辑。
     * @return 返回结果
     */
    public LongTermMedicationOrderStoppedEvent(
        Long id, LocalDateTime effectiveDoseEnd, Long operatorId, String statusEnum) {
        super(id, AggregateTypeConstants.MEDICATION_REQUEST);
        this.effectiveDoseEnd = effectiveDoseEnd;
        this.operatorId = operatorId;
        this.statusEnum = statusEnum;
    }
}
