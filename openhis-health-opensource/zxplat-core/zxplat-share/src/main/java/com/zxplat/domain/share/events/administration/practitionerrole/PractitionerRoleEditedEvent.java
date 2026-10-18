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
 * PractitionerRoleEditedEvent。
 */
public class PractitionerRoleEditedEvent extends DomainEvent {
    /**
     * changedFields。
     */
    private List<String> changedFields;
    public PractitionerRoleEditedEvent() { super(); }
    /**
     * 处理Long assignmentId, List<String> changedFields逻辑。
     * @return 返回结果
     */
    public PractitionerRoleEditedEvent(Long assignmentId, List<String> changedFields) {
        super(assignmentId, AggregateTypeConstants.PRACTITIONER_ROLE);
        this.changedFields = changedFields;
    }
}
