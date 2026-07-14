/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
