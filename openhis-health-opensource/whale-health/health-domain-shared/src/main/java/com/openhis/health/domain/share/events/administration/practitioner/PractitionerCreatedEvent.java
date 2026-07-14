/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.practitioner;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

/**
 * PractitionerCreatedEvent：health-domain-shared 跨领域共享模型。
 */
public class PractitionerCreatedEvent extends DomainEvent {
    /** user主键（PractitionerCreated 业务上下文）。 */
    private Long userId;
    private String name;
    /** 业务No（PractitionerCreatedEvent）。 */
    private String busNo;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public PractitionerCreatedEvent() {
        super();
    }

    /**
     * 处理Long practitionerId, Long userId, String name, String busNo逻辑。
     * @return 返回结果
     */
    public PractitionerCreatedEvent(Long practitionerId, Long userId, String name, String busNo) {
        super(practitionerId, AggregateTypeConstants.PRACTITIONER);
        this.userId = userId;
        this.name = name;
        this.busNo = busNo;
    }
}
