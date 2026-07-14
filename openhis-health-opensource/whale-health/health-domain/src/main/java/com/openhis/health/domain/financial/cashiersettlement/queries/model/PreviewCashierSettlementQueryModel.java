/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.financial.cashiersettlement.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 未交账 payment 预览结果。
 */
@Getter
@Setter
public class PreviewCashierSettlementQueryModel {

    /** 候选 payment 列表。 */
    private List<UnsettledPaymentPreviewItemQueryModel> items = new ArrayList<>();

    /** 候选 payment 流水条数（BR-CS-011：含 0 元与收支抵净，与 items.size 一致）。 */
    private Integer paymentCount;
}
