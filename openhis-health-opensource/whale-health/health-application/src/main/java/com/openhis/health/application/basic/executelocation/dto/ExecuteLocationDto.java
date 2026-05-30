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

package com.openhis.health.application.basic.executelocation.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalTime;

/**
 * 执行科室下某诊疗活动定义的执行规则行：绑定执行地点、时段与优先级等。
 */
@Data
public class ExecuteLocationDto {

    /** 执行规则主键（JSON 序列化为字符串）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 诊疗活动定义（产品定义）主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityDefinitionId;

    /** 诊疗大类业务码（与读模型分类过滤一致）。 */
    private String activityCategoryCode;

    /** 执行科室（组织机构）主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long executionOrgId;

    /** 实际执行地点（位置）主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long executionLocationId;

    /** 当日有效开始时刻（含）。 */
    private LocalTime startTime;
    /** 当日有效结束时刻（含）。 */
    private LocalTime endTime;
    /** 规则优先级，数值越小或越大由前端约定展示，此处透传领域值。 */
    private Integer priority;
    /** 同科室内展示排序。 */
    private Integer displayOrder;
    /** 备注。 */
    private String remark;
}
