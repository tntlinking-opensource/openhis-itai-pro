/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.mrms.homepageedit;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 首页编辑申请已通过。
 */
@Getter
@Setter
public class HomepageEditApprovedEvent extends DomainEvent {

    /** 病案主档主键。 */
    private Long caseRecordId;
    /** 旧版本号。 */
    private Integer fromVersion;
    /** 新版本号。 */
    private Integer toVersion;

    /** 无参构造。 */
    public HomepageEditApprovedEvent() {
        super();
    }

    /**
     * @param applicationId 申请主键
     * @param caseRecordId 病案主档
     * @param fromVersion 旧版本
     * @param toVersion 新版本
     * @param tenantId 租户
     * @param reviewerId 审核人
     */
    public HomepageEditApprovedEvent(
            Long applicationId,
            Long caseRecordId,
            Integer fromVersion,
            Integer toVersion,
            Long tenantId,
            Long reviewerId) {
        super(applicationId, AggregateTypeConstants.HOMEPAGE_EDIT_APPLICATION);
        setTenantId(tenantId);
        setUserId(reviewerId);
        this.caseRecordId = caseRecordId;
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
    }
}
