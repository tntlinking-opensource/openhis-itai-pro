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

package com.openhis.health.domain.share.events.administration.encounter;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 住院财务结算完成事件（出院全结：{@code PENDING→COMPLETED}，DM-ADM-002）。
 *
 * <p>发布时机：住院收费员完成出院全结且应用编排确认就诊须推进财务终态后。</p>
 */
@Getter
@Setter
public class InpatientFinancialSettlementCompletedEvent extends DomainEvent {

    /** 患者主键。 */
    private Long patientId;

    /** 结算后就诊状态码（典型 {@code COMPLETED}）。 */
    private String statusEnum;

    /** 结算业务时间（与支付主单 billDate 对齐，由应用层写入）。 */
    private LocalDateTime settlementTime;

    /** 无参构造器（反序列化用）。 */
    public InpatientFinancialSettlementCompletedEvent() {
        super();
    }

    public InpatientFinancialSettlementCompletedEvent(Long encounterId, Long patientId) {
        super(encounterId, AggregateTypeConstants.ENCOUNTER);
        this.patientId = patientId;
    }
}
