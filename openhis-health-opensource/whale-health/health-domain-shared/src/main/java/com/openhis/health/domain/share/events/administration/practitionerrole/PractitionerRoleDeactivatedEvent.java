/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.practitionerrole;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
/**
 * PractitionerRoleDeactivatedEvent。
 */
public class PractitionerRoleDeactivatedEvent extends DomainEvent {
    /**
     * reason。
     */
    private String reason;
    public PractitionerRoleDeactivatedEvent() { super(); }
    /**
     * 处理Long assignmentId, String reason逻辑。
     * @return 返回结果
     */
    public PractitionerRoleDeactivatedEvent(Long assignmentId, String reason) {
        super(assignmentId, AggregateTypeConstants.PRACTITIONER_ROLE);
        this.reason = reason;
    }
}
