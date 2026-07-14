/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.workflow.activitydefinition;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 诊疗项目定义已创建事件。
 *
 * <p>SDD-WKF-001 §7 最小载荷：activityDefinitionId（via super.aggregateId）、busNo、name。</p>
 */
@Getter
@Setter
public class ActivityDefinitionCreatedEvent extends DomainEvent {

    /** 业务编码。 */
    private String busNo;

    /** 诊疗项目名称。 */
    private String name;

    /**
     * 默认构造函数（反序列化用）。
     */
    public ActivityDefinitionCreatedEvent() {
        super();
    }

    /**
     * 构造诊疗项目定义已创建事件。
     *
     * @param activityDefinitionId 诊疗项目主键（aggregateId）
     * @param busNo                业务编码
     * @param name                 诊疗项目名称
     */
    public ActivityDefinitionCreatedEvent(Long activityDefinitionId, String busNo, String name) {
        super(activityDefinitionId, AggregateTypeConstants.ACTIVITY_DEFINITION);
        this.busNo = busNo;
        this.name = name;
    }
}
