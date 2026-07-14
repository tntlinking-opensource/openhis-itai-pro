/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.mrms.caserecord;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 病案已退回事件（DM-MRMS-001 §5）。
 */
@Getter
@Setter
public class CaseRecordReturnedEvent extends DomainEvent {

    /** 就诊主键。 */
    private Long encounterId;
    /** 退回原因。 */
    private String returnReason;

    /** 无参构造器（反序列化用）。 */
    public CaseRecordReturnedEvent() {
        super();
    }

    /**
     * 构造病案退回事件。
     *
     * @param caseRecordId 病案主档主键
     * @param tenantId 租户
     * @param operatorId 操作人
     * @param encounterId 就诊
     * @param returnReason 退回原因
     */
    public CaseRecordReturnedEvent(
            Long caseRecordId,
            Long tenantId,
            Long operatorId,
            Long encounterId,
            String returnReason) {
        super(caseRecordId, AggregateTypeConstants.CASE_RECORD);
        setTenantId(tenantId);
        setUserId(operatorId);
        this.encounterId = encounterId;
        this.returnReason = returnReason;
    }
}
