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
 * 病案主档已创建事件（DM-MRMS-001 §5）。
 */
@Getter
@Setter
public class CaseRecordCreatedEvent extends DomainEvent {

    /** 患者主键。 */
    private Long patientId;
    /** 就诊主键。 */
    private Long encounterId;
    /** 病案号（bootstrap 建档时可能为空，首次保存首页后才有值）。 */
    private String medcasno;

    /** 无参构造器（反序列化用）。 */
    public CaseRecordCreatedEvent() {
        super();
    }

    /**
     * 构造病案创建事件。
     *
     * @param caseRecordId 病案主档主键
     * @param tenantId 租户
     * @param operatorId 操作人
     * @param patientId 患者
     * @param encounterId 就诊
     * @param medcasno 病案号（可空）
     */
    public CaseRecordCreatedEvent(
            Long caseRecordId,
            Long tenantId,
            Long operatorId,
            Long patientId,
            Long encounterId,
            String medcasno) {
        super(caseRecordId, AggregateTypeConstants.CASE_RECORD);
        setTenantId(tenantId);
        setUserId(operatorId);
        this.patientId = patientId;
        this.encounterId = encounterId;
        this.medcasno = medcasno;
    }
}
