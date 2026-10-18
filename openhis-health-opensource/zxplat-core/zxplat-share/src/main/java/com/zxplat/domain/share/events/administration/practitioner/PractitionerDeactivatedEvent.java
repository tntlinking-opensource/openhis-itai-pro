/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.practitioner;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

/**
 * PractitionerDeactivatedEvent：health-domain-shared 跨领域共享模型。
 */
public class PractitionerDeactivatedEvent extends DomainEvent {
    /** 原因（PractitionerDeactivated 业务上下文）。 */
    private String reason;
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public PractitionerDeactivatedEvent() {
        super();
    }

    /**
     * 处理Long practitionerId, String reason, Long operatorId逻辑。
     * @return 返回结果
     */
    public PractitionerDeactivatedEvent(Long practitionerId, String reason, Long operatorId) {
        super(practitionerId, AggregateTypeConstants.PRACTITIONER);
        this.reason = reason;
        this.operatorId = operatorId;
    }
}
