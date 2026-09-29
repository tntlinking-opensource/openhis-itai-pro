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
 * 首页编辑申请已交审。
 */
@Getter
@Setter
public class HomepageEditSubmittedEvent extends DomainEvent {

    /** 病案主档主键。 */
    private Long caseRecordId;
    /** 旧版本号。 */
    private Integer fromVersion;
    /** 新版本号。 */
    private Integer toVersion;

    /** 无参构造。 */
    public HomepageEditSubmittedEvent() {
        super();
    }

    /**
     * @param applicationId 申请主键
     * @param caseRecordId 病案主档
     * @param fromVersion 旧版本
     * @param toVersion 新版本
     * @param tenantId 租户
     * @param operatorId 操作人
     */
    public HomepageEditSubmittedEvent(
            Long applicationId,
            Long caseRecordId,
            Integer fromVersion,
            Integer toVersion,
            Long tenantId,
            Long operatorId) {
        super(applicationId, AggregateTypeConstants.HOMEPAGE_EDIT_APPLICATION);
        setTenantId(tenantId);
        setUserId(operatorId);
        this.caseRecordId = caseRecordId;
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
    }
}
