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
 * 首页编辑申请已驳回（终态）。
 */
@Getter
@Setter
public class HomepageEditRejectedEvent extends DomainEvent {

    /** 病案主档主键。 */
    private Long caseRecordId;
    /** 驳回原因。 */
    private String rejectReason;

    /** 无参构造。 */
    public HomepageEditRejectedEvent() {
        super();
    }

    /**
     * @param applicationId 申请主键
     * @param caseRecordId 病案主档
     * @param rejectReason 驳回原因
     * @param tenantId 租户
     * @param reviewerId 审核人
     */
    public HomepageEditRejectedEvent(
            Long applicationId,
            Long caseRecordId,
            String rejectReason,
            Long tenantId,
            Long reviewerId) {
        super(applicationId, AggregateTypeConstants.HOMEPAGE_EDIT_APPLICATION);
        setTenantId(tenantId);
        setUserId(reviewerId);
        this.caseRecordId = caseRecordId;
        this.rejectReason = rejectReason;
    }
}
