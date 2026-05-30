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

package com.openhis.health.domain.share.events.medication.medicationdispense;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 发放单挂接执行记录并推进待配药事件（临时医嘱执行路径）。
 */
@Getter
@Setter
public class DispenseProcedureLinkedEvent extends DomainEvent {
    /** 药品申请主键。 */
    private Long medReqId;
    /** 执行记录主键。 */
    private Long procedureId;
    /** 计划发药时刻。 */
    private LocalDateTime plannedDispenseTime;
    /** 操作人主键。 */
    private Long operatorId;
    /** 发放状态码（DispenseStatus#getCode()）。 */
    private String statusEnumCode;

    /** 无参构造器（反序列化用）。 */
    public DispenseProcedureLinkedEvent() {
        super();
    }

    public DispenseProcedureLinkedEvent(
        Long dispenseId, Long medReqId, Long procedureId, Long operatorId, String statusEnumCode) {
        super(dispenseId, AggregateTypeConstants.MEDICATION_DISPENSE);
        this.medReqId = medReqId;
        this.procedureId = procedureId;
        this.operatorId = operatorId;
        this.statusEnumCode = statusEnumCode;
    }
}
