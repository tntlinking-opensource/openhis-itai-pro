/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.deptdesk.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 观察实例行 DTO（诊台小项录入表）。
 */
@Data
public class ObservationRowDto {

    /** 观察实例主键。 */
    @Schema(description = "观察实例主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 观察定义主键。 */
    @Schema(description = "观察定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long definitionId;

    /** 观察项展示名。 */
    @Schema(description = "观察项展示名")
    private String definitionDisplay;

    /** 观察项分类 code。 */
    @Schema(description = "观察项分类")
    private String categoryCode;

    /** 数值结果。 */
    @Schema(description = "数值结果")
    private BigDecimal valueQuantity;

    /** 文本结果。 */
    @Schema(description = "文本结果")
    private String valueString;

    /** 解读编码。 */
    @Schema(description = "解读编码")
    private String interpretationCode;

    /** 状态 code。 */
    @Schema(description = "状态")
    private String statusEnum;
}
