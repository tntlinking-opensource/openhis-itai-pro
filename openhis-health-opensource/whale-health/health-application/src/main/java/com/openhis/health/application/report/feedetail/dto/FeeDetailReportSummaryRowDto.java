/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.feedetail.dto;

import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;
import com.openhis.whale.core.application.annotation.Excel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 费用明细报表汇总行（AS-RPT-008/009；支持多汇总维度）。
 *
 * <p>金额列口径（2026-08 DD）：进价合计 / 售价合计；本轮不展示实收/应收。
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

    /** 财务类型码（fin_type_code；FIN_TYPE 维兼容字段）。 */
    @Schema(description = "财务类型码")
    private String finTypeCode;

    /** 财务类型展示名（FIN_TYPE 维兼容；不参与 Excel 以免与维度列重复）。 */
    @Schema(description = "财务类型展示名")
    private String finTypeDisplay;

    /** 数量合计（带符号代数和；退费冲减；导出整数）。 */
    @Excel(name = "数量", sort = 2, scale = 0)
    @Schema(description = "数量合计")
    private BigDecimal quantityTotal;

    /** 进价单价（批号 LOT_NUMBER_COST 同明细；同桶多批号不一致则为空；展示 2 位）。 */
    @Excel(name = "进价单价", sort = 3, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    @Schema(description = "进价单价")
    private BigDecimal purchaseUnitPrice;

    /** 售价单价（ChargeItem 账单单价同明细；同桶不一致则为空；展示 2 位）。 */
    @Excel(name = "售价单价", sort = 4, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    @Schema(description = "售价单价")
    private BigDecimal saleUnitPrice;

    /** 进价合计（行级 6 位；退费冲减；诊疗无进价则不加；展示 2 位）。 */
    @Excel(name = "进价合计", sort = 5, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    @Schema(description = "进价合计")
    private BigDecimal purchaseAmountTotal;

    /** 售价合计（行级 6 位；退费冲减；账单 total_price 加和；展示 2 位）。 */
    @Excel(name = "售价合计", sort = 6, scale = ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE)
    @Schema(description = "售价合计")
    private BigDecimal saleAmountTotal;

    /**
     * 实收合计（遗留字段；本轮前端不展示）。
     *
     * @deprecated 报表中心仅保留进/售价
     */
    @Deprecated
    @Schema(description = "实收合计（遗留）")
    private BigDecimal actualReceivedAmountTotal;

    /**
     * 应收合计（兼容旧导出/契约；与售价合计同值时可忽略）。
     *
     * @deprecated 展示以 {@link #saleAmountTotal} 为准
     */
    @Deprecated
    @Schema(description = "应收合计（兼容）")
    private BigDecimal receivableAmountTotal;

    /**
     * 折后应收合计（兼容旧契约）。
     *
     * @deprecated 展示以 {@link #saleAmountTotal} 为准
     */
    @Deprecated
    @Schema(description = "折后应收合计（兼容）")
    private BigDecimal discountedAmountTotal;

    /**
     * 行金额合计（兼容旧契约；与售价合计同值）。
     *
     * @deprecated 展示以 {@link #saleAmountTotal} 为准
     */
    @Deprecated
    @Schema(description = "行金额合计（兼容）")
    private BigDecimal lineAmountTotal;

    /**
     * 命中行数（含退费行；内部统计，前端汇总 Tab 不展示）。
     *
     * @deprecated 汇总展示改数量/进售单价/进售合计
     */
    @Deprecated
    @Schema(description = "命中行数（内部）")
    private Long lineCount;
}
