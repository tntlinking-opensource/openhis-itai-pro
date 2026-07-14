/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.practitionerrole;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter

/**
 * PractitionerRoleBatchReplacedEvent：health-domain-shared 跨领域共享模型。
 */
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
