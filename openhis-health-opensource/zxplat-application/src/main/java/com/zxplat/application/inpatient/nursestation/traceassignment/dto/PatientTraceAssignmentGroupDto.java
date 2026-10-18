/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.nursestation.traceassignment.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/**
 * IF-IP-TRC-02：患者药批汇总待分配/已分配行。
 */
@Data
@Accessors(chain = true)
@Schema(description = "患者药批汇总待分配行")
public class PatientTraceAssignmentGroupDto {

    /** 汇总组键。 */
    @Schema(description = "汇总组键")
    private String groupKey;

    /** 汇总单号。 */
    @Schema(description = "汇总单号")
    private String summaryNo;

    /** 药品 ID。 */
    @Schema(description = "药品 ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationId;

    /** 药品名称。 */
    @Schema(description = "药品名称")
    private String medicationName;

    /** 规格。 */
    @Schema(description = "规格")
    private String specification;

    /** 批号。 */
    @Schema(description = "批号")
    private String lotNumber;

    /** 拆零发药量。 */
    @Schema(description = "拆零发药量")
    private BigDecimal dispenseQuantitySplit;

    /** 拆零单位编码。 */
    @Schema(description = "拆零单位编码")
    private String splitUnitCode;

    /** 拆零单位展示文案。 */
    @Schema(description = "拆零单位展示文案")
    private String splitUnitCode_dictText;

    /** 底层发放行 ID 列表。 */
    @Schema(description = "底层发放行 ID 列表")
    private List<Long> dispenseIds;

    /** 已分配追溯码。 */
    @Schema(description = "已分配追溯码")
    private String assignedTraceNo;

    /** 分配状态。 */
    @Schema(description = "分配状态")
    private String assignmentStatus;

    /** 分配状态展示文案。 */
    @Schema(description = "分配状态展示文案")
    private String assignmentStatus_enumText;

    /** 码池摘要。 */
    @Schema(description = "码池摘要")
    private String tracePoolPreview;

    /** 汇总单码池完整列表。 */
    @Schema(description = "汇总单码池完整列表")
    private List<String> tracePoolCodes;

    /** 允许最大追溯码数。 */
    @Schema(description = "允许最大追溯码数")
    private Integer maxTraceCodeCount;
}
