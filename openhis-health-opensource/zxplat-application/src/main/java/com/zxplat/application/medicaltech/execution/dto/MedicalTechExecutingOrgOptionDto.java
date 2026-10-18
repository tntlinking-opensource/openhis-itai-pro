/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.medicaltech.execution.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 医技项目类型筛选项（共看组时下发；如 CT/DR，按执行科室区分项目）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医技项目类型筛选项（CT/DR 等，按执行科室区分）")
public class MedicalTechExecutingOrgOptionDto {

    /** 执行科室主键（筛选项值；项目按该科区分类型）。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "执行科室主键（筛值）")
    private Long organizationId;

    /** 执行科室 bus_no（配置共看组匹配键；展示类型勿依赖名称）。 */
    @Schema(description = "执行科室 bus_no")
    private String organizationBusNo;

    /** 执行科室名称（展示用；筛匹配以 bus_no 为准）。 */
    @Schema(description = "执行科室名称")
    private String organizationName;
}
