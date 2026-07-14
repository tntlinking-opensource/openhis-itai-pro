/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.dailysettlement.support;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 库房日结品种级期间业务累加器（内部模型）。
 */
@Getter
@Setter
public class DailySettlementAggregateRow {

    /** 品种主键。 */
    private Long itemId;

    /** 进货数量。 */
    private BigDecimal purchaseQuantity = BigDecimal.ZERO;

    /** 退供应商数量。 */
    private BigDecimal returnToSupplierQuantity = BigDecimal.ZERO;

    /** 出库数量。 */
    private BigDecimal stockOutQuantity = BigDecimal.ZERO;

    /** 退库入库数量。 */
    private BigDecimal returnToWarehouseQuantity = BigDecimal.ZERO;

    /** 调入数量。 */
    private BigDecimal transferInQuantity = BigDecimal.ZERO;

    /** 调出数量。 */
    private BigDecimal transferOutQuantity = BigDecimal.ZERO;

    /** 药房入库数量。 */
    private BigDecimal stockInQuantity = BigDecimal.ZERO;

    /** 发药数量。 */
    private BigDecimal dispenseQuantity = BigDecimal.ZERO;

    /** 退药数量。 */
    private BigDecimal refundDispenseQuantity = BigDecimal.ZERO;

    /** 领用数量。 */
    private BigDecimal deptIssueQuantity = BigDecimal.ZERO;

    /** 退领数量。 */
    private BigDecimal deptIssueReturnQuantity = BigDecimal.ZERO;

    /** 盘点盈数量。 */
    private BigDecimal stocktakeGainQuantity = BigDecimal.ZERO;

    /** 盘点亏数量。 */
    private BigDecimal stocktakeLossQuantity = BigDecimal.ZERO;

    /** 损益盈数量。 */
    private BigDecimal gainQuantity = BigDecimal.ZERO;

    /** 损益亏数量。 */
    private BigDecimal lossQuantity = BigDecimal.ZERO;

    /** 进货进价额。 */
    private BigDecimal purchaseCostAmount = BigDecimal.ZERO;

    /** 进货售价额。 */
    private BigDecimal purchaseSaleAmount = BigDecimal.ZERO;

    /** 退供应商进价额。 */
    private BigDecimal returnToSupplierCostAmount = BigDecimal.ZERO;

    /** 退供应商售价额。 */
    private BigDecimal returnToSupplierSaleAmount = BigDecimal.ZERO;

    /** 出库进价额。 */
    private BigDecimal stockOutCostAmount = BigDecimal.ZERO;

    /** 出库售价额。 */
    private BigDecimal stockOutSaleAmount = BigDecimal.ZERO;

    /** 退库进价额。 */
    private BigDecimal returnToWarehouseCostAmount = BigDecimal.ZERO;

    /** 退库售价额。 */
    private BigDecimal returnToWarehouseSaleAmount = BigDecimal.ZERO;

    /** 调入进价额。 */
    private BigDecimal transferInCostAmount = BigDecimal.ZERO;

    /** 调入售价额。 */
    private BigDecimal transferInSaleAmount = BigDecimal.ZERO;

    /** 调出进价额。 */
    private BigDecimal transferOutCostAmount = BigDecimal.ZERO;

    /** 调出售价额。 */
    private BigDecimal transferOutSaleAmount = BigDecimal.ZERO;

    /** 药房入库进价额。 */
    private BigDecimal stockInCostAmount = BigDecimal.ZERO;

    /** 药房入库售价额。 */
    private BigDecimal stockInSaleAmount = BigDecimal.ZERO;

    /** 发药进价额。 */
    private BigDecimal dispenseCostAmount = BigDecimal.ZERO;

    /** 发药售价额。 */
    private BigDecimal dispenseSaleAmount = BigDecimal.ZERO;

    /** 退药进价额。 */
    private BigDecimal refundDispenseCostAmount = BigDecimal.ZERO;

    /** 退药售价额。 */
    private BigDecimal refundDispenseSaleAmount = BigDecimal.ZERO;

    /** 领用进价额。 */
    private BigDecimal deptIssueCostAmount = BigDecimal.ZERO;

    /** 领用售价额。 */
    private BigDecimal deptIssueSaleAmount = BigDecimal.ZERO;

    /** 退领进价额。 */
    private BigDecimal deptIssueReturnCostAmount = BigDecimal.ZERO;

    /** 退领售价额。 */
    private BigDecimal deptIssueReturnSaleAmount = BigDecimal.ZERO;

    /** 盘点盈进价额。 */
    private BigDecimal stocktakeGainCostAmount = BigDecimal.ZERO;

    /** 盘点亏进价额。 */
    private BigDecimal stocktakeLossCostAmount = BigDecimal.ZERO;

    /** 损益盈进价额。 */
    private BigDecimal gainCostAmount = BigDecimal.ZERO;

    /** 损益亏进价额。 */
    private BigDecimal lossCostAmount = BigDecimal.ZERO;

    /** 期间业务包装进价追踪（groupCode → 追踪器）。 */
    private java.util.Map<String, DailySettlementPackageUnitPriceTracker> costPackageUnitPriceByGroup =
        new java.util.HashMap<>();

    /** 期间业务包装售价追踪（groupCode → 追踪器）。 */
    private java.util.Map<String, DailySettlementPackageUnitPriceTracker> salePackageUnitPriceByGroup =
        new java.util.HashMap<>();
}
