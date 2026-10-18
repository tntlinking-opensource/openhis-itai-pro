/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.outpatient.financial.feedetail.dto;

import com.zxplat.domain.share.constants.ChargeItemAmountScaleConstants;
import com.zxplat.framework.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 门诊费用明细导出行 DTO（IF-OP-FDT-03）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊费用明细导出行")
public class OutpatientFeeDetailExportRowDto {

    /** 患者姓名。 */
    @Excel(name = "姓名", sort = 1)
    private String patientName;

    /** 门诊号。 */
    @Excel(name = "门诊号", sort = 2)
    private String busNo;

    /** 挂号科室。 */
    @Excel(name = "挂号科室", sort = 3)
    private String registrationDepartmentName;

    /** 费用性质（收费行 account_id → fin_contract）。 */
    @Excel(name = "费用性质", sort = 4)
    private String feeNatureDisplay;

    /** 医疗类别（主诊断 med_type）。 */
    @Excel(name = "医疗类别", sort = 5)
    private String medTypeDisplay;

    /** 费用大类。 */
    @Excel(name = "费用大类", sort = 6)
    private String feeCategoryDisplay;

    /** 细分类。 */
    @Excel(name = "细分类", sort = 7)
    private String subCategoryDisplay;

    /** 项目名称。 */
    @Excel(name = "项目名称", sort = 8)
    private String productName;

    /** 项目编码。 */
    @Excel(name = "项目编码", sort = 9)
    private String productCode;

    /** 规格。 */
    @Excel(name = "规格", sort = 10)
    private String specification;

    /** 数量（导出整数）。 */
    @Excel(name = "数量", sort = 11, scale = 0)
    private BigDecimal quantityValue;

    /** 单位。 */
    @Excel(name = "单位", sort = 12)
    private String quantityUnitDisplay;

    /** 开单医生。 */
    @Excel(name = "开单医生", sort = 13)
    private String orderingDoctorName;

    /** 执行人。 */
    @Excel(name = "执行人", sort = 14)
    private String performerName;

    /** 收费人。 */
    @Excel(name = "收费人", sort = 15)
    private String cashierName;

    /** 结算时间。 */
    @Excel(name = "结算时间", sort = 16)
    private String billDate;

    /** 医保等级。 */
    @Excel(name = "医保等级", sort = 17)
    private String insuranceLevelDisplay;

    /** 进价单价（中药 3 位 DOWN）。 */
    @Excel(
        name = "进价单价",
        sort = 18,
        scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_TCM_UNIT_PRICE_DISPLAY_SCALE,
        roundingMode = RoundingMode.DOWN)
    private BigDecimal purchaseUnitPrice;

    /** 进价总价（中药 3 位 DOWN）。 */
    @Excel(
        name = "进价总价",
        sort = 19,
        scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_TCM_UNIT_PRICE_DISPLAY_SCALE,
        roundingMode = RoundingMode.DOWN)
    private BigDecimal purchaseAmount;

    /** 售价单价（展示 2 位）。 */
    @Excel(name = "售价单价", sort = 20, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal saleUnitPrice;

    /** 售价总价（展示 2 位）。 */
    @Excel(name = "售价总价", sort = 21, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal saleAmount;

    /** 优惠金额（售价总价 − 折后应收；展示 2 位）。 */
    @Excel(name = "优惠金额", sort = 22, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal discountAmount;

    /** 实收金额（折后应收；展示 2 位）。 */
    @Excel(name = "实收金额", sort = 23, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    private BigDecimal actualReceivedAmount;
}
