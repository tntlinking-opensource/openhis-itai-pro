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

import java.math.BigDecimal;

/**
 * 支付静态快照读模型：按维度汇总的费用类型金额与关联明细串（用于收银台静态展示）。
 */
@Getter
@Setter
public class PaymentStaticSnapshotQueryModel {
    /** 快照行主键。 */
    private Long id;
    /** 所属支付/对账主键。 */
    private Long reconciliationId;
    /** 汇总维度序号或分组键。 */
    private Integer countBy;
    /** 费用类型编码。 */
    private String typeCode;
    /** 费用类型名称。 */
    private String typeName;
    /** 该类型合计金额。 */
    private BigDecimal amount;
    /** 纳入该汇总的费用明细 ID 序列化串。 */
    private String chargeItemIds;
}
