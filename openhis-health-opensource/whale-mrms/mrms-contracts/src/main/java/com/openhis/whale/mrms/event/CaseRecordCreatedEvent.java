/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.event;

import com.openhis.whale.core.domain.event.DomainEvent;
import com.openhis.whale.mrms.support.MrmsAggregateTypeConstants;
import lombok.Getter;
import lombok.Setter;

/**
 * 病案主档已创建事件。
 */
@Getter
@Setter
public class CaseRecordCreatedEvent extends DomainEvent {

    private Long patientId;
    private Long encounterId;
    private String medcasno;

    public CaseRecordCreatedEvent() {
        super();
    }

    public CaseRecordCreatedEvent(
            Long caseRecordId,
            Long tenantId,
            Long operatorId,
            Long patientId,
            Long encounterId,
            String medcasno) {
        super(caseRecordId, MrmsAggregateTypeConstants.CASE_RECORD);
        setTenantId(tenantId);
        setUserId(operatorId);
        this.patientId = patientId;
        this.encounterId = encounterId;
        this.medcasno = medcasno;
    }
}
