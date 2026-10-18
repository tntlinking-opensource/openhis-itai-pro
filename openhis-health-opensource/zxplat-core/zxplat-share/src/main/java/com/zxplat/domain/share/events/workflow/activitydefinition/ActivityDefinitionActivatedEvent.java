/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.events.workflow.activitydefinition;

import com.zxplat.domain.share.constants.AggregateTypeConstants;
import com.zxplat.framework.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 诊疗项目定义批量已启用事件。
 *
 * <p>SDD-WKF-001 §7 最小载荷：activityDefinitionIdList（批量 ID 列表）、targetStatus。</p>
 *
 * <p>本事件为批量事件，aggregateId 使用 0L 作为占位；下游消费者应以 activityDefinitionIdList 为准。</p>
 */
@Getter
@Setter
public class ActivityDefinitionActivatedEvent extends DomainEvent {

    /** 启用的诊疗项目主键列表。 */
    private List<Long> activityDefinitionIdList;

    /** 目标状态枚举（启用后的状态码）。 */
    private String targetStatus;

    /**
     * 默认构造函数（反序列化用）。
     */
    public ActivityDefinitionActivatedEvent() {
        super();
    }

    /**
     * 构造诊疗项目批量已启用事件。
     *
     * @param activityDefinitionIdList 启用的诊疗项目主键列表
     * @param targetStatus             目标状态枚举
     */
    public ActivityDefinitionActivatedEvent(List<Long> activityDefinitionIdList, String targetStatus) {
        super(0L, AggregateTypeConstants.ACTIVITY_DEFINITION);
        this.activityDefinitionIdList = activityDefinitionIdList;
        this.targetStatus = targetStatus;
    }
}
