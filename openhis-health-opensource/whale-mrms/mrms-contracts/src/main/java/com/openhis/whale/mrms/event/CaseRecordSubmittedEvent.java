/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.event;

import com.openhis.whale.core.domain.event.DomainEvent;
import com.openhis.whale.mrms.support.MrmsAggregateTypeConstants;
import lombok.Getter;
import lombok.Setter;

/**
 * 病案首页已提交事件。
 */
@Getter
@Setter
public class CaseRecordSubmittedEvent extends DomainEvent {

    private Long encounterId;
    private String medcasno;

    public CaseRecordSubmittedEvent() {
        super();
    }

    public CaseRecordSubmittedEvent(
            Long caseRecordId,
            Long tenantId,
            Long operatorId,
            Long encounterId,
            String medcasno) {
        super(caseRecordId, MrmsAggregateTypeConstants.CASE_RECORD);
        setTenantId(tenantId);
        setUserId(operatorId);
        this.encounterId = encounterId;
        this.medcasno = medcasno;
    }
}
