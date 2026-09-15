/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.outpatient.response;

import java.math.BigDecimal;
import java.util.List;

import com.openhis.health.application.report.consumableusage.outpatient.dto.OutpatientConsumableUsageSummaryRowDto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 门诊耗材使用报表汇总响应（IF-RPT-OCU-02）。
 */
@Data
@Accessors(chain = true)
@Schema(description = "门诊耗材使用报表汇总响应")
public class SummarizeOutpatientConsumableUsageReportResponse {

    /** 汇总行。 */
    @Schema(description = "汇总行")
    private List<OutpatientConsumableUsageSummaryRowDto> rows;

    /** 净数量合计。 */
    @Schema(description = "净数量合计")
    private BigDecimal totalNetQuantity;

    /** 进价净额合计（结算 2 位）。 */
    @Schema(description = "进价净额合计")
    private BigDecimal purchaseNetTotalAmount;

    /** 售价净额合计（结算 2 位）。 */
    @Schema(description = "售价净额合计")
    private BigDecimal saleNetTotalAmount;
}
