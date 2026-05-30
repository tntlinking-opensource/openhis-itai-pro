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

package com.openhis.health.domain.financial.paymentreconciliation.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 支付单关联费用明细集合读模型：解析后的明细主键列表及解析时刻（供对账与展示）。
 */
@Getter
@Setter
public class ChargeItemIdsCollectionQueryModel {
    /** 支付单主键。 */
    private Long paymentId;
    /** 费用明细主键列表。 */
    private List<Long> chargeItemIds;
    /** 解析或快照生成时间。 */
    private LocalDateTime parsedAt;
}
