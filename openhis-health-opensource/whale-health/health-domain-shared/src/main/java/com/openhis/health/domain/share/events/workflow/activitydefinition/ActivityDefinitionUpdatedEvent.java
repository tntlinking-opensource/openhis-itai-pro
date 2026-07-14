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
 * 诊疗项目定义已更新事件。
 *
 * <p>SDD-WKF-001 §7 最小载荷：activityDefinitionId（via super.aggregateId）、changedFields（动态变更摘要，禁止硬编码）。</p>
 */
@Getter
@Setter
public class ActivityDefinitionUpdatedEvent extends DomainEvent {

    /** 变更字段名称列表（逗号分隔，动态计算）。 */
    private String changedFields;

    /**
     * 默认构造函数（反序列化用）。
     */
    public ActivityDefinitionUpdatedEvent() {
        super();
    }

    /**
     * 构造诊疗项目定义已更新事件。
     *
     * @param activityDefinitionId 诊疗项目主键（aggregateId）
     * @param changedFields        变更字段名称（逗号分隔）
     */
    public ActivityDefinitionUpdatedEvent(Long activityDefinitionId, String changedFields) {
        super(activityDefinitionId, AggregateTypeConstants.ACTIVITY_DEFINITION);
        this.changedFields = changedFields;
    }
}
