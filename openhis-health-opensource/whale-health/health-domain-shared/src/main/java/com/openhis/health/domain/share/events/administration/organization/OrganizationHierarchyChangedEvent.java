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
 * OrganizationHierarchyChangedEvent：health-domain-shared 跨领域共享模型。
 */
public class OrganizationHierarchyChangedEvent extends DomainEvent {
    /** oldParent主键（OrganizationHierarchyChanged 业务上下文）。 */
    private Long oldParentId;
    private Long newParentId;
    /** new业务No（OrganizationHierarchyChangedEvent）。 */
    private String newBusNo;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public OrganizationHierarchyChangedEvent() {
        super();
    }

    /**
     * 处理Long organizationId, Long oldParentId, Long newParentId, String newBusNo逻辑。
     * @return 返回结果
     */
    public OrganizationHierarchyChangedEvent(Long organizationId, Long oldParentId, Long newParentId, String newBusNo) {
        super(organizationId, AggregateTypeConstants.ORGANIZATION);
        this.oldParentId = oldParentId;
        this.newParentId = newParentId;
        this.newBusNo = newBusNo;
    }
}
