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
 * 病案归档审批驳回（pending_archive → draft）。
 */
@Getter
@Setter
public class CaseRecordArchiveRejectedEvent extends DomainEvent {

    /** 驳回原因。 */
    private String rejectReason;

    /** 无参构造。 */
    public CaseRecordArchiveRejectedEvent() {
        super();
    }

    /**
     * @param caseRecordId 病案主档
     * @param tenantId 租户
     * @param operatorId 驳回人
     * @param rejectReason 驳回原因
     */
    public CaseRecordArchiveRejectedEvent(
            Long caseRecordId, Long tenantId, Long operatorId, String rejectReason) {
        super(caseRecordId, AggregateTypeConstants.CASE_RECORD);
        setTenantId(tenantId);
        setUserId(operatorId);
        this.rejectReason = rejectReason;
    }
}
