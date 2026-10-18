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
 * OrganizationRetiredEvent：health-domain-shared 跨领域共享模型。
 */
public class OrganizationRetiredEvent extends DomainEvent {
    /** retire原因（OrganizationRetired 业务上下文）。 */
    private String retireReason;
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public OrganizationRetiredEvent() {
        super();
    }

    /**
     * 处理Long organizationId, String retireReason, Long operatorId逻辑。
     * @return 返回结果
     */
    public OrganizationRetiredEvent(Long organizationId, String retireReason, Long operatorId) {
        super(organizationId, AggregateTypeConstants.ORGANIZATION);
        this.retireReason = retireReason;
        this.operatorId = operatorId;
    }
}
