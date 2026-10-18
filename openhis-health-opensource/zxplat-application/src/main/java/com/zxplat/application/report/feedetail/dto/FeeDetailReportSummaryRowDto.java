/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.feedetail.dto;

import com.zxplat.domain.share.constants.ChargeItemAmountScaleConstants;
import com.zxplat.framework.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 费用明细报表汇总行（AS-RPT-008/009；支持多汇总维度）。
 *
 * <p>金额列口径：仅进价/售价/数量；不再双写应收/实收/行金额。
 * Excel 维度列统一落在 {@link #dimensionName}，避免与遗留 fin_type 列重复。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "费用明细报表汇总行")
public class FeeDetailReportSummaryRowDto {

    /**
     * 汇总维度 code（FIN_TYPE / DEPARTMENT / FEE_CATEGORY / ITEM）。
     */
    @Schema(description = "汇总维度 code")
    private String summaryDimension;

    /**
     * 当前维度分桶键（如 fin_type_code、科室 id、大类 code、definitionId）。
     */
    @Schema(description = "维度分桶键")
    private String dimensionKey;

    /**
     * 当前维度展示名（导出「维度」列）。
     */
    @Excel(name = "维度", sort = 1)
    @Schema(description = "维度展示名")
    private String dimensionName;

    /** 财务类型码（fin_type_code；FIN_TYPE 维元数据，与 dimensionKey 同值）。 */
    @Schema(description = "财务类型码")
    private String finTypeCode;

    /** 财务类型展示名（FIN_TYPE 维元数据；不参与 Excel，避免与维度列重复）。 */
    @Schema(description = "财务类型展示名")
    private String finTypeDisplay;

    /** 数量合计（带符号代数和；退费冲减；导出整数）。 */
    @Excel(name = "数量", sort = 2, scale = 0)
    @Schema(description = "数量合计")
    private BigDecimal quantityTotal;

    /** 进价单价（批号 LOT_NUMBER_COST 同明细；同桶多批号不一致则为空；中药 3 位 DOWN）。 */
    @Excel(
        name = "进价单价",
        sort = 3,
        scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_TCM_UNIT_PRICE_DISPLAY_SCALE,
        roundingMode = RoundingMode.DOWN)
    @Schema(description = "进价单价")
    private BigDecimal purchaseUnitPrice;

    /** 售价单价（ChargeItem 账单单价同明细；同桶不一致则为空；展示 2 位）。 */
    @Excel(name = "售价单价", sort = 4, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    @Schema(description = "售价单价")
    private BigDecimal saleUnitPrice;

    /** 进价合计（行级 6 位；退费冲减；诊疗无进价则不加；中药 3 位 DOWN）。 */
    @Excel(
        name = "进价合计",
        sort = 5,
        scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_TCM_UNIT_PRICE_DISPLAY_SCALE,
        roundingMode = RoundingMode.DOWN)
    @Schema(description = "进价合计")
    private BigDecimal purchaseAmountTotal;

    /** 售价合计（行级 6 位；退费冲减；账单 total_price 加和；展示 2 位）。 */
    @Excel(name = "售价合计", sort = 6, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    @Schema(description = "售价合计")
    private BigDecimal saleAmountTotal;
}
