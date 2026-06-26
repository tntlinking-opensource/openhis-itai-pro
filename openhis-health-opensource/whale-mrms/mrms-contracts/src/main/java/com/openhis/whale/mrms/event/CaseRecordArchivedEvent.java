/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.event;

import com.openhis.whale.core.domain.event.DomainEvent;
import com.openhis.whale.mrms.support.MrmsAggregateTypeConstants;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 病案已归档事件（首页冻结、EMR 文书锁定等下游可订阅）。
 */
@Getter
@Setter
public class CaseRecordArchivedEvent extends DomainEvent {

    private Long encounterId;
    private Long patientId;
    private LocalDateTime archivedAt;

    public CaseRecordArchivedEvent() {
        super();
    }

    public CaseRecordArchivedEvent(
            Long caseRecordId,
            Long tenantId,
            Long operatorId,
            Long encounterId,
            Long patientId,
            LocalDateTime archivedAt) {
        super(caseRecordId, MrmsAggregateTypeConstants.CASE_RECORD);
        setTenantId(tenantId);
        setUserId(operatorId);
        this.encounterId = encounterId;
        this.patientId = patientId;
        this.archivedAt = archivedAt;
    }
}
