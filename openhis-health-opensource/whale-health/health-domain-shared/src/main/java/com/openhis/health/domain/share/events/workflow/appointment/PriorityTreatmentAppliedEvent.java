/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.workflow.appointment;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 优先就诊已申请事件。
 *
 * <p>SDD-WKF-002 §7 最小载荷：appointmentId（via super.aggregateId）、oldOrder、newOrder。</p>
 */
@Getter
@Setter
public class PriorityTreatmentAppliedEvent extends DomainEvent {

    /** 调序前的排队序号。 */
    private Integer oldOrder;

    /** 调序后的排队序号（优先后通常为 1）。 */
    private Integer newOrder;

    /**
     * 默认构造函数（反序列化用）。
     */
    public PriorityTreatmentAppliedEvent() {
        super();
    }

    /**
     * 构造优先就诊已申请事件。
     *
     * @param appointmentId 候诊/预约项主键（aggregateId）
     * @param oldOrder      调序前排队序号
     * @param newOrder      调序后排队序号
     */
    public PriorityTreatmentAppliedEvent(Long appointmentId, Integer oldOrder, Integer newOrder) {
        super(appointmentId, AggregateTypeConstants.APPOINTMENT);
        this.oldOrder = oldOrder;
        this.newOrder = newOrder;
    }
}
