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
 * OrganizationCreatedEvent：health-domain-shared 跨领域共享模型。
 */
public class OrganizationCreatedEvent extends DomainEvent {
    /** name（OrganizationCreated 业务上下文）。 */
    private String name;
    private String typeEnum;
    /** 业务No（OrganizationCreatedEvent）。 */
    private String busNo;
    /** parent主键（OrganizationCreatedEvent）。 */
    private Long parentId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public OrganizationCreatedEvent() {
        super();
    }

    /**
     * 处理Long organizationId, String name, String typeEnum, String busNo, Long parentId逻辑。
     * @return 返回结果
     */
    public OrganizationCreatedEvent(Long organizationId, String name, String typeEnum, String busNo, Long parentId) {
        super(organizationId, AggregateTypeConstants.ORGANIZATION);
        this.name = name;
        this.typeEnum = typeEnum;
        this.busNo = busNo;
        this.parentId = parentId;
    }
}
