/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.events.workflow.appointment;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 队列顺序已调整事件。
 *
 * <p>SDD-WKF-002 §7 最小载荷：appointmentId（via super.aggregateId）、oldOrder、newOrder。</p>
 */
@Getter
@Setter
public class QueueOrderChangedEvent extends DomainEvent {

    /** 调序前的排队序号。 */
    private Integer oldOrder;

    /** 调序后的排队序号。 */
    private Integer newOrder;

    /**
     * 默认构造函数（反序列化用）。
     */
    public QueueOrderChangedEvent() {
        super();
    }

    /**
     * 构造队列顺序已调整事件。
     *
     * @param appointmentId 候诊/预约项主键（aggregateId）
     * @param oldOrder      调序前排队序号
     * @param newOrder      调序后排队序号
     */
    public QueueOrderChangedEvent(Long appointmentId, Integer oldOrder, Integer newOrder) {
        super(appointmentId, AggregateTypeConstants.APPOINTMENT);
        this.oldOrder = oldOrder;
        this.newOrder = newOrder;
    }
}
