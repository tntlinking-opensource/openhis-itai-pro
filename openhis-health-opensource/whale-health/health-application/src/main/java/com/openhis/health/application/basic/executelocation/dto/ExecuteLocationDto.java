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
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalTime;

/**
 * 执行科室下某诊疗活动定义的执行规则行（WH-EXE001 右侧表格）。
 */
@Data
@Schema(description = "执行科室规则行")
public class ExecuteLocationDto {

    @Schema(description = "规则主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    @Schema(description = "诊疗活动定义主键；为空表示该诊疗大类下的默认规则")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityDefinitionId;

    /** 诊疗活动定义名称。 */
    @Schema(description = "诊疗活动定义名称")
    private String activityDefinitionName;

    /** 诊疗大类业务码。 */
    @Schema(description = "诊疗大类编码")
    private String activityCategoryCode;

    /** 诊疗大类展示名。 */
    @Schema(description = "诊疗大类展示名")
    private String activityCategoryCode_dictText;

    /** 执行科室主键。 */
    @Schema(description = "执行科室标识")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long executionOrgId;

    /** 执行科室名称。 */
    @Schema(description = "执行科室名称")
    private String executionOrgName;

    /** 实际执行地点主键。 */
    @Schema(description = "执行地点标识")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long executionLocationId;

    /** 执行地点名称。 */
    @Schema(description = "执行地点名称")
    private String executionLocationName;

    /** 当日有效开始时刻（含）。 */
    @Schema(description = "生效开始时刻")
    private LocalTime startTime;

    /** 当日有效结束时刻（含）。 */
    @Schema(description = "生效结束时刻")
    private LocalTime endTime;

    /** 优先级（1 最优先，数值越小越优先）。 */
    @Schema(description = "优先级")
    private Integer priority;

    /** 同科室内展示排序。 */
    @Schema(description = "展示顺序")
    private Integer displayOrder;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;
}
