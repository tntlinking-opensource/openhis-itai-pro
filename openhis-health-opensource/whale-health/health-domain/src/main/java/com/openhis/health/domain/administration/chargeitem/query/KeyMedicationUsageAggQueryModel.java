/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.chargeitem.query;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 重点药品使用统计域汇总读模型（DM-ADM-011 §6.2.35；AS-RPT-011）。
 */
@Getter
@Setter
public class KeyMedicationUsageAggQueryModel {

    /**
     * 净人次：该类重点药净售价 &gt; 0 的 distinct {@code encounter_id} 数。
     */
    private Long encounterNetCount;

    /**
     * 售价净额（结算刻度）：命中行带收退符号的 {@code total_price} 代数和。
     */
    private BigDecimal saleAmountTotal;
}
