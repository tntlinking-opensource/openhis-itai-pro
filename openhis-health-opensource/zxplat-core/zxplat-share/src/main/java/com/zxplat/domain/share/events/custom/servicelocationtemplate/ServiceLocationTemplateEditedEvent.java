/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.custom.servicelocationtemplate;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 取药科室模板已编辑事件。
 */
@Getter
@Setter
public class ServiceLocationTemplateEditedEvent extends DomainEvent {
    /** organization主键（ServiceLocationTemplateEditedEvent）。 */
    private Long organizationId;
    /** ruleCount（ServiceLocationTemplateEditedEvent）。 */
    private Integer ruleCount;

    /** 操作人主键（ServiceLocationTemplateEditedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public ServiceLocationTemplateEditedEvent() {
        super();
    }

    /**
     * 处理Long aggregateId, Long organizationId, Integer ruleCount逻辑。
     * @return 返回结果
     */
    public ServiceLocationTemplateEditedEvent(Long aggregateId, Long organizationId, Integer ruleCount, Long operatorId) {
        super(aggregateId, AggregateTypeConstants.SERVICE_LOCATION_TEMPLATE);
        this.organizationId = organizationId;
        this.ruleCount = ruleCount;
        this.operatorId = operatorId;
    }
}
