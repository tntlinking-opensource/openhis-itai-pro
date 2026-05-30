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

package com.openhis.health.domain.custom.activityexecutionlocation.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalTime;

/**
 * 执行规则列表查询模型。
 */
@Getter
@Setter
public class ExecutionRuleListQueryModel {
    /**
     * 规则主键。
     */
    private Long id;
    /**
     * 诊疗定义标识。
     */
    private Long activityDefinitionId;
    /**
     * 收费项定义标识。
     */
    private Long chargeItemDefinitionId;
    /**
     * 活动类别编码。
     */
    private String activityCategoryCode;
    /**
     * 执行科室标识。
     */
    private Long executionOrgId;
    /**
     * 执行位置标识。
     */
    private Long executionLocationId;
    /**
     * 生效开始时间。
     */
    private LocalTime startTime;
    /**
     * 生效结束时间。
     */
    private LocalTime endTime;
    /**
     * 优先级。
     */
    private Integer priority;
    /**
     * 展示顺序。
     */
    private Integer displayOrder;
    /**
     * 备注。
     */
    private String remark;
}
