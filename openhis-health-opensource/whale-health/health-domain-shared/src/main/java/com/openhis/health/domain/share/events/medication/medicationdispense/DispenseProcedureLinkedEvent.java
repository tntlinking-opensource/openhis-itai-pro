/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
