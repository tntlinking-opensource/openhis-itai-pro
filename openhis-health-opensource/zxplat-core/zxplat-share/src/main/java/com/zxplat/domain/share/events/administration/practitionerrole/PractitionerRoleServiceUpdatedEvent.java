/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.practitionerrole;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
/**
 * PractitionerRoleServiceUpdatedEvent。
 */
public class PractitionerRoleServiceUpdatedEvent extends DomainEvent {
    /**
     * healthcareServiceIds。
     */
    private List<Long> healthcareServiceIds;
    public PractitionerRoleServiceUpdatedEvent() { super(); }
    /**
     * 处理Long assignmentId, List<Long> healthcareServiceIds逻辑。
     * @return 返回结果
     */
    public PractitionerRoleServiceUpdatedEvent(Long assignmentId, List<Long> healthcareServiceIds) {
        super(assignmentId, AggregateTypeConstants.PRACTITIONER_ROLE);
        this.healthcareServiceIds = healthcareServiceIds;
    }
}
