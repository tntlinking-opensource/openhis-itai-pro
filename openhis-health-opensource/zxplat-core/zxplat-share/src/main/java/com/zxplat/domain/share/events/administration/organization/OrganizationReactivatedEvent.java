/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.organization;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

/**
 * OrganizationReactivatedEvent：health-domain-shared 跨领域共享模型。
 */
public class OrganizationReactivatedEvent extends DomainEvent {
    /** 操作人主键（OrganizationReactivated 业务上下文）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public OrganizationReactivatedEvent() {
        super();
    }

    /**
     * 处理Long organizationId, Long operatorId逻辑。
     * @return 返回结果
     */
    public OrganizationReactivatedEvent(Long organizationId, Long operatorId) {
        super(organizationId, AggregateTypeConstants.ORGANIZATION);
        this.operatorId = operatorId;
    }
}
