/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.outpatient.dto;

import java.math.BigDecimal;

import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.whale.core.application.annotation.Excel;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊耗材使用报表导出行（汇总+明细并集；写出列由前端 {@code exportColumns} 决定）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊耗材使用报表导出行")
public class OutpatientConsumableUsageExportRowDto {

    /** 维度（汇总）。 */
    @Excel(name = "维度", sort = 1)
    private String dimensionName;

    /** 来源（明细）。 */
    @Excel(name = "来源", sort = 2)
    private String sourceText;

    /** 耗材名称（明细）。 */
    @Excel(name = "耗材名称", sort = 3)
    private String deviceName;

    /** 规格（明细）。 */
    @Excel(name = "规格", sort = 4)
    private String specText;

    /** 患者（明细）。 */
    @Excel(name = "患者", sort = 5)
    private String patientName;

    /** 就诊号（明细）。 */
    @Excel(name = "就诊号", sort = 6)
    private String encounterBusNo;

    /** 费用性质（明细）。 */
    @Excel(name = "费用性质", sort = 7)
    private String feeNatureDisplay;

    /** 医疗类别（明细）。 */
    @Excel(name = "医疗类别", sort = 8)
    private String medTypeDisplay;

    /** 科室（明细）。 */
    @Excel(name = "科室", sort = 9)
    private String organizationName;

    /** 耗材房（明细）。 */
    @Excel(name = "耗材房", sort = 10)
    private String locationName;

    /** 状态/类型（明细）。 */
    @Excel(name = "状态/类型", sort = 11)
    private String statusOrTypeText;

    /** 净数量（汇总）。 */
    @Excel(name = "数量", sort = 12, scale = 0)
    private BigDecimal netQuantity;

    /** 数量（明细）。 */
    @Excel(name = "数量", sort = 13, scale = 0)
    private BigDecimal quantity;

    /** 单位（明细，对应 quantity）。 */
    @Excel(name = "单位", sort = 14)
    private String unitText;

    /** 进价单价。 */
    @Excel(name = "进价单价", sort = 15, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal purchaseUnitPrice;

    /** 售价单价。 */
    @Excel(name = "售价单价", sort = 16, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal saleUnitPrice;

    /** 进价合计（汇总）。 */
    @Excel(name = "进价合计", sort = 17, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal purchaseNetAmount;

    /** 售价合计（汇总）。 */
    @Excel(name = "售价合计", sort = 18, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal saleNetAmount;

    /** 进价总价（明细）。 */
    @Excel(name = "进价总价", sort = 19, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal purchaseAmount;

    /** 售价总价（明细）。 */
    @Excel(name = "售价总价", sort = 20, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal saleAmount;

    /** 批号（明细）。 */
    @Excel(name = "批号", sort = 21)
    private String lotNumber;

    /** 执行人（明细）。 */
    @Excel(name = "执行人", sort = 22)
    private String performerName;

    /** 业务单号（明细）。 */
    @Excel(name = "业务单号", sort = 23)
    private String busNo;

    /** 业务时间（明细）。 */
    @Excel(name = "业务时间", sort = 24)
    private String businessTimeText;
}
