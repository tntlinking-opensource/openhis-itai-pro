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

import java.util.List;

@Getter
@Setter
public class PractitionerRoleBatchReplacedEvent extends DomainEvent {

    /** 平台角色主键 {@code whale_roles.id}。 */
    private Long roleId;
    private List<Long> replacedAssignmentIds;

    public PractitionerRoleBatchReplacedEvent() {
        super();
    }

    public PractitionerRoleBatchReplacedEvent(Long practitionerId, Long roleId, List<Long> replacedAssignmentIds) {
        super(practitionerId, AggregateTypeConstants.PRACTITIONER_ROLE);
        this.roleId = roleId;
        this.replacedAssignmentIds = replacedAssignmentIds;
    }
}
