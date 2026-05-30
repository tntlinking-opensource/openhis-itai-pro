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

package com.openhis.health.application.basic.executelocation.request;

import lombok.Data;

import java.util.List;

/**
 * 在同一执行科室与诊疗大类下，批量将多条诊疗定义绑定为相同执行时段/地点/优先级的规则。
 */
@Data
public class BatchCreateExecuteLocationRequest {

    /** 执行科室主键。 */
    private Long executionOrgId;
    /** 诊疗大类业务码。 */
    private String activityCategoryCode;
    /** 共用执行地点主键。 */
    private Long executionLocationId;
    /** 待绑定诊疗定义主键列表，非空。 */
    private List<Long> activityDefinitionIds;
    /** 共用开始时间字符串。 */
    private String startTime;
    /** 共用结束时间字符串。 */
    private String endTime;
    /** 共用优先级。 */
    private Integer priority;
    /** 共用展示顺序。 */
    private Integer displayOrder;
    /** 共用备注。 */
    private String remark;
}
