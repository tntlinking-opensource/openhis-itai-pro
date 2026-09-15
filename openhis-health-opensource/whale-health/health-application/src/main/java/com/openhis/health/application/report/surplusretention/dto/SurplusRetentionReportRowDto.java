/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.surplusretention.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.whale.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 药品结余留用报表明细行（对齐《患者明细主表》19 列，含 {@code *} 前缀表头）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "药品结余留用报表明细行")
public class SurplusRetentionReportRowDto {

    /** 门诊住院号（Encounter.busNo）。 */
    @Excel(name = "*门诊住院号", sort = 1)
    @Schema(description = "门诊住院号")
    private String outpatientInpatientNo;

    /** 患者姓名（Patient.name；缺省可空）。 */
    @Excel(name = "患者姓名", sort = 2)
    @Schema(description = "患者姓名")
    private String patientName;

    /** 参保地区（v1 可空）。 */
    @Excel(name = "*参保地区", sort = 3)
    @Schema(description = "参保地区")
    private String insuredRegion;

    /** 参保类型（v1 可空）。 */
    @Excel(name = "*参保类型", sort = 4)
    @Schema(description = "参保类型")
    private String insuredType;

    /** 入院时间（住院 Encounter.startTime；门诊空）。 */
    @Excel(name = "*入院时间", sort = 5)
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "入院时间")
    private LocalDateTime admissionTime;

    /** 出院时间（住院 Encounter.endTime；门诊空）。 */
    @Excel(name = "*出院时间", sort = 6)
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "出院时间")
    private LocalDateTime dischargeTime;

    /** 处方日期（MedicationRequest.reqAuthoredTime）。 */
    @Excel(name = "*处方日期", sort = 7)
    @JsonFormat(pattern = GeneralSharedConstants.DATE_TIME_PATTERN)
    @Schema(description = "处方日期")
    private LocalDateTime prescriptionDate;

    /** 药品通用名（目录 name）。 */
    @Excel(name = "*药品通用名", sort = 8)
    @Schema(description = "药品通用名")
    private String drugGenericName;

    /** 产品名称（目录 merchandiseName）。 */
    @Excel(name = "*产品名称", sort = 9)
    @Schema(description = "产品名称")
    private String productName;

    /** 药品类别展示文案。 */
    @Excel(name = "药品类别", sort = 10)
    @Schema(description = "药品类别")
    private String drugCategoryText;

    /** 剂型展示文案（术语 dose_form_code → display）。 */
    @Excel(name = "*剂型", sort = 11)
    @Schema(description = "剂型")
    private String doseFormText;

    /** 规格（目录 totalVolume）。 */
    @Excel(name = "*规格", sort = 12)
    @Schema(description = "规格")
    private String specification;

    /** 包装单位展示名。 */
    @Excel(name = "*包装", sort = 13)
    @Schema(description = "包装")
    private String packageUnitText;

    /** 计价单位展示名（售药单位，与包装同口径）。 */
    @Excel(name = "*计价单位", sort = 14)
    @Schema(description = "计价单位")
    private String pricingUnitText;

    /** 是否中选产品（是/否）。 */
    @Excel(name = "*是否中选产品", sort = 15)
    @Schema(description = "是否中选产品")
    private String selectedProductText;

    /** 生产企业名称（Supplier.name ← manufacturerId）。 */
    @Excel(name = "*生产企业", sort = 16)
    @Schema(description = "生产企业")
    private String manufacturerName;

    /** 单价（元，结算展示刻度；退药仍为正单价）。 */
    @Excel(name = "*单价（元）", sort = 17, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    @Schema(description = "单价（元）")
    private BigDecimal unitPrice;

    /** 使用数量（片袋支；退药为负；整数刻度）。 */
    @Excel(name = "*使用数量（片袋支）", sort = 18, scale = 0)
    @Schema(description = "使用数量")
    private BigDecimal usageQuantity;

    /** 使用总金额（元；退药为负；结算展示刻度）。 */
    @Excel(name = "*使用总金额（元）", sort = 19, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    @Schema(description = "使用总金额（元）")
    private BigDecimal usageAmount;
}
