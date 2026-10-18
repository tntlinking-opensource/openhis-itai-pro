/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.doctorstation.advice.response;

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
    /** 诊疗活动定义主键。 */
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

    /** 规则生效开始时刻（日内）。 */
    @Schema(description = "规则生效开始时刻（日内）")
    private LocalTime startTime;

    /** 规则生效结束时刻（日内）。 */
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
