/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.administration.organization;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

/**
 * OrganizationAbolishedEvent：health-domain-shared 跨领域共享模型。
 */
public class OrganizationAbolishedEvent extends DomainEvent {
    /** abolish原因（OrganizationAbolished 业务上下文）。 */
    private String abolishReason;
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public OrganizationAbolishedEvent() {
        super();
    }

    /**
     * 处理Long organizationId, String abolishReason, Long operatorId逻辑。
     * @return 返回结果
     */
    public OrganizationAbolishedEvent(Long organizationId, String abolishReason, Long operatorId) {
        super(organizationId, AggregateTypeConstants.ORGANIZATION);
        this.abolishReason = abolishReason;
        this.operatorId = operatorId;
    }
}
