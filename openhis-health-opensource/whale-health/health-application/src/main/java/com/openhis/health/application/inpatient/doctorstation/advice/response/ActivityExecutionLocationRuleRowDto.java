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

package com.openhis.health.application.inpatient.doctorstation.advice.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalTime;

/**
 * IF-IP-DOR-19：单条诊疗活动「可执行科室」规则展示行（执行科室/地点展示名由应用层批量解析后回填）。
 */
@Data
@Schema(description = "医生站-诊疗可执行科室规则行")
public class ActivityExecutionLocationRuleRowDto {

    /** 可执行科室规则主键。 */
    @Schema(description = "规则主键")
    private Long id;

    /** 关联的诊疗活动定义主键。 */
    @Schema(description = "诊疗活动定义主键")
    private Long activityDefinitionId;

    /** 规则绑定的收费项定义主键，用于计价锚点。 */
    @Schema(description = "关联收费项定义主键")
    private Long chargeItemDefinitionId;

    /** 诊疗大类编码，用于前端分组或筛选。 */
    @Schema(description = "诊疗大类编码")
    private String activityCategoryCode;

    /** 规则指定的执行科室组织主键。 */
    @Schema(description = "执行科室组织主键")
    private Long executionOrgId;

    /** 执行科室组织展示名（批量解析回填）。 */
    @Schema(description = "执行科室展示名")
    private String executionOrgName;

    /** 规则指定的执行地点（如检查室、治疗室）主键。 */
    @Schema(description = "执行地点主键")
    private Long executionLocationId;

    /** 执行地点展示名（批量解析回填）。 */
    @Schema(description = "执行地点展示名")
    private String executionLocationName;

    /** 规则在一天内的生效起始时刻（含边界语义以实现为准）。 */
    @Schema(description = "规则生效开始时刻（日内）")
    private LocalTime startTime;

    /** 规则在一天内的生效结束时刻。 */
    @Schema(description = "规则生效结束时刻（日内）")
    private LocalTime endTime;

    /** 多条规则冲突时的匹配优先级，数值越大优先。 */
    @Schema(description = "优先级")
    private Integer priority;

    /** 前端列表展示排序序号。 */
    @Schema(description = "展示排序")
    private Integer displayOrder;

    /** 规则备注或运营说明。 */
    @Schema(description = "备注")
    private String remark;
}
