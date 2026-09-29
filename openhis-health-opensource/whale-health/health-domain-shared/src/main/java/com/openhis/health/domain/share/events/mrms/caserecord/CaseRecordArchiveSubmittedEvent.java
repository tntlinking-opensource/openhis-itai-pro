/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.mrms.caserecord;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 病案已提交归档审批（draft → pending_archive）。
 */
@Getter
@Setter
public class CaseRecordArchiveSubmittedEvent extends DomainEvent {

    /** 就诊主键。 */
    private Long encounterId;
    /** 患者主键。 */
    private Long patientId;
    /** 提交时间。 */
    private LocalDateTime submittedAt;

    /** 无参构造。 */
    public CaseRecordArchiveSubmittedEvent() {
        super();
    }

    /**
     * @param caseRecordId 病案主档
     * @param tenantId 租户
     * @param operatorId 提交人
     * @param encounterId 就诊
     * @param patientId 患者
     * @param submittedAt 提交时间
     */
    public CaseRecordArchiveSubmittedEvent(
            Long caseRecordId,
            Long tenantId,
            Long operatorId,
            Long encounterId,
            Long patientId,
            LocalDateTime submittedAt) {
        super(caseRecordId, AggregateTypeConstants.CASE_RECORD);
        setTenantId(tenantId);
        setUserId(operatorId);
        this.encounterId = encounterId;
        this.patientId = patientId;
        this.submittedAt = submittedAt;
    }
}
