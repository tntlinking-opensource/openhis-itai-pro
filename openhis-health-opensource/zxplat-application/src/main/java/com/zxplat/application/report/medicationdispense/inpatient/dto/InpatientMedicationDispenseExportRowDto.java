/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.medicationdispense.inpatient.dto;

import com.zxplat.domain.share.constants.ChargeItemAmountScaleConstants;
import com.zxplat.framework.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 住院发药报表导出行（汇总+明细并集；写出列由前端 {@code exportColumns} 决定）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院发药报表导出行")
public class InpatientMedicationDispenseExportRowDto {

    /** 维度展示名称（汇总）。 */
    @Excel(name = "维度", sort = 1)
    private String dimensionName;

    /** 来源展示（明细）。 */
    @Excel(name = "来源", sort = 2)
    private String sourceText;

    /** 记录类型展示（明细）。 */
    @Excel(name = "类型", sort = 3)
    private String recordTypeText;

    /** 业务时间（明细）。 */
    @Excel(name = "业务时间", sort = 4)
    private String businessTime;

    /** 药房名称（明细）。 */
    @Excel(name = "药房", sort = 5)
    private String pharmacyLocationName;

    /** 患者姓名（明细）。 */
    @Excel(name = "患者", sort = 6)
    private String patientName;

    /** 就诊号（明细）。 */
    @Excel(name = "就诊号", sort = 7)
    private String encounterNo;

    /** 费用性质（明细）。 */
    @Excel(name = "费用性质", sort = 8)
    private String feeNatureDisplay;

    /** 医疗类别（明细）。 */
    @Excel(name = "医疗类别", sort = 9)
    private String medTypeDisplay;

    /** 病区（明细·住院）。 */
    @Excel(name = "病区", sort = 10)
    private String wardName;

    /** 床号（明细·住院）。 */
    @Excel(name = "床号", sort = 11)
    private String bedNo;

    /** 药品编码。 */
    @Excel(name = "药品编码", sort = 12)
    private String medicationCode;

    /** 药品名称（明细）。 */
    @Excel(name = "药品名称", sort = 13)
    private String medicationName;

    /** 规格（明细）。 */
    @Excel(name = "规格", sort = 14)
    private String medicationSpec;

    /** 净数量（汇总）。 */
    @Excel(name = "数量", sort = 15, scale = 0)
    private BigDecimal netQuantity;

    /** 包装/操作单位名（汇总）。 */
    @Excel(name = "单位", sort = 16)
    private String packageUnitName;

    /** 最小单位净数量（汇总）。 */
    @Excel(name = "最小数量", sort = 17, scale = 0)
    private BigDecimal minUnitQuantity;

    /** 最小单位名（汇总）。 */
    @Excel(name = "最小单位", sort = 18)
    private String minUnitName;

    /** 数量（明细）。 */
    @Excel(name = "数量", sort = 19, scale = 0)
    private BigDecimal quantity;

    /** 操作单位名（明细）。 */
    @Excel(name = "单位", sort = 20)
    private String unitText;

    /** 进价单价（中药 3 位 DOWN）。 */
    @Excel(
        name = "进价单价",
        sort = 21,
        scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_TCM_UNIT_PRICE_DISPLAY_SCALE,
        roundingMode = RoundingMode.DOWN)
    private BigDecimal purchaseUnitPrice;

    /** 售价单价。 */
    @Excel(name = "售价单价", sort = 22, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal saleUnitPrice;

    /** 进价合计（汇总，中药 3 位 DOWN）。 */
    @Excel(
        name = "进价合计",
        sort = 23,
        scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_TCM_UNIT_PRICE_DISPLAY_SCALE,
        roundingMode = RoundingMode.DOWN)
    private BigDecimal purchaseNetAmount;

    /** 售价合计（汇总）。 */
    @Excel(name = "售价合计", sort = 24, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal saleNetAmount;

    /** 进价总价（明细，中药 3 位 DOWN）。 */
    @Excel(
        name = "进价总价",
        sort = 25,
        scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_TCM_UNIT_PRICE_DISPLAY_SCALE,
        roundingMode = RoundingMode.DOWN)
    private BigDecimal purchaseAmount;

    /** 售价总价（明细）。 */
    @Excel(name = "售价总价", sort = 26, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal saleAmount;

    /** 批号（明细）。 */
    @Excel(name = "批号", sort = 27)
    private String lotNumber;

    /** 追溯码（明细）。 */
    @Excel(name = "追溯码", sort = 28)
    private String traceNo;

    /** 科室（明细）。 */
    @Excel(name = "科室", sort = 29)
    private String departmentName;

    /** 发药人（明细）。 */
    @Excel(name = "发药人", sort = 30)
    private String dispenserName;

    /** 业务单号（明细）。 */
    @Excel(name = "业务单号", sort = 31)
    private String busNo;

    /** 处方号（明细）。 */
    @Excel(name = "处方号", sort = 32)
    private String prescriptionNo;
}
