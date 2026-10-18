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
 * PractitionerOrgSwitchedEvent：health-domain-shared 跨领域共享模型。
 */
public class PractitionerOrgSwitchedEvent extends DomainEvent {
    /** fromOrg主键（PractitionerOrgSwitched 业务上下文）。 */
    private Long fromOrgId;
    private Long toOrgId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public PractitionerOrgSwitchedEvent() {
        super();
    }

    /**
     * 处理Long practitionerId, Long fromOrgId, Long toOrgId逻辑。
     * @return 返回结果
     */
    public PractitionerOrgSwitchedEvent(Long practitionerId, Long fromOrgId, Long toOrgId) {
        super(practitionerId, AggregateTypeConstants.PRACTITIONER);
        this.fromOrgId = fromOrgId;
        this.toOrgId = toOrgId;
    }
}
