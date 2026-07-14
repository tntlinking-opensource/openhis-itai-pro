/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
