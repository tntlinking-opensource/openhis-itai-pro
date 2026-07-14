/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.doctorstation.advice.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * IF-DOR-12：医嘱绑定明细行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "医生站医嘱绑定明细行")
public class DoctorStationAdviceOrderBindRowDto {

    @Schema(description = "绑定类型回显")
    /** 绑定类型回显。 */
    private String bindType;

    /** 源定义主键；用法绑定场景为空，诊疗绑定为活动定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 源定义主键。 */
    @Schema(description = "源定义主键")
    private Long sourceItemId;

    /** 源展示名。 */
    @Schema(description = "源展示名")
    private String sourceItemName;

    /** 目标定义主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    /** 目标定义主键。 */
    @Schema(description = "目标定义主键")
    private Long targetItemId;

    /** 目标展示名。 */
    @Schema(description = "目标展示名")
    private String targetItemName;

    /** 数量分子。 */
    @Schema(description = "数量分子")
    private BigDecimal quantityNumerator;

    /** 数量分母。 */
    @Schema(description = "数量分母")
    private BigDecimal quantityDenominator;

    /** 单位编码。 */
    @Schema(description = "单位编码")
    private String unitCode;

    /** 单位展示名。 */
    @Schema(description = "单位展示名")
    private String unitName;

    /** 展示顺序。 */
    @Schema(description = "展示顺序")
    private Integer priority;

    /** 是否有效。 */
    @Schema(description = "是否有效")
    private Boolean activeFlag;
}
