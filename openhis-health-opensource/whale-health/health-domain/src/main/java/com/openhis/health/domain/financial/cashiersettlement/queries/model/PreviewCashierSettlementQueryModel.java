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
