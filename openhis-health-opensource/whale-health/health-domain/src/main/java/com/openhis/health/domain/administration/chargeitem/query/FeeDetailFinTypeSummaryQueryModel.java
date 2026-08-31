/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.query;

import java.math.BigDecimal;

import lombok.Getter;
import lombok.Setter;

/**
 * 费用明细报表按定价 definition 净汇总行（AS-RPT-008/009；fin_type 由应用层 enrich）。
 *
 * <p>金额为行级 6 位代数和（含退费负向）；页脚 2 位结算刻度在应用层收口。</p>
 */
@Getter
@Setter
public class FeeDetailFinTypeSummaryQueryModel {

    /** 定价锚点 {@code definition_id}。 */
    private Long definitionId;

    /** 应收合计（折后应向患者收取口径）。 */
    private BigDecimal receivableAmountTotal = BigDecimal.ZERO;

    /** 折后应收合计。 */
    private BigDecimal discountedAmountTotal = BigDecimal.ZERO;

    /** 行金额合计（{@code total_price}）。 */
    private BigDecimal lineAmountTotal = BigDecimal.ZERO;

    /** 命中行数（含退费行）。 */
    private long lineCount;

    /**
     * 带收退符号的数量代数和（FIN_TYPE 维汇总用；禁止为数量再物化全窗 RowQueryModel）。
     */
    private BigDecimal quantityTotal = BigDecimal.ZERO;
}
