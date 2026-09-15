/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.inpatient.support;

import java.math.BigDecimal;
import java.util.List;

import com.openhis.health.application.report.consumableusage.inpatient.dto.InpatientConsumableUsageSummaryRowDto;

import lombok.Getter;
import lombok.Setter;

/**
 * 住院耗材使用报表汇总编排结果（support 内部；非 HTTP Response）。
 */
@Getter
@Setter
public class InpatientConsumableUsageSummaryResult {

    /** 汇总行。 */
    private List<InpatientConsumableUsageSummaryRowDto> rows;

    /** 净数量合计。 */
    private BigDecimal totalNetQuantity;

    /** 进价净额合计。 */
    private BigDecimal purchaseNetTotalAmount;

    /** 售价净额合计。 */
    private BigDecimal saleNetTotalAmount;
}
