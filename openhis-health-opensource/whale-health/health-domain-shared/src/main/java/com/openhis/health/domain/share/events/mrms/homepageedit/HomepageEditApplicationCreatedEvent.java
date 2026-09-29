/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.mrms.homepageedit;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 首页编辑申请已创建。
 */
@Getter
@Setter
public class HomepageEditApplicationCreatedEvent extends DomainEvent {

    /** 病案主档主键。 */
    private Long caseRecordId;

    /** 无参构造。 */
    public HomepageEditApplicationCreatedEvent() {
        super();
    }

    /**
     * @param applicationId 申请主键
     * @param caseRecordId 病案主档
     * @param tenantId 租户
     * @param applicantId 申请人
     */
    public HomepageEditApplicationCreatedEvent(
            Long applicationId, Long caseRecordId, Long tenantId, Long applicantId) {
        super(applicationId, AggregateTypeConstants.HOMEPAGE_EDIT_APPLICATION);
        setTenantId(tenantId);
        setUserId(applicantId);
        this.caseRecordId = caseRecordId;
    }
}
