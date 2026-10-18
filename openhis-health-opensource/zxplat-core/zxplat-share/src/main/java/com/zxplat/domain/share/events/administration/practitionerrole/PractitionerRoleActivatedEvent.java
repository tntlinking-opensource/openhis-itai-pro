/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.practitionerrole;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * PractitionerRoleActivatedEvent。
 */
public class PractitionerRoleActivatedEvent extends DomainEvent {
    /**
     * practitionerId。
     */
    private Long practitionerId;
    /**
     * orgId。
     */
    private Long orgId;
    public PractitionerRoleActivatedEvent() { super(); }
    /**
     * 处理Long assignmentId, Long practitionerId, Long orgId逻辑。
     * @return 返回结果
     */
    public PractitionerRoleActivatedEvent(Long assignmentId, Long practitionerId, Long orgId) {
        super(assignmentId, AggregateTypeConstants.PRACTITIONER_ROLE);
        this.practitionerId = practitionerId;
        this.orgId = orgId;
    }
}
