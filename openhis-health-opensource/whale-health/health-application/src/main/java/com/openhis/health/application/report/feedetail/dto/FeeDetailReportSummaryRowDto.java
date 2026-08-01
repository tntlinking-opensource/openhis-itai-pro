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
 * 费用明细报表按 fin_type 净汇总行（AS-RPT-008/009）。
 *
 * <p>金额列口径：进价合计 / 售价合计 / 实收合计；行级 6 位。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "费用明细报表费用类型汇总行")
public class FeeDetailReportSummaryRowDto {

    /** 财务类型码（fin_type_code）。 */
    @Schema(description = "财务类型码")
    private String finTypeCode;

    /** 财务类型展示名。 */
    @Excel(name = "费用类型", sort = 1)
    @Schema(description = "财务类型展示名")
    private String finTypeDisplay;

    /** 进价合计（行级 6 位；退费冲减）。 */
    @Excel(name = "进价合计", sort = 2)
    @Schema(description = "进价合计")
    private BigDecimal purchaseAmountTotal;

    /** 售价合计（行级 6 位；退费冲减）。 */
    @Excel(name = "售价合计", sort = 3)
    @Schema(description = "售价合计")
    private BigDecimal saleAmountTotal;

    /** 实收合计（行级 6 位；退费冲减）。 */
    @Excel(name = "实收合计", sort = 4)
    @Schema(description = "实收合计")
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
