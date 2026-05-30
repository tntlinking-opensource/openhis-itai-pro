/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
