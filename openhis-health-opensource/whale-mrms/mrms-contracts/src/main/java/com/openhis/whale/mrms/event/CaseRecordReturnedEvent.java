/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.event;

import com.openhis.whale.core.domain.event.DomainEvent;
import com.openhis.whale.mrms.support.MrmsAggregateTypeConstants;
import lombok.Getter;
import lombok.Setter;

/**
 * 病案已退回事件。
 */
@Getter
@Setter
public class CaseRecordReturnedEvent extends DomainEvent {

    private Long encounterId;
    private String returnReason;

    public CaseRecordReturnedEvent() {
        super();
    }

    public CaseRecordReturnedEvent(
            Long caseRecordId,
            Long tenantId,
            Long operatorId,
            Long encounterId,
            String returnReason) {
        super(caseRecordId, MrmsAggregateTypeConstants.CASE_RECORD);
        setTenantId(tenantId);
        setUserId(operatorId);
        this.encounterId = encounterId;
        this.returnReason = returnReason;
    }
}
