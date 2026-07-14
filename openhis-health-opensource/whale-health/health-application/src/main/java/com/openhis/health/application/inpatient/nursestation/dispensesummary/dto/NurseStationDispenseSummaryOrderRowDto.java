/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.dispensesummary.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.util.List;

/**
 * 待汇总明细 · 医嘱维度行（IF-IP-SUM-01）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "待汇总明细医嘱行")
public class NurseStationDispenseSummaryOrderRowDto {

    @Schema(description = "药品申请主键")
    @JsonSerialize(using = ToStringSerializer.class)
    /** 药品申请主键。 */
    private Long medReqId;

    /** 药品定义主键。 */
    @Schema(description = "药品定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationId;

    /** 药品名称。 */
    @Schema(description = "药品名称")
    private String medicationName;

    /** 规格展示。 */
    @Schema(description = "规格展示")
    private String specificationText;

    /** 成组号。 */
    @Schema(description = "成组号")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long groupId;

    /** 长临 code。 */
    @Schema(description = "长临 code")
    private String therapyEnum;

    /** 长临展示。 */
    @Schema(description = "长临展示")
    private String therapyEnumText;

    /** 用药说明。 */
    @Schema(description = "用药说明")
    private String dosageInstruction;

    /** 用法 code。 */
    @Schema(description = "用法 code")
    private String methodCode;

    /** 用法展示。 */
    @Schema(description = "用法展示")
    private String methodCodeText;

    /** 频次 code。 */
    @Schema(description = "频次 code")
    private String frequencyCode;

    /** 频次展示。 */
    @Schema(description = "频次展示")
    private String frequencyCodeText;

    /** 发放药房主键。 */
    @Schema(description = "发放药房主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long locationId;

    /** 发放药房展示。 */
    @Schema(description = "发放药房展示")
    private String locationName;

    /** 可勾选时间点。 */
    @Schema(description = "可勾选时间点")
    private List<NurseStationDispenseSummaryTimePointDto> timePoints;
}
