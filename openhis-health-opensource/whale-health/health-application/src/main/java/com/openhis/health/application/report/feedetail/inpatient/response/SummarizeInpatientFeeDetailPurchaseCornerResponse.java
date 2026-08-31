/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.feedetail.inpatient.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 住院费用明细进价列角响应（IF-RPT-IFD-05）。
 *
 * <p>仅回填页脚「进价合计」；与明细分页筛选同口径。</p>
 */
@Data
@Accessors(chain = true)
@Schema(description = "住院费用明细进价列角响应")
public class SummarizeInpatientFeeDetailPurchaseCornerResponse {

    /** 命中 scope 历史进价合计（结算刻度）。 */
    @Schema(description = "进价合计")
    private BigDecimal purchaseAmountTotal;
}
