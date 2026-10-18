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
 * PractitionerActivatedEvent：health-domain-shared 跨领域共享模型。
 */
public class PractitionerActivatedEvent extends DomainEvent {
    /** 操作人主键（PractitionerActivated 业务上下文）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public PractitionerActivatedEvent() {
        super();
    }

    /**
     * 处理Long practitionerId, Long operatorId逻辑。
     * @return 返回结果
     */
    public PractitionerActivatedEvent(Long practitionerId, Long operatorId) {
        super(practitionerId, AggregateTypeConstants.PRACTITIONER);
        this.operatorId = operatorId;
    }
}
