/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.patientmedicationusage.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 住院患者药品使用汇总行（按 medicationId 净用量/净金额）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院患者药品使用-汇总行")
public class InpatientPatientMedicationUsageSummaryLineDto {

    /**
     * 分组键：药品定义主键。
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "药品定义主键")
    private Long medicationId;

    /**
     * 药品名称。
     */
    @Schema(description = "药品名称")
    private String itemName;

    /**
     * 规格。
     */
    @Schema(description = "规格")
    private String itemSpec;

    /**
     * 组内任一条账单单价；可空。
     */
    @Schema(description = "账单单价（组内代表）")
    private BigDecimal unitPrice;

    /**
     * 组内账单行合计净额（正向计入、退药冲减）。
     */
    @Schema(description = "行合计净额")
    private BigDecimal lineTotalAmount;

    /**
     * 满包装净整数数量。
     */
    @Schema(description = "满包装净数量")
    private Integer packageQuantity;

    /**
     * 不足一包净小单位数量。
     */
    @Schema(description = "拆零净数量")
    private BigDecimal splitUnitQuantity;

    /**
     * 包装单位 code。
     */
    @Schema(description = "包装单位 code")
    private String packageUnitCode;

    /**
     * 包装单位展示。
     */
    @Schema(description = "包装单位展示")
    private String packageUnitDisplay;

    /**
     * 小单位 code。
     */
    @Schema(description = "小单位 code")
    private String splitUnitCode;

    /**
     * 小单位展示。
     */
    @Schema(description = "小单位展示")
    private String splitUnitDisplay;
}
