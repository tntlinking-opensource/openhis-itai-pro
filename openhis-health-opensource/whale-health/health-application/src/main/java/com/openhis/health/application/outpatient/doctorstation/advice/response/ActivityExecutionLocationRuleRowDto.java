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

package com.openhis.health.application.outpatient.doctorstation.advice.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalTime;

/**
 * 单条诊疗活动「可执行科室」规则展示行（执行科室/地点展示名由应用层批量解析后回填）。
 */
@Data
/** 医生站-诊疗可执行科室规则行。 */
@Schema(description = "医生站-诊疗可执行科室规则行")
public class ActivityExecutionLocationRuleRowDto {

    @Schema(description = "规则主键")
    private Long id;

    @Schema(description = "诊疗活动定义主键")
    private Long activityDefinitionId;

    /** 诊疗大类编码。 */
    @Schema(description = "诊疗大类编码")
    private String activityCategoryCode;

    /** 执行科室组织主键。 */
    @Schema(description = "执行科室组织主键")
    private Long executionOrgId;

    /** 执行科室展示名。 */
    @Schema(description = "执行科室展示名")
    private String executionOrgName;

    /** 执行地点主键。 */
    @Schema(description = "执行地点主键")
    private Long executionLocationId;

    /** 执行地点展示名。 */
    @Schema(description = "执行地点展示名")
    private String executionLocationName;

    /** 规则生效开始时刻。 */
    @Schema(description = "规则生效开始时刻（日内）")
    private LocalTime startTime;

    /** 规则生效结束时刻。 */
    @Schema(description = "规则生效结束时刻（日内）")
    private LocalTime endTime;

    /** 优先级。 */
    @Schema(description = "优先级")
    private Integer priority;

    /** 展示排序。 */
    @Schema(description = "展示排序")
    private Integer displayOrder;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;
}
