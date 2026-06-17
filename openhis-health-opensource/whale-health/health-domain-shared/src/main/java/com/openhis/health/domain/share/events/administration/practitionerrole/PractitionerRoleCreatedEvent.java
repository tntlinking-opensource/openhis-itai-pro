/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 */

package com.openhis.health.domain.share.events.administration.practitionerrole;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PractitionerRoleCreatedEvent extends DomainEvent {

    private Long practitionerId;
    private Long orgId;
    /** 平台角色主键 {@code whale_roles.id}。 */
    private Long roleId;

    public PractitionerRoleCreatedEvent() {
        super();
    }

    public PractitionerRoleCreatedEvent(Long assignmentId, Long practitionerId, Long orgId, Long roleId) {
        super(assignmentId, AggregateTypeConstants.PRACTITIONER_ROLE);
        this.practitionerId = practitionerId;
        this.orgId = orgId;
        this.roleId = roleId;
    }
}
