/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.financial.charge.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 体检收费队列行 DTO。
 */
@Data
@Accessors(chain = true)
@Schema(description = "体检收费队列行")
public class PhysicalExamChargeQueueRowDto {

    /** 就诊主键。 */
    @Schema(description = "就诊主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long encounterId;

    /** 患者主键。 */
    @Schema(description = "患者主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long patientId;

    /** 患者姓名。 */
    @Schema(description = "患者姓名")
    private String patientName;

    /** 性别编码（AdministrativeGender.code）。 */
    @Schema(description = "性别编码")
    private String genderCode;

    /** 性别展示名。 */
    @Schema(description = "性别展示名")
    private String genderEnumText;

    /** 年龄展示文案。 */
    @Schema(description = "年龄展示文案")
    private String ageText;

    /** 体检流水号（peis_encounter_ext.bus_no）。 */
    @Schema(description = "体检流水号")
    private String busNo;

    /** 套餐名称摘要。 */
    @Schema(description = "套餐名称")
    private String packageName;

    /** 就诊开始时间。 */
    @Schema(description = "就诊开始时间")
    private java.time.LocalDateTime startTime;

    /** 待收金额合计。 */
    @Schema(description = "待收金额合计")
    private BigDecimal plannedAmount;

    /** 已收金额合计。 */
    @Schema(description = "已收金额合计")
    private BigDecimal settledAmount;

    /** 队列页签标签编码。 */
    @Schema(description = "队列标签编码")
    private String queueLabelCode;

    /** 是否团检。 */
    @Schema(description = "是否团检")
    private Boolean groupFlag;
}
