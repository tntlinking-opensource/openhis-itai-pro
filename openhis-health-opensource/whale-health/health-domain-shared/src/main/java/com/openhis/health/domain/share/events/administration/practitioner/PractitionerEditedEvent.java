/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.practitioner;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter

/**
 * PractitionerEditedEvent：health-domain-shared 跨领域共享模型。
 */
public class PractitionerEditedEvent extends DomainEvent {
    /** changedFields（PractitionerEdited 业务上下文）。 */
    private List<String> changedFields;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public PractitionerEditedEvent() {
        super();
    }

    /**
     * 处理Long practitionerId, List<String> changedFields逻辑。
     * @return 返回结果
     */
    public PractitionerEditedEvent(Long practitionerId, List<String> changedFields) {
        super(practitionerId, AggregateTypeConstants.PRACTITIONER);
        this.changedFields = changedFields;
    }
}
