/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.periodbalancedetail.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 库房期初期末明细列表行 DTO（IF-INV-SET-02）。
 */
@Getter
@Setter
@Accessors(chain = true)
@Schema(description = "库房期初期末明细列表行")
public class PeriodBalanceDetailListRowDto {

    /** 品种主键。 */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "品种主键")
    private Long itemId;

    /** 院内编码。 */
    @Schema(description = "院内编码")
    private String itemNo;

    /** 药品名称（产品通用名/项目名称）。 */
    @Schema(description = "药品名称")
    private String productName;

    /** 商品名。 */
    @Schema(description = "商品名")
    private String itemName;

    /** 规格。 */
    @Schema(description = "规格")
    private String specText;

    /** 厂商。 */
    @Schema(description = "厂商")
    private String manufacturerText;

    /** 分类 code。 */
    @Schema(description = "分类 code")
    private String categoryCode;

    /** 分类展示。 */
    @Schema(description = "分类展示")
    private String categoryCodeLabel;

    /** 包装单位 code。 */
    @Schema(description = "包装单位 code")
    private String packageUnitCode;

    /** 包装单位展示。 */
    @Schema(description = "包装单位展示")
    private String packageUnitCodeLabel;

    /** 拆零单位 code。 */
    @Schema(description = "拆零单位 code")
    private String minUnitCode;

    /** 拆零单位展示。 */
    @Schema(description = "拆零单位展示")
    private String minUnitCodeLabel;

    /** 包装换算比。 */
    @Schema(description = "包装换算比")
    private BigDecimal packageUnitRatio;

    /** 期初数量（最小单位）。 */
    @Schema(description = "期初数量（最小单位）")
    private BigDecimal beginQuantity;

    /** 期初包装展示串。 */
    @Schema(description = "期初包装展示串")
    private String beginPackageStockText;

    /** 期初拆零展示串。 */
    @Schema(description = "期初拆零展示串")
    private String beginSplitStockText;

    /** 期初进价总额。 */
    @Schema(description = "期初进价总额")
    private BigDecimal beginCostAmount;

    /** 期初售价总额。 */
    @Schema(description = "期初售价总额")
    private BigDecimal beginSaleAmount;

    /** 期初包装进价（单批号一致时可展示）。 */
    @Schema(description = "期初包装进价")
    private BigDecimal beginCostPackageUnitPrice;

    /** 期初包装售价（单批号一致时可展示）。 */
    @Schema(description = "期初包装售价")
    private BigDecimal beginSalePackageUnitPrice;

    /** 实际期末数量。 */
    @Schema(description = "实际期末数量")
    private BigDecimal endQuantity;

    /** 期末包装展示串。 */
    @Schema(description = "期末包装展示串")
    private String endPackageStockText;

    /** 期末拆零展示串。 */
    @Schema(description = "期末拆零展示串")
    private String endSplitStockText;

    /** 实际期末进价总额。 */
    @Schema(description = "实际期末进价总额")
    private BigDecimal endCostAmount;

    /** 实际期末售价总额。 */
    @Schema(description = "实际期末售价总额")
    private BigDecimal endSaleAmount;

    /** 期末包装进价（单批号一致时可展示）。 */
    @Schema(description = "期末包装进价")
    private BigDecimal endCostPackageUnitPrice;

    /** 期末包装售价（单批号一致时可展示）。 */
    @Schema(description = "期末包装售价")
    private BigDecimal endSalePackageUnitPrice;

    /** 推算期末数量。 */
    @Schema(description = "推算期末数量")
    private BigDecimal computedEndQuantity;

    /** 推算期末进价总额。 */
    @Schema(description = "推算期末进价总额")
    private BigDecimal computedEndCostAmount;

    /** 推算期末售价总额。 */
    @Schema(description = "推算期末售价总额")
    private BigDecimal computedEndSaleAmount;

    /** 盈亏差数量。 */
    @Schema(description = "盈亏差数量")
    private BigDecimal quantityDiff;

    /** 盈亏差进价额。 */
    @Schema(description = "盈亏差进价额")
    private BigDecimal costAmountDiff;

    /** 盈亏差售价额。 */
    @Schema(description = "盈亏差售价额")
    private BigDecimal saleAmountDiff;

    /** 是否账平。 */
    @Schema(description = "是否账平")
    private Boolean isBalance;

    /** 进货数量。 */
    @Schema(description = "进货数量")
    private BigDecimal purchaseQuantity;

    /** 退供应商数量。 */
    @Schema(description = "退供应商数量")
    private BigDecimal returnToSupplierQuantity;

    /** 出库数量。 */
    @Schema(description = "出库数量")
    private BigDecimal stockOutQuantity;

    /** 退库入库数量。 */
    @Schema(description = "退库入库数量")
    private BigDecimal returnToWarehouseQuantity;

    /** 调入数量。 */
    @Schema(description = "调入数量")
    private BigDecimal transferInQuantity;

    /** 调出数量。 */
    @Schema(description = "调出数量")
    private BigDecimal transferOutQuantity;

    /** 药房入库数量。 */
    @Schema(description = "药房入库数量")
    private BigDecimal stockInQuantity;

    /** 发药数量。 */
    @Schema(description = "发药数量")
    private BigDecimal dispenseQuantity;

    /** 退药数量。 */
    @Schema(description = "退药数量")
    private BigDecimal refundDispenseQuantity;

    /** 领用数量。 */
    @Schema(description = "领用数量")
    private BigDecimal deptIssueQuantity;

    /** 退领数量。 */
    @Schema(description = "退领数量")
    private BigDecimal deptIssueReturnQuantity;

    /** 盘点盈数量。 */
    @Schema(description = "盘点盈数量")
    private BigDecimal stocktakeGainQuantity;

    /** 盘点亏数量。 */
    @Schema(description = "盘点亏数量")
    private BigDecimal stocktakeLossQuantity;

    /** 损益盈数量。 */
    @Schema(description = "损益盈数量")
    private BigDecimal gainQuantity;

    /** 损益亏数量。 */
    @Schema(description = "损益亏数量")
    private BigDecimal lossQuantity;

    /** 进货进价额。 */
    @Schema(description = "进货进价额")
    private BigDecimal purchaseCostAmount;

    /** 进货售价额。 */
    @Schema(description = "进货售价额")
    private BigDecimal purchaseSaleAmount;

    /** 进货包装进价。 */
    @Schema(description = "进货包装进价")
    private BigDecimal purchaseCostPackageUnitPrice;

    /** 进货包装售价。 */
    @Schema(description = "进货包装售价")
    private BigDecimal purchaseSalePackageUnitPrice;

    /** 退供应商进价额。 */
    @Schema(description = "退供应商进价额")
    private BigDecimal returnToSupplierCostAmount;

    /** 退供应商售价额。 */
    @Schema(description = "退供应商售价额")
    private BigDecimal returnToSupplierSaleAmount;

    /** 退供应商包装进价。 */
    @Schema(description = "退供应商包装进价")
    private BigDecimal returnToSupplierCostPackageUnitPrice;

    /** 退供应商包装售价。 */
    @Schema(description = "退供应商包装售价")
    private BigDecimal returnToSupplierSalePackageUnitPrice;

    /** 出库进价额。 */
    @Schema(description = "出库进价额")
    private BigDecimal stockOutCostAmount;

    /** 出库售价额。 */
    @Schema(description = "出库售价额")
    private BigDecimal stockOutSaleAmount;

    /** 出库包装进价。 */
    @Schema(description = "出库包装进价")
    private BigDecimal stockOutCostPackageUnitPrice;

    /** 出库包装售价。 */
    @Schema(description = "出库包装售价")
    private BigDecimal stockOutSalePackageUnitPrice;

    /** 退库进价额。 */
    @Schema(description = "退库进价额")
    private BigDecimal returnToWarehouseCostAmount;

    /** 退库售价额。 */
    @Schema(description = "退库售价额")
    private BigDecimal returnToWarehouseSaleAmount;

    /** 退库包装进价。 */
    @Schema(description = "退库包装进价")
    private BigDecimal returnToWarehouseCostPackageUnitPrice;

    /** 退库包装售价。 */
    @Schema(description = "退库包装售价")
    private BigDecimal returnToWarehouseSalePackageUnitPrice;

    /** 调入进价额。 */
    @Schema(description = "调入进价额")
    private BigDecimal transferInCostAmount;

    /** 调入售价额。 */
    @Schema(description = "调入售价额")
    private BigDecimal transferInSaleAmount;

    /** 调入包装进价。 */
    @Schema(description = "调入包装进价")
    private BigDecimal transferInCostPackageUnitPrice;

    /** 调入包装售价。 */
    @Schema(description = "调入包装售价")
    private BigDecimal transferInSalePackageUnitPrice;

    /** 调出进价额。 */
    @Schema(description = "调出进价额")
    private BigDecimal transferOutCostAmount;

    /** 调出售价额。 */
    @Schema(description = "调出售价额")
    private BigDecimal transferOutSaleAmount;

    /** 调出包装进价。 */
    @Schema(description = "调出包装进价")
    private BigDecimal transferOutCostPackageUnitPrice;

    /** 调出包装售价。 */
    @Schema(description = "调出包装售价")
    private BigDecimal transferOutSalePackageUnitPrice;

    /** 药房入库进价额。 */
    @Schema(description = "药房入库进价额")
    private BigDecimal stockInCostAmount;

    /** 药房入库售价额。 */
    @Schema(description = "药房入库售价额")
    private BigDecimal stockInSaleAmount;

    /** 入库包装进价。 */
    @Schema(description = "入库包装进价")
    private BigDecimal stockInCostPackageUnitPrice;

    /** 入库包装售价。 */
    @Schema(description = "入库包装售价")
    private BigDecimal stockInSalePackageUnitPrice;

    /** 发药进价额。 */
    @Schema(description = "发药进价额")
    private BigDecimal dispenseCostAmount;

    /** 发药售价额。 */
    @Schema(description = "发药售价额")
    private BigDecimal dispenseSaleAmount;

    /** 发药包装进价。 */
    @Schema(description = "发药包装进价")
    private BigDecimal dispenseCostPackageUnitPrice;

    /** 发药包装售价。 */
    @Schema(description = "发药包装售价")
    private BigDecimal dispenseSalePackageUnitPrice;

    /** 退药进价额。 */
    @Schema(description = "退药进价额")
    private BigDecimal refundDispenseCostAmount;

    /** 退药售价额。 */
    @Schema(description = "退药售价额")
    private BigDecimal refundDispenseSaleAmount;

    /** 退药包装进价。 */
    @Schema(description = "退药包装进价")
    private BigDecimal refundDispenseCostPackageUnitPrice;

    /** 退药包装售价。 */
    @Schema(description = "退药包装售价")
    private BigDecimal refundDispenseSalePackageUnitPrice;

    /** 领用进价额。 */
    @Schema(description = "领用进价额")
    private BigDecimal deptIssueCostAmount;

    /** 领用售价额。 */
    @Schema(description = "领用售价额")
    private BigDecimal deptIssueSaleAmount;

    /** 领用包装进价。 */
    @Schema(description = "领用包装进价")
    private BigDecimal deptIssueCostPackageUnitPrice;

    /** 领用包装售价。 */
    @Schema(description = "领用包装售价")
    private BigDecimal deptIssueSalePackageUnitPrice;

    /** 退领进价额。 */
    @Schema(description = "退领进价额")
    private BigDecimal deptIssueReturnCostAmount;

    /** 退领售价额。 */
    @Schema(description = "退领售价额")
    private BigDecimal deptIssueReturnSaleAmount;

    /** 退领包装进价。 */
    @Schema(description = "退领包装进价")
    private BigDecimal deptIssueReturnCostPackageUnitPrice;

    /** 退领包装售价。 */
    @Schema(description = "退领包装售价")
    private BigDecimal deptIssueReturnSalePackageUnitPrice;

    /** 盘点盈进价额。 */
    @Schema(description = "盘点盈进价额")
    private BigDecimal stocktakeGainCostAmount;

    /** 盘点亏进价额。 */
    @Schema(description = "盘点亏进价额")
    private BigDecimal stocktakeLossCostAmount;

    /** 损益盈进价额。 */
    @Schema(description = "损益盈进价额")
    private BigDecimal gainCostAmount;

    /** 损益亏进价额。 */
    @Schema(description = "损益亏进价额")
    private BigDecimal lossCostAmount;

    /**
     * 本库期间调价进价盈亏（主表列；无调价时可空或 0）。
     */
    @Schema(description = "调价进价盈亏")
    private BigDecimal priceAdjustmentCostAmount;
}
