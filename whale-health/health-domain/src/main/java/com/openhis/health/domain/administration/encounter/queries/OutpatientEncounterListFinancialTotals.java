/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
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
