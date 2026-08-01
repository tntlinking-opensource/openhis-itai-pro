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

import java.math.BigDecimal;

/**
 * 摆药单查询 · 同一患者下按 medReqId 合并后的药品行（IF-IP-SUM-09）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "摆药单查询药品行")
public class NurseStationDispenseSummaryPatientPickupLineDto {

    /** 药品申请（医嘱）主键；合并键。 */
    @Schema(description = "医嘱/药品申请主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medReqId;

    /** 药品定义主键。 */
    @Schema(description = "药品定义主键")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long medicationId;

    /** 药品名称。 */
    @Schema(description = "药品名称")
    private String medicationName;

    /** 规格展示。 */
    @Schema(description = "规格")
    private String specificationText;

    /** 合并后数量（发放原单位累加）。 */
    @Schema(description = "数量")
    private BigDecimal itemQuantity;

    /** 数量单位 code。 */
    @Schema(description = "单位 code")
    private String unitCode;

    /** 数量单位展示。 */
    @Schema(description = "单位展示")
    private String unitCodeText;

    /**
     * 界面数量文案（与 IF-IP-SUM-03 同口径）：满包整数 + 拆零余量，如 {@code 1盒1片}。
     */
    @Schema(description = "数量展示文案（满包+余量）")
    private String displayQuantityText;

    /** 剂量/用药说明。 */
    @Schema(description = "剂量")
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
}
