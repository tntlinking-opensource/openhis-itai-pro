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
 * PractitionerRoleAbolishedEvent。
 */
public class PractitionerRoleAbolishedEvent extends DomainEvent {
    /**
     * practitionerId。
     */
    private Long practitionerId;
    public PractitionerRoleAbolishedEvent() { super(); }
    /**
     * 处理Long assignmentId, Long practitionerId逻辑。
     * @return 返回结果
     */
    public PractitionerRoleAbolishedEvent(Long assignmentId, Long practitionerId) {
        super(assignmentId, AggregateTypeConstants.PRACTITIONER_ROLE);
        this.practitionerId = practitionerId;
    }
}
