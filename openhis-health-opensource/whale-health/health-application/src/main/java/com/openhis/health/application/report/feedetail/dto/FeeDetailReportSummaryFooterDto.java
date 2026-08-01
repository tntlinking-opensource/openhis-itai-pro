/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.feedetail.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 费用明细报表汇总页脚合计（AS-RPT-008/009；结算刻度 2 位）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "费用明细报表汇总页脚")
public class FeeDetailReportSummaryFooterDto {

    /** 进价合计（结算刻度）。 */
    @Schema(description = "进价合计")
    private BigDecimal purchaseAmountTotal;

    /** 售价合计（结算刻度）。 */
    @Schema(description = "售价合计")
    private BigDecimal saleAmountTotal;

    /** 实收合计（结算刻度）。 */
    @Schema(description = "实收合计")
    private BigDecimal actualReceivedAmountTotal;

    /**
     * 应收合计（兼容旧契约）。
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
     * 行金额合计（兼容旧契约）。
     *
     * @deprecated 展示以 {@link #saleAmountTotal} 为准
     */
    @Deprecated
    @Schema(description = "行金额合计（兼容）")
    private BigDecimal lineAmountTotal;

    /** 命中行数合计。 */
    @Schema(description = "行数合计")
    private Long lineCountTotal;
}
