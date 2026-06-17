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

/**
 * 创建单条执行规则请求：描述「某执行科室 + 诊疗大类（+ 可选诊疗项目）」在何地、何时执行。
 */
@Data
public class CreateExecuteLocationRequest {

    /** 诊疗活动定义主键；为空时表示该诊疗大类下的默认规则。 */
    private Long activityDefinitionId;
    /** 诊疗大类业务码。 */
    private String activityCategoryCode;
    /** 执行科室主键。 */
    private Long executionOrgId;
    /** 执行地点主键。 */
    private Long executionLocationId;
    /** 开始时间字符串，由 Mapper 解析为 {@link java.time.LocalTime}。 */
    private String startTime;
    /** 结束时间字符串。 */
    private String endTime;
    /** 优先级；空则领域侧按默认 1 处理。 */
    private Integer priority;
    /** 展示顺序。 */
    private Integer displayOrder;
    /** 备注。 */
    private String remark;
}
