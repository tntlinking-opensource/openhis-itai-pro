/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.feedetail.dto;

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

    /** 进价合计（行级 6 位；退费冲减；诊疗无进价则不加）。 */
    @Excel(name = "进价合计", sort = 2)
    @Schema(description = "进价合计")
    private BigDecimal purchaseAmountTotal;

    /** 售价合计（行级 6 位；退费冲减；账单 total_price 加和）。 */
    @Excel(name = "售价合计", sort = 3)
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

    /** 命中行数（含退费行）。 */
    @Excel(name = "行数", sort = 5)
    @Schema(description = "命中行数")
    private Long lineCount;
}
