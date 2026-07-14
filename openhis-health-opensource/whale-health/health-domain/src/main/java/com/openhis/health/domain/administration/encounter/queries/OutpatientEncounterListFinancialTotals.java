/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.queries;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 门诊医生站列表用的「就诊维度收费汇总」传输对象。
 * <p>金额事实仅以 {@link com.openhis.health.domain.administration.chargeitem.entity.ChargeItem} 为源，
 * 由应用层对当前页就诊主键做一次批量 {@code IN} 查询后在内存汇总。</p>
 */
@Getter
@Setter
public class OutpatientEncounterListFinancialTotals {

    /** 费用总额：该就诊下未逻辑删除的收费明细 {@code total_price} 之和。 */
    private BigDecimal totalAmount;

    /** 已结算金额：明细状态为 SETTLED 的 {@code total_price} 之和。 */
    private BigDecimal paidAmount;

    /** 已退款金额：明细状态为 REFUNDED 的 {@code total_price} 之和。 */
    private BigDecimal refundAmount;
}
