/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.organization;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.domain.share.enums.OrganizationClassification;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter

/**
 * OrganizationPublishedEvent：health-domain-shared 跨领域共享模型。
 */
public class OrganizationPublishedEvent extends DomainEvent {
    /** name（OrganizationPublished 业务上下文）。 */
    private String name;
    private String typeEnum;
    /** class编码（OrganizationPublishedEvent）。 */
    private OrganizationClassification classEnum;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public OrganizationPublishedEvent() {
        super();
    }

    /**
     * 处理Long organizationId, String name, String typeEnum, Integer classEnum逻辑。
     * @return 返回结果
     */
    public OrganizationPublishedEvent(Long organizationId, String name, String typeEnum, OrganizationClassification classEnum) {
        super(organizationId, AggregateTypeConstants.ORGANIZATION);
        this.name = name;
        this.typeEnum = typeEnum;
        this.classEnum = classEnum;
    }
}
