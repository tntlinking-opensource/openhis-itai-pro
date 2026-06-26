/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.event;

import com.openhis.whale.core.domain.event.DomainEvent;
import com.openhis.whale.mrms.support.MrmsAggregateTypeConstants;
import lombok.Getter;
import lombok.Setter;

/**
 * 病案已接收事件。
 */
@Getter
@Setter
public class CaseRecordReceivedEvent extends DomainEvent {

    private Long encounterId;

    public CaseRecordReceivedEvent() {
        super();
    }

    public CaseRecordReceivedEvent(
            Long caseRecordId,
            Long tenantId,
            Long operatorId,
            Long encounterId) {
        super(caseRecordId, MrmsAggregateTypeConstants.CASE_RECORD);
        setTenantId(tenantId);
        setUserId(operatorId);
        this.encounterId = encounterId;
    }
}
