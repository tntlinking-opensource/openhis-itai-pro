/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.medication.medicationrequest;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter

/**
 * LongTermMedicationOrderStoppedEvent：health-domain-shared 跨领域共享模型。
 */
public class LongTermMedicationOrderStoppedEvent extends DomainEvent {
    /**
     * 停嘱时间（落库 {@code stop_time}）。
     */
    private LocalDateTime stopTime;

    /** 操作人主键（LongTermMedicationOrderStoppedEvent）。 */
    private Long operatorId;

    /** 停嘱后请求状态（{@link com.zxplat.domain.share.enums.RequestStatus#getCode()}，如 STOPPED）。 */
    private String statusEnum;

    /**
     * 无参构造（反序列化/框架用）。
     */
    public LongTermMedicationOrderStoppedEvent() {
        super();
    }

    /**
     * 构造长期药品医嘱已停嘱事件。
     *
     * @param id         药品申请主键
     * @param stopTime   停嘱时间
     * @param operatorId 操作人主键
     * @param statusEnum 停嘱后状态码
     */
    public LongTermMedicationOrderStoppedEvent(
        Long id, LocalDateTime stopTime, Long operatorId, String statusEnum) {
        super(id, AggregateTypeConstants.MEDICATION_REQUEST);
        this.stopTime = stopTime;
        this.operatorId = operatorId;
        this.statusEnum = statusEnum;
    }
}
