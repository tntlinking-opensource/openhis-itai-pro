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
 * 病案首页已提交事件（DM-MRMS-001 §5）。
 */
@Getter
@Setter
public class CaseRecordSubmittedEvent extends DomainEvent {

    /** 就诊主键。 */
    private Long encounterId;
    /** 病案号。 */
    private String medcasno;

    /** 无参构造器（反序列化用）。 */
    public CaseRecordSubmittedEvent() {
        super();
    }

    /**
     * 构造病案提交事件。
     *
     * @param caseRecordId 病案主档主键
     * @param tenantId 租户
     * @param operatorId 操作人
     * @param encounterId 就诊
     * @param medcasno 病案号
     */
    public CaseRecordSubmittedEvent(
            Long caseRecordId,
            Long tenantId,
            Long operatorId,
            Long encounterId,
            String medcasno) {
        super(caseRecordId, AggregateTypeConstants.CASE_RECORD);
        setTenantId(tenantId);
        setUserId(operatorId);
        this.encounterId = encounterId;
        this.medcasno = medcasno;
    }
}
