/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.mrms.caserecord;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 病案已接收事件（DM-MRMS-001 §5）。
 */
@Getter
@Setter
public class CaseRecordReceivedEvent extends DomainEvent {

    /** 就诊主键。 */
    private Long encounterId;

    /** 无参构造器（反序列化用）。 */
    public CaseRecordReceivedEvent() {
        super();
    }

    /**
     * 构造病案接收事件。
     *
     * @param caseRecordId 病案主档主键
     * @param tenantId 租户
     * @param operatorId 操作人
     * @param encounterId 就诊
     */
    public CaseRecordReceivedEvent(
            Long caseRecordId,
            Long tenantId,
            Long operatorId,
            Long encounterId) {
        super(caseRecordId, AggregateTypeConstants.CASE_RECORD);
        setTenantId(tenantId);
        setUserId(operatorId);
        this.encounterId = encounterId;
    }
}
