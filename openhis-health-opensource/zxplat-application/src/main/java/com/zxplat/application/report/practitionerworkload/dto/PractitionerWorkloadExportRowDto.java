/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.practitionerworkload.dto;

import com.zxplat.domain.share.constants.ChargeItemAmountScaleConstants;
import com.zxplat.framework.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 科室/医生工作量明细导出行 DTO（IF-RPT-WKL-04；DD-报表中心-进售价口径）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "科室医生工作量明细导出行")
public class PractitionerWorkloadExportRowDto {

    /** 开单科室。 */
    @Excel(name = "开单科室", sort = 1)
    @Schema(description = "开单科室")
    private String requestingOrgName;

    /** 目录类型。 */
    @Excel(name = "目录类型", sort = 2)
    @Schema(description = "目录类型")
    private String catalogTypeDisplay;

    /** 项目名称。 */
    @Excel(name = "项目名称", sort = 3)
    @Schema(description = "项目名称")
    private String productName;

    /** 项目编码。 */
    @Excel(name = "项目编码", sort = 4)
    @Schema(description = "项目编码")
    private String productCode;

    /** 规格。 */
    @Excel(name = "规格", sort = 5)
    @Schema(description = "规格")
    private String specification;

    /** 开单医生。 */
    @Excel(name = "开单医生", sort = 6)
    @Schema(description = "开单医生")
    private String practitionerName;

    /** 开立时间。 */
    @Excel(name = "开立时间", sort = 7)
    @Schema(description = "开立时间")
    private String enteredDate;

    /** 售价（展示 2 位）。 */
    @Excel(name = "售价", sort = 8, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    @Schema(description = "售价")
    private BigDecimal saleAmount;

    /** 优惠（展示 2 位）。 */
    @Excel(name = "优惠金额", sort = 9, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    @Schema(description = "优惠金额")
    private BigDecimal discountAmount;

    /** 实收（折后应收；展示 2 位）。 */
    @Excel(name = "实收金额", sort = 10, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    @Schema(description = "实收金额")
    private BigDecimal actualReceivedAmount;

    /** 进价（中药可 3 位，DOWN 截断）。 */
    @Excel(
        name = "进价",
        sort = 11,
        scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_TCM_UNIT_PRICE_DISPLAY_SCALE,
        roundingMode = RoundingMode.DOWN)
    @Schema(description = "进价")
    private BigDecimal purchaseAmount;
}
