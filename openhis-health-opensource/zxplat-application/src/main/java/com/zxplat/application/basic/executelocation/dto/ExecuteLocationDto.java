/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.executelocation.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalTime;

/**
 * 开立科室下某诊疗活动定义的执行规则行（WH-EXE001 右侧表格）。
 */
@Data
@Schema(description = "执行科室规则行")
public class ExecuteLocationDto {

    /** 规则主键。 */
    @Schema(description = "规则主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 开立科室标识。 */
    @Schema(description = "开立科室标识")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long organizationId;

    /** 开立科室名称。 */
    @Schema(description = "开立科室名称")
    private String organizationName;

    /** 诊疗活动定义主键；为空表示未绑单品（大类默认或业务类型层）。 */
    @Schema(description = "诊疗活动定义主键；为空表示未绑单品（大类默认或业务类型层）")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long activityDefinitionId;

    /** 诊疗活动定义名称。 */
    @Schema(description = "诊疗活动定义名称")
    private String activityDefinitionName;

    /** 诊疗大类编码。 */
    @Schema(description = "诊疗大类编码")
    private String activityCategoryCode;

    /** 诊疗大类展示名。 */
    @Schema(description = "诊疗大类展示名")
    private String activityCategoryCode_dictText;

    /** 业务类型码（术语 activity_type_code）；可空。 */
    @Schema(description = "业务类型码")
    private String activityTypeCode;

    /** 业务类型展示名。 */
    @Schema(description = "业务类型展示名")
    private String activityTypeCode_dictText;

    /** 执行科室标识。 */
    @Schema(description = "执行科室标识")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long executionOrgId;

    /** 执行科室名称。 */
    @Schema(description = "执行科室名称")
    private String executionOrgName;

    /** 执行地点标识。 */
    @Schema(description = "执行地点标识")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long executionLocationId;

    /** 执行地点名称。 */
    @Schema(description = "执行地点名称")
    private String executionLocationName;

    /** 生效开始时刻。 */
    @Schema(description = "生效开始时刻")
    private LocalTime startTime;

    /** 生效结束时刻。 */
    @Schema(description = "生效结束时刻")
    private LocalTime endTime;

    /** 优先级。 */
    @Schema(description = "优先级")
    private Integer priority;

    /** 展示顺序。 */
    @Schema(description = "展示顺序")
    private Integer displayOrder;

    /** 备注。 */
    @Schema(description = "备注")
    private String remark;
}
