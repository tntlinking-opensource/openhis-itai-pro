/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.events.custom.activityexecutionlocation;

import com.openhis.health.domain.share.constants.AggregateTypeConstants;
import com.openhis.whale.core.domain.event.DomainEvent;
import lombok.Getter;
import lombok.Setter;

/**
 * 执行科室规则已变更事件。
 */
@Getter
@Setter
public class ActivityExecutionLocationEditedEvent extends DomainEvent {
    /**
     * 操作类型（CREATE/UPDATE/DELETE）。
     */
    private String operationType;
    /**
     * 活动类别编码。
     */
    private String activityCategoryCode;
    /**
     * 执行科室标识。
     */
    private Long executionOrgId;

    /** 操作人主键（ActivityExecutionLocationEditedEvent）。 */
    private Long operatorId;

    /**
     * 处理逻辑。
     * @return 返回结果
     */
    public ActivityExecutionLocationEditedEvent() {
        super();
    }

    /**
     * 构造事件。
     *
     * @param ruleId 规则标识
     * @param operationType 操作类型
     * @param activityCategoryCode 活动类别编码
     * @param executionOrgId 执行科室标识
     */
    public ActivityExecutionLocationEditedEvent(
        Long ruleId,
        String operationType,
        String activityCategoryCode,
        Long executionOrgId
    ) {
        this(ruleId, operationType, activityCategoryCode, executionOrgId, null);
    }

    /**
     * 构造事件（含操作人）。
     *
     * @param ruleId 规则标识
     * @param operationType 操作类型
     * @param activityCategoryCode 活动类别编码
     * @param executionOrgId 执行科室标识
     * @param operatorId 操作人标识
     */
    public ActivityExecutionLocationEditedEvent(
        Long ruleId,
        String operationType,
        String activityCategoryCode,
        Long executionOrgId,
        Long operatorId
    ) {
        super(ruleId, AggregateTypeConstants.ACTIVITY_EXECUTION_LOCATION);
        this.operationType = operationType;
        this.activityCategoryCode = activityCategoryCode;
        this.executionOrgId = executionOrgId;
        this.operatorId = operatorId;
    }
}
