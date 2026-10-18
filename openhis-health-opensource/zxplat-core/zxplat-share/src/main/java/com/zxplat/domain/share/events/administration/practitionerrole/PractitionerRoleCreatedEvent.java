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
 * PractitionerRoleCreatedEvent：health-domain-shared 跨领域共享模型。
 */
public class PractitionerRoleCreatedEvent extends DomainEvent {

    /** 医务人员主键（PractitionerRoleCreated 业务上下文）。 */
    private Long practitionerId;
    private Long orgId;
    /** 平台角色主键 {@code zxplat_roles.id}。 */
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
