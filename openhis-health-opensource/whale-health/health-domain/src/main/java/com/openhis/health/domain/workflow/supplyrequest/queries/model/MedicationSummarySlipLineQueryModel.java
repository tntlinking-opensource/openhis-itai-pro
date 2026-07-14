/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.supplyrequest.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 住院护士站汇总领药 · 药品汇总单合并明细行（**AS-IP-NUR-010 · IF-IP-SUM-03**）。
 *
 * <p>价格口径 **BR-IP-SUM-009**：{@code unitPrice} 取合并组 ChargeItem 单价；{@code totalPrice} 为组内
 * {@code ChargeItem.total_price} 之和。**不返回批号**（**BR-IP-SUM-010**）。</p>
 */
@Getter
@Setter
public class MedicationSummarySlipLineQueryModel {

    /** 供应请求明细行主键。 */
    private Long supplyRequestId;

    /** 汇总单号。 */
    private String busNo;

    /** 药品定义主键。 */
    private Long itemId;

    /** 拆零单位汇总数量（SupplyRequest.item_quantity）。 */
    private BigDecimal itemQuantity;

    /** 拆零单位编码（SupplyRequest.unit_code）。 */
    private String unitCode;

    /** 只读账单单价。 */
    private BigDecimal unitPrice;

    /** 只读账单总价累加（禁止单价×数量重算）。 */
    private BigDecimal totalPrice;
}
