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
 *
 * <p>仅进价/售价/数量合计；不再双写应收/实收/行金额与行数。</p>
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

    /** 数量合计（带符号代数和）。 */
    @Schema(description = "数量合计")
    private BigDecimal quantityTotal;
}
