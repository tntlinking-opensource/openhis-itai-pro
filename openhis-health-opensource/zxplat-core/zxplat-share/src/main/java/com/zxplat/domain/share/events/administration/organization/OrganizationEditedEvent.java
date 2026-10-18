/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.administration.organization;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter

/**
 * OrganizationEditedEvent：health-domain-shared 跨领域共享模型。
 */
public class OrganizationEditedEvent extends DomainEvent {
    /** changedFields（OrganizationEdited 业务上下文）。 */
    private List<String> changedFields;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public OrganizationEditedEvent() {
        super();
    }

    /**
     * 处理Long organizationId, List<String> changedFields逻辑。
     * @return 返回结果
     */
    public OrganizationEditedEvent(Long organizationId, List<String> changedFields) {
        super(organizationId, AggregateTypeConstants.ORGANIZATION);
        this.changedFields = changedFields;
    }
}
