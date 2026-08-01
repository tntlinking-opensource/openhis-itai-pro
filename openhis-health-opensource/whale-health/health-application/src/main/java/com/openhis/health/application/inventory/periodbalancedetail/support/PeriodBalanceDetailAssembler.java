/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inventory.periodbalancedetail.support;

import com.openhis.health.application.inventory.periodbalancedetail.dto.PeriodBalanceDetailColumnDto;
import com.openhis.health.application.inventory.periodbalancedetail.dto.PeriodBalanceDetailListRowDto;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionLotBatchKey;
import com.openhis.health.domain.share.constants.InventoryPeriodBalanceDetailConstants;
import com.openhis.health.domain.document.inventoryitemsnapshot.queries.model.DocInventoryItemSnapshotRowQueryModel;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.workflow.devicedispense.queries.model.DeviceDispenseSettlementLineQueryModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.SupplyRequestSettlementLineQueryModel;
import com.openhis.health.application.shared.product.DoctorStationAdviceInventoryUnitQuantityHelper;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 库房期初期末明细品种汇总、账平判定与动态列裁剪（BR-INV-SET-006/008/011/013）。
 */
@Component
@RequiredArgsConstructor
public class PeriodBalanceDetailAssembler {

    /** 行额计算。 */
    private final PeriodBalanceDetailPricingAssembler pricingSupport;

    /**
     * 快照批号行按品种 SUM 数量与冻结金额。
     *
     * @param rows 快照行
     * @return itemId → 汇总块
     */
    public Map<Long, PeriodBalanceDetailAssemblyContext.ItemSnapshotSum> sumSnapshotRowsByItemId(
        List<DocInventoryItemSnapshotRowQueryModel> rows
    ) {
        Map<Long, PeriodBalanceDetailAssemblyContext.ItemSnapshotSum> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (DocInventoryItemSnapshotRowQueryModel row : rows) {
            if (row == null || row.getItemId() == null) {
                continue;
            }
            PeriodBalanceDetailAssemblyContext.ItemSnapshotSum sum = map.get(row.getItemId());
            if (sum == null) {
                sum = new PeriodBalanceDetailAssemblyContext.ItemSnapshotSum();
                sum.setItemId(row.getItemId());
                map.put(row.getItemId(), sum);
            }
            if (row.getQuantity() != null) {
                sum.setQuantity(sum.getQuantity().add(row.getQuantity()));
            }
            BigDecimal lineCost = this.pricingSupport.resolveSnapshotLineCostTotal(row, sum.getPackageUnitRatio());
            if (lineCost != null) {
                sum.setCostAmount(sum.getCostAmount().add(lineCost));
            }
            BigDecimal lineSale = this.pricingSupport.resolveSnapshotLineSaleTotal(row, sum.getPackageUnitRatio());
            if (lineSale != null) {
                sum.setSaleAmount(sum.getSaleAmount().add(lineSale));
            }
            if (row.getLotNumberCostAmount() != null) {
                sum.getCostPackageUnitPriceTracker().merge(row.getLotNumberCostAmount());
            }
            if (row.getLotNumberPriceAmount() != null) {
                sum.getSalePackageUnitPriceTracker().merge(row.getLotNumberPriceAmount());
            }
            if (sum.getPackageUnitRatio() == null && row.getPackageUnitRatio() != null) {
                sum.setPackageUnitRatio(row.getPackageUnitRatio());
            }
            if (sum.getPackageUnitCode() == null && row.getPackageUnitCode() != null) {
                sum.setPackageUnitCode(row.getPackageUnitCode());
            }
        }
        return map;
    }

    /**
     * 供应动账行写入品种累加器。
     *
     * @param lines      供应明细
     * @param locationId 选中库位
     * @param formEnum   库位形态
     * @param target     itemId → 累加器
     */
    public void accumulateSupplyLines(
        List<SupplyRequestSettlementLineQueryModel> lines,
        Long locationId,
        String formEnum,
        Map<Long, PeriodBalanceDetailAggregateRow> target
    ) {
        if (lines == null || locationId == null) {
            return;
        }
        for (SupplyRequestSettlementLineQueryModel line : lines) {
            if (line == null || line.getItemId() == null) {
                continue;
            }
            PeriodBalanceDetailAggregateRow row = this.resolveOrCreateAggregateRow(target, line.getItemId());
            this.applySupplyLine(row, line, locationId, formEnum);
        }
    }

    /**
     * 发药行有效拆零数量：优先正数实发量，回落医嘱开立量（**BR-INV-SET-028**）；
     * 再按发放行 {@code unit_code} 换算为拆零最小单位（与药房占用/扣减 **BR-ORD-021** 同口径）。
     *
     * @param line    发药读模型
     * @param product 品种展示（单位槽换算；缺则原样回落业务数量）
     * @return 拆零数量；无法解析时 null
     */
    public BigDecimal resolveMedicationDispenseSplitQuantity(
        MedicationDispenseSlipQueryModel line,
        ProductDefinitionDisplayDto product
    ) {
        if (line == null) {
            return null;
        }
        BigDecimal businessQty = line.getDispenseQuantity();
        if (businessQty == null || businessQty.compareTo(BigDecimal.ZERO) <= 0) {
            businessQty = line.getQuantity();
        }
        if (businessQty == null) {
            return null;
        }
        if (product != null && StringUtils.isNotEmpty(line.getUnitCode())) {
            return DoctorStationAdviceInventoryUnitQuantityHelper.businessQuantityToSplitUnit(
                product, line.getUnitCode(), businessQty);
        }
        return businessQty;
    }

    /**
     * 解析品种包装换算比（BR-INV-DTL-007）；缺省回落 1。
     *
     * @param itemId  品种主键
     * @param context 装配上下文
     * @return 包装换算比
     */
    public BigDecimal resolvePackageUnitRatio(Long itemId, PeriodBalanceDetailAssemblyContext context) {
        if (itemId != null && context.getProductByItemId() != null) {
            ProductDefinitionDisplayDto product = context.getProductByItemId().get(itemId);
            if (product != null && product.getPackageUnitRatio() != null
                && product.getPackageUnitRatio().compareTo(BigDecimal.ZERO) > 0) {
                return product.getPackageUnitRatio();
            }
        }
        return BigDecimal.ONE;
    }

    /**
     * 药品发退药行写入品种累加器。
     *
     * @param line       发药明细
     * @param product    品种展示（发药数量按 unit_code 换算拆零）
     * @param costAmount 行进价额（已算）
     * @param saleAmount 行售价额（已算）
     * @param costPackageUnitPrice 行进价包装单价
     * @param salePackageUnitPrice 行售价包装单价
     * @param target     累加器映射
     */
    public void accumulateMedicationDispenseLine(
        MedicationDispenseSlipQueryModel line,
        ProductDefinitionDisplayDto product,
        BigDecimal costAmount,
        BigDecimal saleAmount,
        BigDecimal costPackageUnitPrice,
        BigDecimal salePackageUnitPrice,
        Map<Long, PeriodBalanceDetailAggregateRow> target
    ) {
        if (line == null || line.getMedicationId() == null) {
            return;
        }
        BigDecimal splitQty = this.resolveMedicationDispenseSplitQuantity(line, product);
        if (splitQty == null) {
            return;
        }
        PeriodBalanceDetailAggregateRow row = this.resolveOrCreateAggregateRow(target, line.getMedicationId());
        BigDecimal qty = splitQty.abs();
        // 退药计入退药列，正向发药计入发药列
        if (line.getStatusEnum() == DispenseStatus.REFUNDED) {
            row.setRefundDispenseQuantity(row.getRefundDispenseQuantity().add(qty));
            row.setRefundDispenseCostAmount(this.addAmount(row.getRefundDispenseCostAmount(), costAmount));
            row.setRefundDispenseSaleAmount(this.addAmount(row.getRefundDispenseSaleAmount(), saleAmount));
            this.recordCostPackageUnitPrice(row, "refundDispense", costPackageUnitPrice);
            this.recordSalePackageUnitPrice(row, "refundDispense", salePackageUnitPrice);
        } else {
            row.setDispenseQuantity(row.getDispenseQuantity().add(qty));
            row.setDispenseCostAmount(this.addAmount(row.getDispenseCostAmount(), costAmount));
            row.setDispenseSaleAmount(this.addAmount(row.getDispenseSaleAmount(), saleAmount));
            this.recordCostPackageUnitPrice(row, "dispense", costPackageUnitPrice);
            this.recordSalePackageUnitPrice(row, "dispense", salePackageUnitPrice);
        }
    }

    /**
     * 耗材发退药行写入品种累加器。
     *
     * @param line       发药明细
     * @param costAmount 行进价额
     * @param saleAmount 行售价额
     * @param target     累加器映射
     */
    public void accumulateDeviceDispenseLine(
        DeviceDispenseSettlementLineQueryModel line,
        BigDecimal costAmount,
        BigDecimal saleAmount,
        Map<Long, PeriodBalanceDetailAggregateRow> target
    ) {
        if (line == null || line.getDeviceDefId() == null || line.getQuantity() == null) {
            return;
        }
        PeriodBalanceDetailAggregateRow row = this.resolveOrCreateAggregateRow(target, line.getDeviceDefId());
        BigDecimal qty = line.getQuantity().abs();
        // 退药计入退药列，正向发药计入发药列
        if (DispenseStatus.REFUNDED.getCode().equals(line.getStatusEnum())) {
            row.setRefundDispenseQuantity(row.getRefundDispenseQuantity().add(qty));
            row.setRefundDispenseCostAmount(this.addAmount(row.getRefundDispenseCostAmount(), costAmount));
            row.setRefundDispenseSaleAmount(this.addAmount(row.getRefundDispenseSaleAmount(), saleAmount));
        } else {
            row.setDispenseQuantity(row.getDispenseQuantity().add(qty));
            row.setDispenseCostAmount(this.addAmount(row.getDispenseCostAmount(), costAmount));
            row.setDispenseSaleAmount(this.addAmount(row.getDispenseSaleAmount(), saleAmount));
        }
    }

    /**
     * 将目录页品种装配为列表行并计算账平。
     *
     * @param pageItemIds 当前页品种主键
     * @param context     装配上下文
     * @return 列表行
     */
    public List<PeriodBalanceDetailListRowDto> buildListRows(
        List<Long> pageItemIds,
        PeriodBalanceDetailAssemblyContext context
    ) {
        List<PeriodBalanceDetailListRowDto> rows = new ArrayList<>();
        if (pageItemIds == null) {
            return rows;
        }
        for (Long itemId : pageItemIds) {
            if (itemId == null) {
                continue;
            }
            PeriodBalanceDetailListRowDto dto = this.buildSingleListRow(itemId, context);
            if (dto != null) {
                rows.add(dto);
            }
        }
        return rows;
    }

    /**
     * 应用 isBus / isBalance / keyword 筛选（BR-INV-SET-006）。
     *
     * @param rows       待筛选行
     * @param keyword    关键字
     * @param isBus      是否隐藏全 0 业务行
     * @param isBalance  账平筛选
     * @return 筛选后行
     */
    public List<PeriodBalanceDetailListRowDto> applyRowFilters(
        List<PeriodBalanceDetailListRowDto> rows,
        String keyword,
        Boolean isBus,
        Boolean isBalance
    ) {
        List<PeriodBalanceDetailListRowDto> filtered = new ArrayList<>();
        if (rows == null) {
            return filtered;
        }
        // 默认隐藏期间全 0 业务行（BR-INV-SET-006）
        boolean hideZeroBus = true;
        if (isBus != null && !isBus.booleanValue()) {
            hideZeroBus = false;
        }
        String keywordNorm = trimToNull(keyword);
        for (PeriodBalanceDetailListRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (keywordNorm != null && !this.matchesKeyword(row, keywordNorm)) {
                continue;
            }
            if (hideZeroBus && this.isAllZeroBusinessRow(row)) {
                continue;
            }
            if (isBalance != null) {
                boolean balanced = Boolean.TRUE.equals(row.getIsBalance());
                // 仅账平：跳过不平行
                if (isBalance.booleanValue() && !balanced) {
                    continue;
                }
                // 仅不平：跳过账平行
                if (!isBalance.booleanValue() && balanced) {
                    continue;
                }
            }
            filtered.add(row);
        }
        return filtered;
    }

    /**
     * 按库位形态解析动态列元数据（BR-INV-SET-021）。
     *
     * @param locationFormEnum 库位形态 code
     * @return 列元数据列表
     */
    public List<PeriodBalanceDetailColumnDto> resolveActiveColumns(String locationFormEnum) {
        List<PeriodBalanceDetailColumnDto> columns = new ArrayList<>();
        if (StringUtils.isEmpty(locationFormEnum)) {
            return columns;
        }
        String form = locationFormEnum.trim();
        if (LocationForm.WAREHOUSE.getCode().equals(form)) {
            this.addQtyCostGroup(columns, "purchase", "进货");
            this.addQtyCostGroup(columns, "returnToSupplier", "退供应商");
            this.addQtyCostGroup(columns, "stockOut", "出库");
            this.addQtyCostGroup(columns, "returnToWarehouse", "退库入库");
            this.addQtyCostGroup(columns, "transferIn", "调入");
            this.addQtyCostGroup(columns, "transferOut", "调出");
            this.addStocktakeGainLoss(columns);
            this.addGainLoss(columns);
        } else if (LocationForm.PHARMACY.getCode().equals(form)) {
            this.addQtyCostGroup(columns, "stockIn", "入库");
            this.addQtyCostGroup(columns, "returnToWarehouse", "退库");
            this.addQtyCostGroup(columns, "transferIn", "调入");
            this.addQtyCostGroup(columns, "transferOut", "调出");
            this.addQtyCostGroup(columns, "dispense", "发药");
            this.addQtyCostGroup(columns, "refundDispense", "退药");
            this.addStocktakeGainLoss(columns);
            this.addGainLoss(columns);
        } else if (LocationForm.DEPARTMENT_PHARMACY.getCode().equals(form)) {
            this.addQtyCostGroup(columns, "stockIn", "入库");
            this.addQtyCostGroup(columns, "returnToWarehouse", "退库");
            this.addQtyCostGroup(columns, "transferIn", "调入");
            this.addQtyCostGroup(columns, "transferOut", "调出");
            this.addQtyCostGroup(columns, "dispense", "发药");
            this.addQtyCostGroup(columns, "refundDispense", "退药");
            this.addQtyCostGroup(columns, "deptIssue", "领用");
            this.addQtyCostGroup(columns, "deptIssueReturn", "退领");
            this.addStocktakeGainLoss(columns);
            this.addGainLoss(columns);
        }
        return columns;
    }

    /**
     * 提取动态列 code 列表。
     *
     * @param columns 列元数据
     * @return code 列表
     */
    public List<String> extractActiveColumnCodes(List<PeriodBalanceDetailColumnDto> columns) {
        List<String> codes = new ArrayList<>();
        if (columns == null) {
            return codes;
        }
        for (PeriodBalanceDetailColumnDto column : columns) {
            if (column == null || StringUtils.isEmpty(column.getColumnCode())) {
                continue;
            }
            codes.add(column.getColumnCode());
        }
        return codes;
    }

    /**
     * 单行品种装配。
     *
     * @param itemId  品种主键
     * @param context 上下文
     * @return 列表行
     */
    private PeriodBalanceDetailListRowDto buildSingleListRow(Long itemId, PeriodBalanceDetailAssemblyContext context) {
        PeriodBalanceDetailListRowDto dto = new PeriodBalanceDetailListRowDto();
        dto.setItemId(itemId);
        ProductDefinitionDisplayDto product = null;
        if (context.getProductByItemId() != null) {
            product = context.getProductByItemId().get(itemId);
        }
        if (product != null) {
            dto.setItemNo(product.getProductBusNo());
            dto.setProductName(product.getProductName());
            dto.setItemName(product.getMerchandiseName());
            dto.setSpecText(product.getTotalVolume());
            dto.setManufacturerText(product.getManufacturer());
            dto.setCategoryCode(product.getCategoryCode());
            dto.setCategoryCodeLabel(product.getCategoryCodeDictText());
            dto.setPackageUnitCode(product.getPackageUnitCode());
            dto.setPackageUnitCodeLabel(product.getPackageUnitCodeDictText());
            dto.setMinUnitCode(product.getSplitUnitCode());
            dto.setMinUnitCodeLabel(product.getSplitUnitCodeDictText());
            dto.setPackageUnitRatio(product.getPackageUnitRatio());
        }
        PeriodBalanceDetailAssemblyContext.ItemSnapshotSum begin = null;
        if (context.getBeginByItemId() != null) {
            begin = context.getBeginByItemId().get(itemId);
        }
        BigDecimal beginQty = BigDecimal.ZERO;
        BigDecimal beginCost = BigDecimal.ZERO;
        BigDecimal beginSale = BigDecimal.ZERO;
        if (begin != null) {
            beginQty = begin.getQuantity();
            beginCost = begin.getCostAmount();
            beginSale = begin.getSaleAmount();
            if (dto.getPackageUnitRatio() == null) {
                dto.setPackageUnitRatio(begin.getPackageUnitRatio());
            }
        }
        dto.setBeginQuantity(beginQty);
        dto.setBeginCostAmount(this.pricingSupport.scaleMoney(beginCost));
        dto.setBeginSaleAmount(this.pricingSupport.scaleMoney(beginSale));
        if (begin != null) {
            dto.setBeginCostPackageUnitPrice(this.pricingSupport.scaleMoney(
                begin.getCostPackageUnitPriceTracker().resolveForDisplay()));
            dto.setBeginSalePackageUnitPrice(this.pricingSupport.scaleMoney(
                begin.getSalePackageUnitPriceTracker().resolveForDisplay()));
        }
        BigDecimal endQty = BigDecimal.ZERO;
        BigDecimal endCost = BigDecimal.ZERO;
        BigDecimal endSale = BigDecimal.ZERO;
        PeriodBalanceDetailAssemblyContext.ItemSnapshotSum endSum = null;
        if (context.isEndUsesRealtime()) {
            endSum = this.sumRealtimeEndForItem(itemId, context);
            if (endSum != null) {
                endQty = endSum.getQuantity();
                endCost = endSum.getCostAmount();
                endSale = endSum.getSaleAmount();
            }
        } else {
            if (context.getEndSnapshotByItemId() != null) {
                endSum = context.getEndSnapshotByItemId().get(itemId);
            }
            if (endSum != null) {
                endQty = endSum.getQuantity();
                endCost = endSum.getCostAmount();
                endSale = endSum.getSaleAmount();
            }
        }
        dto.setEndQuantity(endQty);
        dto.setEndCostAmount(this.pricingSupport.scaleMoney(endCost));
        dto.setEndSaleAmount(this.pricingSupport.scaleMoney(endSale));
        if (endSum != null) {
            dto.setEndCostPackageUnitPrice(this.pricingSupport.scaleMoney(
                endSum.getCostPackageUnitPriceTracker().resolveForDisplay()));
            dto.setEndSalePackageUnitPrice(this.pricingSupport.scaleMoney(
                endSum.getSalePackageUnitPriceTracker().resolveForDisplay()));
        }
        PeriodBalanceDetailAggregateRow period = null;
        if (context.getPeriodByItemId() != null) {
            period = context.getPeriodByItemId().get(itemId);
        }
        if (period != null) {
            this.copyPeriodFields(dto, period);
        }
        this.applyStockDisplayTexts(dto);
        this.computeBalanceFields(dto, context.getLocationFormEnum());
        this.nullifyInactiveColumns(dto, context.getLocationFormEnum());
        return dto;
    }

    /**
     * 实时期末按批号定价汇总单品种数量与金额。
     *
     * @param itemId  品种主键
     * @param context 上下文
     * @return 汇总块
     */
    private PeriodBalanceDetailAssemblyContext.ItemSnapshotSum sumRealtimeEndForItem(
        Long itemId,
        PeriodBalanceDetailAssemblyContext context
    ) {
        List<DocInventoryItemSnapshotRowQueryModel> lotRows = null;
        if (context.getEndRealtimeLotRowsByItemId() != null) {
            lotRows = context.getEndRealtimeLotRowsByItemId().get(itemId);
        }
        PeriodBalanceDetailAssemblyContext.ItemSnapshotSum sum = new PeriodBalanceDetailAssemblyContext.ItemSnapshotSum();
        sum.setItemId(itemId);
        if (lotRows == null) {
            return sum;
        }
        for (DocInventoryItemSnapshotRowQueryModel lotRow : lotRows) {
            if (lotRow == null) {
                continue;
            }
            BigDecimal qty = lotRow.getQuantity();
            if (qty != null) {
                sum.setQuantity(sum.getQuantity().add(qty));
            }
            BigDecimal productRatio = null;
            if (context.getProductByItemId() != null && context.getProductByItemId().get(itemId) != null) {
                productRatio = context.getProductByItemId().get(itemId).getPackageUnitRatio();
            }
            BigDecimal ratio = this.pricingSupport.resolveEffectivePackageUnitRatio(
                lotRow.getPackageUnitRatio(),
                productRatio
            );
            if (sum.getPackageUnitRatio() == null) {
                sum.setPackageUnitRatio(ratio);
            }
            String lotKey = null;
            ChargeItemDefinitionLotBatchKey batchKey = ChargeItemDefinitionLotBatchKey.maybeOf(
                lotRow.getItemTable(), lotRow.getItemId(), lotRow.getLotNumber());
            if (batchKey != null) {
                lotKey = batchKey.compositeKey();
            }
            BigDecimal costPkg = lotRow.getLotNumberCostAmount();
            if (costPkg == null && lotKey != null && context.getLotCostPriceByKey() != null) {
                costPkg = context.getLotCostPriceByKey().get(lotKey);
            }
            BigDecimal salePkg = lotRow.getLotNumberPriceAmount();
            if (salePkg == null && lotKey != null && context.getLotSalePriceByKey() != null) {
                salePkg = context.getLotSalePriceByKey().get(lotKey);
            }
            BigDecimal lineCost = this.pricingSupport.computeLineTotal(qty, ratio, costPkg);
            BigDecimal lineSale = this.pricingSupport.computeLineTotal(qty, ratio, salePkg);
            if (lineCost != null) {
                sum.setCostAmount(sum.getCostAmount().add(lineCost));
            }
            if (lineSale != null) {
                sum.setSaleAmount(sum.getSaleAmount().add(lineSale));
            }
            if (costPkg != null) {
                sum.getCostPackageUnitPriceTracker().merge(costPkg);
            }
            if (salePkg != null) {
                sum.getSalePackageUnitPriceTracker().merge(salePkg);
            }
        }
        return sum;
    }

    /**
     * 供应单行映射到动态列。
     *
     * @param aggregate  累加器
     * @param line       供应行
     * @param locationId 库位
     * @param formEnum   形态
     */
    private void applySupplyLine(
        PeriodBalanceDetailAggregateRow aggregate,
        SupplyRequestSettlementLineQueryModel line,
        Long locationId,
        String formEnum
    ) {
        String typeEnum = line.getTypeEnum();
        if (StringUtils.isEmpty(typeEnum)) {
            return;
        }
        BigDecimal qty = line.getItemQuantity();
        if (qty == null) {
            return;
        }
        BigDecimal costAmt = line.getTotalPrice();
        BigDecimal saleAmt = line.getTotalPrice();
        BigDecimal packageUnitPrice = line.getPrice();
        if (SupplyType.isStocktakeTypeCode(typeEnum) || SupplyType.isGainLossTypeCode(typeEnum)) {
            BigDecimal signed = qty;
            BigDecimal plPrice = line.getProfitLossPrice();
            if (plPrice == null) {
                plPrice = costAmt;
            }
            this.applySignedAdjustment(aggregate, typeEnum, signed, plPrice);
            return;
        }
        boolean sourceHit = locationId.equals(line.getSourceLocationId());
        boolean purposeHit = locationId.equals(line.getPurposeLocationId());
        BigDecimal absQty = qty.abs();
        if (SupplyType.CABINET_INBOUND_RECEIPT.getCode().equals(typeEnum) && purposeHit) {
            aggregate.setPurchaseQuantity(aggregate.getPurchaseQuantity().add(absQty));
            aggregate.setPurchaseCostAmount(this.addAmount(aggregate.getPurchaseCostAmount(), costAmt));
            aggregate.setPurchaseSaleAmount(this.addAmount(aggregate.getPurchaseSaleAmount(), saleAmt));
            this.recordCostPackageUnitPrice(aggregate, "purchase", packageUnitPrice);
            this.recordSalePackageUnitPrice(aggregate, "purchase", packageUnitPrice);
            return;
        }
        if (SupplyType.CABINET_RETURN_TO_SUPPLIER.getCode().equals(typeEnum) && purposeHit) {
            aggregate.setReturnToSupplierQuantity(aggregate.getReturnToSupplierQuantity().add(absQty));
            aggregate.setReturnToSupplierCostAmount(this.addAmount(aggregate.getReturnToSupplierCostAmount(), costAmt));
            aggregate.setReturnToSupplierSaleAmount(this.addAmount(aggregate.getReturnToSupplierSaleAmount(), saleAmt));
            this.recordCostPackageUnitPrice(aggregate, "returnToSupplier", packageUnitPrice);
            this.recordSalePackageUnitPrice(aggregate, "returnToSupplier", packageUnitPrice);
            return;
        }
        if (SupplyType.CABINET_STOCK_OUT.getCode().equals(typeEnum)) {
            if (LocationForm.WAREHOUSE.getCode().equals(formEnum) && sourceHit) {
                aggregate.setStockOutQuantity(aggregate.getStockOutQuantity().add(absQty));
                aggregate.setStockOutCostAmount(this.addAmount(aggregate.getStockOutCostAmount(), costAmt));
                aggregate.setStockOutSaleAmount(this.addAmount(aggregate.getStockOutSaleAmount(), saleAmt));
                this.recordCostPackageUnitPrice(aggregate, "stockOut", packageUnitPrice);
                this.recordSalePackageUnitPrice(aggregate, "stockOut", packageUnitPrice);
            } else if (purposeHit) {
                aggregate.setStockInQuantity(aggregate.getStockInQuantity().add(absQty));
                aggregate.setStockInCostAmount(this.addAmount(aggregate.getStockInCostAmount(), costAmt));
                aggregate.setStockInSaleAmount(this.addAmount(aggregate.getStockInSaleAmount(), saleAmt));
                this.recordCostPackageUnitPrice(aggregate, "stockIn", packageUnitPrice);
                this.recordSalePackageUnitPrice(aggregate, "stockIn", packageUnitPrice);
            }
            return;
        }
        if (SupplyType.PHARMACY_RETURN_TO_WAREHOUSE.getCode().equals(typeEnum)
            || SupplyType.DEPT_RETURN.getCode().equals(typeEnum)) {
            if (LocationForm.WAREHOUSE.getCode().equals(formEnum) && purposeHit) {
                aggregate.setReturnToWarehouseQuantity(aggregate.getReturnToWarehouseQuantity().add(absQty));
                aggregate.setReturnToWarehouseCostAmount(this.addAmount(aggregate.getReturnToWarehouseCostAmount(), costAmt));
                aggregate.setReturnToWarehouseSaleAmount(this.addAmount(aggregate.getReturnToWarehouseSaleAmount(), saleAmt));
                this.recordCostPackageUnitPrice(aggregate, "returnToWarehouse", packageUnitPrice);
                this.recordSalePackageUnitPrice(aggregate, "returnToWarehouse", packageUnitPrice);
            } else if (sourceHit) {
                aggregate.setReturnToWarehouseQuantity(aggregate.getReturnToWarehouseQuantity().add(absQty));
                aggregate.setReturnToWarehouseCostAmount(this.addAmount(aggregate.getReturnToWarehouseCostAmount(), costAmt));
                aggregate.setReturnToWarehouseSaleAmount(this.addAmount(aggregate.getReturnToWarehouseSaleAmount(), saleAmt));
                this.recordCostPackageUnitPrice(aggregate, "returnToWarehouse", packageUnitPrice);
                this.recordSalePackageUnitPrice(aggregate, "returnToWarehouse", packageUnitPrice);
            }
            return;
        }
        if (SupplyType.CABINET_TRANSFER_OUT.getCode().equals(typeEnum)
            || SupplyType.PHARMACY_TRANSFER_OUT.getCode().equals(typeEnum)
            || SupplyType.DEPT_TRANSFER_OUT.getCode().equals(typeEnum)) {
            if (sourceHit) {
                aggregate.setTransferOutQuantity(aggregate.getTransferOutQuantity().add(absQty));
                aggregate.setTransferOutCostAmount(this.addAmount(aggregate.getTransferOutCostAmount(), costAmt));
                aggregate.setTransferOutSaleAmount(this.addAmount(aggregate.getTransferOutSaleAmount(), saleAmt));
                this.recordCostPackageUnitPrice(aggregate, "transferOut", packageUnitPrice);
                this.recordSalePackageUnitPrice(aggregate, "transferOut", packageUnitPrice);
            }
            if (purposeHit) {
                aggregate.setTransferInQuantity(aggregate.getTransferInQuantity().add(absQty));
                aggregate.setTransferInCostAmount(this.addAmount(aggregate.getTransferInCostAmount(), costAmt));
                aggregate.setTransferInSaleAmount(this.addAmount(aggregate.getTransferInSaleAmount(), saleAmt));
                this.recordCostPackageUnitPrice(aggregate, "transferIn", packageUnitPrice);
                this.recordSalePackageUnitPrice(aggregate, "transferIn", packageUnitPrice);
            }
            return;
        }
        if (SupplyType.DEPT_ISSUE.getCode().equals(typeEnum) && purposeHit) {
            aggregate.setDeptIssueQuantity(aggregate.getDeptIssueQuantity().add(absQty));
            aggregate.setDeptIssueCostAmount(this.addAmount(aggregate.getDeptIssueCostAmount(), costAmt));
            aggregate.setDeptIssueSaleAmount(this.addAmount(aggregate.getDeptIssueSaleAmount(), saleAmt));
            this.recordCostPackageUnitPrice(aggregate, "deptIssue", packageUnitPrice);
            this.recordSalePackageUnitPrice(aggregate, "deptIssue", packageUnitPrice);
            return;
        }
        if (SupplyType.DEPT_ISSUE_RETURN.getCode().equals(typeEnum) && purposeHit) {
            aggregate.setDeptIssueReturnQuantity(aggregate.getDeptIssueReturnQuantity().add(absQty));
            aggregate.setDeptIssueReturnCostAmount(this.addAmount(aggregate.getDeptIssueReturnCostAmount(), costAmt));
            aggregate.setDeptIssueReturnSaleAmount(this.addAmount(aggregate.getDeptIssueReturnSaleAmount(), saleAmt));
            this.recordCostPackageUnitPrice(aggregate, "deptIssueReturn", packageUnitPrice);
            this.recordSalePackageUnitPrice(aggregate, "deptIssueReturn", packageUnitPrice);
        }
    }

    /**
     * 盘点/损益有符号量拆分盈/亏列。
     *
     * @param aggregate 累加器
     * @param typeEnum  类型
     * @param signedQty 有符号数量
     * @param signedAmt 有符号金额
     */
    private void applySignedAdjustment(
        PeriodBalanceDetailAggregateRow aggregate,
        String typeEnum,
        BigDecimal signedQty,
        BigDecimal signedAmt
    ) {
        if (signedQty == null) {
            return;
        }
        BigDecimal absQty = signedQty.abs();
        BigDecimal absAmt = signedAmt;
        if (absAmt != null) {
            absAmt = signedAmt.abs();
        }
        if (SupplyType.isStocktakeTypeCode(typeEnum)) {
            if (signedQty.compareTo(BigDecimal.ZERO) >= 0) {
                aggregate.setStocktakeGainQuantity(aggregate.getStocktakeGainQuantity().add(absQty));
                aggregate.setStocktakeGainCostAmount(this.addAmount(aggregate.getStocktakeGainCostAmount(), absAmt));
            } else {
                aggregate.setStocktakeLossQuantity(aggregate.getStocktakeLossQuantity().add(absQty));
                aggregate.setStocktakeLossCostAmount(this.addAmount(aggregate.getStocktakeLossCostAmount(), absAmt));
            }
            return;
        }
        if (signedQty.compareTo(BigDecimal.ZERO) >= 0) {
            aggregate.setGainQuantity(aggregate.getGainQuantity().add(absQty));
            aggregate.setGainCostAmount(this.addAmount(aggregate.getGainCostAmount(), absAmt));
        } else {
            aggregate.setLossQuantity(aggregate.getLossQuantity().add(absQty));
            aggregate.setLossCostAmount(this.addAmount(aggregate.getLossCostAmount(), absAmt));
        }
    }

    /**
     * 复制期间业务字段到 DTO。
     *
     * @param dto    目标 DTO
     * @param period 期间累加器
     */
    private void copyPeriodFields(PeriodBalanceDetailListRowDto dto, PeriodBalanceDetailAggregateRow period) {
        dto.setPurchaseQuantity(period.getPurchaseQuantity());
        dto.setReturnToSupplierQuantity(period.getReturnToSupplierQuantity());
        dto.setStockOutQuantity(period.getStockOutQuantity());
        dto.setReturnToWarehouseQuantity(period.getReturnToWarehouseQuantity());
        dto.setTransferInQuantity(period.getTransferInQuantity());
        dto.setTransferOutQuantity(period.getTransferOutQuantity());
        dto.setStockInQuantity(period.getStockInQuantity());
        dto.setDispenseQuantity(period.getDispenseQuantity());
        dto.setRefundDispenseQuantity(period.getRefundDispenseQuantity());
        dto.setDeptIssueQuantity(period.getDeptIssueQuantity());
        dto.setDeptIssueReturnQuantity(period.getDeptIssueReturnQuantity());
        dto.setStocktakeGainQuantity(period.getStocktakeGainQuantity());
        dto.setStocktakeLossQuantity(period.getStocktakeLossQuantity());
        dto.setGainQuantity(period.getGainQuantity());
        dto.setLossQuantity(period.getLossQuantity());
        dto.setPurchaseCostAmount(this.pricingSupport.scaleMoney(period.getPurchaseCostAmount()));
        dto.setPurchaseSaleAmount(this.pricingSupport.scaleMoney(period.getPurchaseSaleAmount()));
        dto.setReturnToSupplierCostAmount(this.pricingSupport.scaleMoney(period.getReturnToSupplierCostAmount()));
        dto.setReturnToSupplierSaleAmount(this.pricingSupport.scaleMoney(period.getReturnToSupplierSaleAmount()));
        dto.setStockOutCostAmount(this.pricingSupport.scaleMoney(period.getStockOutCostAmount()));
        dto.setStockOutSaleAmount(this.pricingSupport.scaleMoney(period.getStockOutSaleAmount()));
        dto.setReturnToWarehouseCostAmount(this.pricingSupport.scaleMoney(period.getReturnToWarehouseCostAmount()));
        dto.setReturnToWarehouseSaleAmount(this.pricingSupport.scaleMoney(period.getReturnToWarehouseSaleAmount()));
        dto.setTransferInCostAmount(this.pricingSupport.scaleMoney(period.getTransferInCostAmount()));
        dto.setTransferInSaleAmount(this.pricingSupport.scaleMoney(period.getTransferInSaleAmount()));
        dto.setTransferOutCostAmount(this.pricingSupport.scaleMoney(period.getTransferOutCostAmount()));
        dto.setTransferOutSaleAmount(this.pricingSupport.scaleMoney(period.getTransferOutSaleAmount()));
        dto.setStockInCostAmount(this.pricingSupport.scaleMoney(period.getStockInCostAmount()));
        dto.setStockInSaleAmount(this.pricingSupport.scaleMoney(period.getStockInSaleAmount()));
        dto.setDispenseCostAmount(this.pricingSupport.scaleMoney(period.getDispenseCostAmount()));
        dto.setDispenseSaleAmount(this.pricingSupport.scaleMoney(period.getDispenseSaleAmount()));
        dto.setRefundDispenseCostAmount(this.pricingSupport.scaleMoney(period.getRefundDispenseCostAmount()));
        dto.setRefundDispenseSaleAmount(this.pricingSupport.scaleMoney(period.getRefundDispenseSaleAmount()));
        dto.setDeptIssueCostAmount(this.pricingSupport.scaleMoney(period.getDeptIssueCostAmount()));
        dto.setDeptIssueSaleAmount(this.pricingSupport.scaleMoney(period.getDeptIssueSaleAmount()));
        dto.setDeptIssueReturnCostAmount(this.pricingSupport.scaleMoney(period.getDeptIssueReturnCostAmount()));
        dto.setDeptIssueReturnSaleAmount(this.pricingSupport.scaleMoney(period.getDeptIssueReturnSaleAmount()));
        dto.setStocktakeGainCostAmount(this.pricingSupport.scaleMoney(period.getStocktakeGainCostAmount()));
        dto.setStocktakeLossCostAmount(this.pricingSupport.scaleMoney(period.getStocktakeLossCostAmount()));
        dto.setGainCostAmount(this.pricingSupport.scaleMoney(period.getGainCostAmount()));
        dto.setLossCostAmount(this.pricingSupport.scaleMoney(period.getLossCostAmount()));
        dto.setPriceAdjustmentCostAmount(this.pricingSupport.scaleMoney(period.getPriceAdjustmentCostAmount()));
        this.copyPeriodPackageUnitPrices(dto, period);
    }

    /**
     * 复制期间业务包装单价到 DTO（多批号混价时置空）。
     *
     * @param dto    目标 DTO
     * @param period 期间累加器
     */
    private void copyPeriodPackageUnitPrices(PeriodBalanceDetailListRowDto dto, PeriodBalanceDetailAggregateRow period) {
        dto.setPurchaseCostPackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "purchase", true));
        dto.setPurchaseSalePackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "purchase", false));
        dto.setReturnToSupplierCostPackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "returnToSupplier", true));
        dto.setReturnToSupplierSalePackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "returnToSupplier", false));
        dto.setStockOutCostPackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "stockOut", true));
        dto.setStockOutSalePackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "stockOut", false));
        dto.setReturnToWarehouseCostPackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "returnToWarehouse", true));
        dto.setReturnToWarehouseSalePackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "returnToWarehouse", false));
        dto.setTransferInCostPackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "transferIn", true));
        dto.setTransferInSalePackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "transferIn", false));
        dto.setTransferOutCostPackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "transferOut", true));
        dto.setTransferOutSalePackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "transferOut", false));
        dto.setStockInCostPackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "stockIn", true));
        dto.setStockInSalePackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "stockIn", false));
        dto.setDispenseCostPackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "dispense", true));
        dto.setDispenseSalePackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "dispense", false));
        dto.setRefundDispenseCostPackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "refundDispense", true));
        dto.setRefundDispenseSalePackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "refundDispense", false));
        dto.setDeptIssueCostPackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "deptIssue", true));
        dto.setDeptIssueSalePackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "deptIssue", false));
        dto.setDeptIssueReturnCostPackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "deptIssueReturn", true));
        dto.setDeptIssueReturnSalePackageUnitPrice(this.resolveScaledPackageUnitPrice(period, "deptIssueReturn", false));
    }

    /**
     * 计算推算期末与账平（BR-INV-SET-011/013/014）。
     *
     * @param dto              列表行
     * @param locationFormEnum 库位形态；药库退库入库为增，药房/科室退库为减（BR-INV-SET-008）
     */
    private void computeBalanceFields(PeriodBalanceDetailListRowDto dto, String locationFormEnum) {
        BigDecimal beginQty = this.zeroIfNull(dto.getBeginQuantity());
        BigDecimal computedQty = beginQty;
        computedQty = computedQty.add(this.zeroIfNull(dto.getPurchaseQuantity()));
        BigDecimal returnToWarehouseQty = this.zeroIfNull(dto.getReturnToWarehouseQuantity());
        if (this.isReturnToWarehouseIncreasesStock(locationFormEnum)) {
            computedQty = computedQty.add(returnToWarehouseQty);
        } else {
            computedQty = computedQty.subtract(returnToWarehouseQty);
        }
        computedQty = computedQty.add(this.zeroIfNull(dto.getTransferInQuantity()));
        computedQty = computedQty.add(this.zeroIfNull(dto.getStockInQuantity()));
        computedQty = computedQty.add(this.zeroIfNull(dto.getRefundDispenseQuantity()));
        computedQty = computedQty.add(this.zeroIfNull(dto.getDeptIssueReturnQuantity()));
        computedQty = computedQty.add(this.zeroIfNull(dto.getStocktakeGainQuantity()));
        computedQty = computedQty.add(this.zeroIfNull(dto.getGainQuantity()));
        computedQty = computedQty.subtract(this.zeroIfNull(dto.getReturnToSupplierQuantity()));
        computedQty = computedQty.subtract(this.zeroIfNull(dto.getStockOutQuantity()));
        computedQty = computedQty.subtract(this.zeroIfNull(dto.getTransferOutQuantity()));
        computedQty = computedQty.subtract(this.zeroIfNull(dto.getDispenseQuantity()));
        computedQty = computedQty.subtract(this.zeroIfNull(dto.getDeptIssueQuantity()));
        computedQty = computedQty.subtract(this.zeroIfNull(dto.getStocktakeLossQuantity()));
        computedQty = computedQty.subtract(this.zeroIfNull(dto.getLossQuantity()));
        dto.setComputedEndQuantity(computedQty);
        BigDecimal beginCost = this.zeroIfNull(dto.getBeginCostAmount());
        BigDecimal computedCost = beginCost;
        computedCost = this.addSignedBusinessCost(computedCost, dto, locationFormEnum);
        BigDecimal computedCostScaled = this.pricingSupport.scaleMoney(computedCost);
        dto.setComputedEndCostAmount(computedCostScaled);
        BigDecimal beginSale = this.zeroIfNull(dto.getBeginSaleAmount());
        BigDecimal computedSale = beginSale;
        computedSale = this.addSignedBusinessSale(computedSale, dto, locationFormEnum);
        BigDecimal computedSaleScaled = this.pricingSupport.scaleMoney(computedSale);
        dto.setComputedEndSaleAmount(computedSaleScaled);
        BigDecimal endQty = this.zeroIfNull(dto.getEndQuantity());
        BigDecimal endCost = this.zeroIfNull(dto.getEndCostAmount());
        BigDecimal endSale = this.zeroIfNull(dto.getEndSaleAmount());
        dto.setQuantityDiff(endQty.subtract(computedQty));
        dto.setCostAmountDiff(this.pricingSupport.scaleMoney(endCost.subtract(computedCostScaled)));
        dto.setSaleAmountDiff(this.pricingSupport.scaleMoney(endSale.subtract(computedSaleScaled)));
        boolean qtyBalanced = endQty.compareTo(computedQty) == 0;
        boolean costBalanced = endCost.compareTo(computedCostScaled) == 0;
        // 账平仅核对数量与进价额（售价侧本版不展示，不参与 isBalance）
        dto.setIsBalance(Boolean.valueOf(qtyBalanced && costBalanced));
    }

    /**
     * 进价侧期间业务累加到推算期末。
     *
     * @param base             期初
     * @param dto              行
     * @param locationFormEnum 库位形态
     * @return 推算进价
     */
    private BigDecimal addSignedBusinessCost(BigDecimal base, PeriodBalanceDetailListRowDto dto, String locationFormEnum) {
        BigDecimal result = base;
        result = result.add(this.zeroIfNull(dto.getPurchaseCostAmount()));
        BigDecimal returnToWarehouseCost = this.zeroIfNull(dto.getReturnToWarehouseCostAmount());
        if (this.isReturnToWarehouseIncreasesStock(locationFormEnum)) {
            result = result.add(returnToWarehouseCost);
        } else {
            result = result.subtract(returnToWarehouseCost);
        }
        result = result.add(this.zeroIfNull(dto.getTransferInCostAmount()));
        result = result.add(this.zeroIfNull(dto.getStockInCostAmount()));
        result = result.add(this.zeroIfNull(dto.getRefundDispenseCostAmount()));
        result = result.add(this.zeroIfNull(dto.getDeptIssueReturnCostAmount()));
        result = result.add(this.zeroIfNull(dto.getStocktakeGainCostAmount()));
        result = result.add(this.zeroIfNull(dto.getGainCostAmount()));
        result = result.subtract(this.zeroIfNull(dto.getReturnToSupplierCostAmount()));
        result = result.subtract(this.zeroIfNull(dto.getStockOutCostAmount()));
        result = result.subtract(this.zeroIfNull(dto.getTransferOutCostAmount()));
        result = result.subtract(this.zeroIfNull(dto.getDispenseCostAmount()));
        result = result.subtract(this.zeroIfNull(dto.getDeptIssueCostAmount()));
        result = result.subtract(this.zeroIfNull(dto.getStocktakeLossCostAmount()));
        result = result.subtract(this.zeroIfNull(dto.getLossCostAmount()));
        // 调价：数量不变，仅进价额加减（降价为负、涨价为正）
        result = result.add(this.zeroIfNull(dto.getPriceAdjustmentCostAmount()));
        return result;
    }

    /**
     * 售价侧期间业务累加到推算期末。
     *
     * @param base             期初
     * @param dto              行
     * @param locationFormEnum 库位形态
     * @return 推算售价
     */
    private BigDecimal addSignedBusinessSale(BigDecimal base, PeriodBalanceDetailListRowDto dto, String locationFormEnum) {
        BigDecimal result = base;
        result = result.add(this.zeroIfNull(dto.getPurchaseSaleAmount()));
        BigDecimal returnToWarehouseSale = this.zeroIfNull(dto.getReturnToWarehouseSaleAmount());
        if (this.isReturnToWarehouseIncreasesStock(locationFormEnum)) {
            result = result.add(returnToWarehouseSale);
        } else {
            result = result.subtract(returnToWarehouseSale);
        }
        result = result.add(this.zeroIfNull(dto.getTransferInSaleAmount()));
        result = result.add(this.zeroIfNull(dto.getStockInSaleAmount()));
        result = result.add(this.zeroIfNull(dto.getRefundDispenseSaleAmount()));
        result = result.add(this.zeroIfNull(dto.getDeptIssueReturnSaleAmount()));
        result = result.subtract(this.zeroIfNull(dto.getReturnToSupplierSaleAmount()));
        result = result.subtract(this.zeroIfNull(dto.getStockOutSaleAmount()));
        result = result.subtract(this.zeroIfNull(dto.getTransferOutSaleAmount()));
        result = result.subtract(this.zeroIfNull(dto.getDispenseSaleAmount()));
        result = result.subtract(this.zeroIfNull(dto.getDeptIssueSaleAmount()));
        return result;
    }

    /**
     * 不适用形态列置 null。
     *
     * @param dto      行
     * @param formEnum 形态
     */
    private void nullifyInactiveColumns(PeriodBalanceDetailListRowDto dto, String formEnum) {
        if (StringUtils.isEmpty(formEnum)) {
            return;
        }
        String form = formEnum.trim();
        if (LocationForm.WAREHOUSE.getCode().equals(form)) {
            dto.setStockInQuantity(null);
            dto.setStockInCostAmount(null);
            dto.setStockInSaleAmount(null);
            dto.setStockInCostPackageUnitPrice(null);
            dto.setStockInSalePackageUnitPrice(null);
            dto.setDispenseQuantity(null);
            dto.setDispenseCostAmount(null);
            dto.setDispenseSaleAmount(null);
            dto.setDispenseCostPackageUnitPrice(null);
            dto.setDispenseSalePackageUnitPrice(null);
            dto.setRefundDispenseQuantity(null);
            dto.setRefundDispenseCostAmount(null);
            dto.setRefundDispenseSaleAmount(null);
            dto.setRefundDispenseCostPackageUnitPrice(null);
            dto.setRefundDispenseSalePackageUnitPrice(null);
            dto.setDeptIssueQuantity(null);
            dto.setDeptIssueCostAmount(null);
            dto.setDeptIssueSaleAmount(null);
            dto.setDeptIssueCostPackageUnitPrice(null);
            dto.setDeptIssueSalePackageUnitPrice(null);
            dto.setDeptIssueReturnQuantity(null);
            dto.setDeptIssueReturnCostAmount(null);
            dto.setDeptIssueReturnSaleAmount(null);
            dto.setDeptIssueReturnCostPackageUnitPrice(null);
            dto.setDeptIssueReturnSalePackageUnitPrice(null);
        } else if (LocationForm.PHARMACY.getCode().equals(form)) {
            this.nullifyWarehouseOnlyPeriodColumns(dto);
            dto.setDeptIssueQuantity(null);
            dto.setDeptIssueCostAmount(null);
            dto.setDeptIssueSaleAmount(null);
            dto.setDeptIssueCostPackageUnitPrice(null);
            dto.setDeptIssueSalePackageUnitPrice(null);
            dto.setDeptIssueReturnQuantity(null);
            dto.setDeptIssueReturnCostAmount(null);
            dto.setDeptIssueReturnSaleAmount(null);
            dto.setDeptIssueReturnCostPackageUnitPrice(null);
            dto.setDeptIssueReturnSalePackageUnitPrice(null);
        } else if (LocationForm.DEPARTMENT_PHARMACY.getCode().equals(form)) {
            // 科室药房与药房共享发药/入库/调拨列，但保留领用/退领列
            this.nullifyWarehouseOnlyPeriodColumns(dto);
        }
    }

    /**
     * 药库专属期间列置 null（药房/科室药房报表不展示进货、出库等药库列）。
     *
     * @param dto 日结行
     */
    private void nullifyWarehouseOnlyPeriodColumns(PeriodBalanceDetailListRowDto dto) {
        dto.setPurchaseQuantity(null);
        dto.setPurchaseCostAmount(null);
        dto.setPurchaseSaleAmount(null);
        dto.setPurchaseCostPackageUnitPrice(null);
        dto.setPurchaseSalePackageUnitPrice(null);
        dto.setReturnToSupplierQuantity(null);
        dto.setReturnToSupplierCostAmount(null);
        dto.setReturnToSupplierSaleAmount(null);
        dto.setReturnToSupplierCostPackageUnitPrice(null);
        dto.setReturnToSupplierSalePackageUnitPrice(null);
        dto.setStockOutQuantity(null);
        dto.setStockOutCostAmount(null);
        dto.setStockOutSaleAmount(null);
        dto.setStockOutCostPackageUnitPrice(null);
        dto.setStockOutSalePackageUnitPrice(null);
    }

    /**
     * 装配期初/期末展示串。
     *
     * @param dto 行
     */
    private void applyStockDisplayTexts(PeriodBalanceDetailListRowDto dto) {
        BigDecimal ratio = dto.getPackageUnitRatio();
        String[] beginTexts = this.pricingSupport.buildStockDisplayTexts(
            dto.getBeginQuantity(), ratio, dto.getPackageUnitCodeLabel(), dto.getMinUnitCodeLabel());
        dto.setBeginPackageStockText(beginTexts[0]);
        dto.setBeginSplitStockText(beginTexts[1]);
        String[] endTexts = this.pricingSupport.buildStockDisplayTexts(
            dto.getEndQuantity(), ratio, dto.getPackageUnitCodeLabel(), dto.getMinUnitCodeLabel());
        dto.setEndPackageStockText(endTexts[0]);
        dto.setEndSplitStockText(endTexts[1]);
    }

    /**
     * 判断是否全零行：期初、期末数量均为 0，且期间业务列均为 0。
     *
     * @param row 行
     * @return 全零为 true
     */
    private boolean isAllZeroBusinessRow(PeriodBalanceDetailListRowDto row) {
        // 期初或期末有库存：不算全零行（BR-INV-SET-006 业务口径）
        if (!this.isZero(row.getBeginQuantity())) {
            return false;
        }
        if (!this.isZero(row.getEndQuantity())) {
            return false;
        }
        if (!this.isZero(row.getPurchaseQuantity())) {
            return false;
        }
        if (!this.isZero(row.getReturnToSupplierQuantity())) {
            return false;
        }
        if (!this.isZero(row.getStockOutQuantity())) {
            return false;
        }
        if (!this.isZero(row.getReturnToWarehouseQuantity())) {
            return false;
        }
        if (!this.isZero(row.getTransferInQuantity())) {
            return false;
        }
        if (!this.isZero(row.getTransferOutQuantity())) {
            return false;
        }
        if (!this.isZero(row.getStockInQuantity())) {
            return false;
        }
        if (!this.isZero(row.getDispenseQuantity())) {
            return false;
        }
        if (!this.isZero(row.getRefundDispenseQuantity())) {
            return false;
        }
        if (!this.isZero(row.getDeptIssueQuantity())) {
            return false;
        }
        if (!this.isZero(row.getDeptIssueReturnQuantity())) {
            return false;
        }
        if (!this.isZero(row.getStocktakeGainQuantity())) {
            return false;
        }
        if (!this.isZero(row.getStocktakeLossQuantity())) {
            return false;
        }
        if (!this.isZero(row.getGainQuantity())) {
            return false;
        }
        if (!this.isZero(row.getLossQuantity())) {
            return false;
        }
        // 期间有调价进价盈亏：不算全零业务行
        if (!this.isZero(row.getPriceAdjustmentCostAmount())) {
            return false;
        }
        return true;
    }

    /**
     * 关键字匹配药品名/商品名/院内编码（BR-INV-SET-006）。
     *
     * <p>{@code productName}=通用名，{@code itemName}=商品名；二者任一命中即可，避免只搜通用名被商品名字段滤掉。</p>
     *
     * @param row     行
     * @param keyword 关键字
     * @return 命中为 true
     */
    private boolean matchesKeyword(PeriodBalanceDetailListRowDto row, String keyword) {
        String lower = keyword.toLowerCase();
        // 药品名称（通用名/项目名称）
        if (row.getProductName() != null && row.getProductName().toLowerCase().contains(lower)) {
            return true;
        }
        // 商品名
        if (row.getItemName() != null && row.getItemName().toLowerCase().contains(lower)) {
            return true;
        }
        // 院内编码
        if (row.getItemNo() != null && row.getItemNo().toLowerCase().contains(lower)) {
            return true;
        }
        return false;
    }

    /**
     * 累加器获取或创建。
     *
     * @param target 映射
     * @param itemId 品种
     * @return 累加器
     */
    private PeriodBalanceDetailAggregateRow resolveOrCreateAggregateRow(
        Map<Long, PeriodBalanceDetailAggregateRow> target,
        Long itemId
    ) {
        PeriodBalanceDetailAggregateRow row = target.get(itemId);
        if (row == null) {
            row = new PeriodBalanceDetailAggregateRow();
            row.setItemId(itemId);
            target.put(itemId, row);
        }
        return row;
    }

    /**
     * 金额安全累加。
     *
     * @param base   基值
     * @param addend 加数
     * @return 合计
     */
    private BigDecimal addAmount(BigDecimal base, BigDecimal addend) {
        if (addend == null) {
            return base;
        }
        if (base == null) {
            return addend;
        }
        return base.add(addend);
    }

    /**
     * null 转零。
     *
     * @param value 值
     * @return 非 null 数值
     */
    private BigDecimal zeroIfNull(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    /**
     * 判断数量是否为 0。
     *
     * @param value 数量
     * @return 0 或 null 为 true
     */
    private boolean isZero(BigDecimal value) {
        if (value == null) {
            return true;
        }
        return value.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * 记录期间业务包装进价（按 groupCode）。
     *
     * @param row    累加器
     * @param group  业务组 code
     * @param price  包装进价
     */
    private void recordCostPackageUnitPrice(PeriodBalanceDetailAggregateRow row, String group, BigDecimal price) {
        if (row == null || StringUtils.isEmpty(group) || price == null) {
            return;
        }
        PeriodBalanceDetailPackageUnitPriceTracker tracker = row.getCostPackageUnitPriceByGroup().get(group);
        if (tracker == null) {
            tracker = new PeriodBalanceDetailPackageUnitPriceTracker();
            row.getCostPackageUnitPriceByGroup().put(group, tracker);
        }
        tracker.merge(price);
    }

    /**
     * 记录期间业务包装售价（按 groupCode）。
     *
     * @param row    累加器
     * @param group  业务组 code
     * @param price  包装售价
     */
    private void recordSalePackageUnitPrice(PeriodBalanceDetailAggregateRow row, String group, BigDecimal price) {
        if (row == null || StringUtils.isEmpty(group) || price == null) {
            return;
        }
        PeriodBalanceDetailPackageUnitPriceTracker tracker = row.getSalePackageUnitPriceByGroup().get(group);
        if (tracker == null) {
            tracker = new PeriodBalanceDetailPackageUnitPriceTracker();
            row.getSalePackageUnitPriceByGroup().put(group, tracker);
        }
        tracker.merge(price);
    }

    /**
     * 解析期间业务组包装单价并刻度化。
     *
     * @param period  累加器
     * @param group   业务组 code
     * @param costSide true 进价侧
     * @return 刻度化包装单价或 null
     */
    private BigDecimal resolveScaledPackageUnitPrice(
        PeriodBalanceDetailAggregateRow period,
        String group,
        boolean costSide
    ) {
        if (period == null || StringUtils.isEmpty(group)) {
            return null;
        }
        PeriodBalanceDetailPackageUnitPriceTracker tracker;
        if (costSide) {
            tracker = period.getCostPackageUnitPriceByGroup().get(group);
        } else {
            tracker = period.getSalePackageUnitPriceByGroup().get(group);
        }
        if (tracker == null) {
            return null;
        }
        return this.pricingSupport.scaleMoney(tracker.resolveForDisplay());
    }

    /**
     * 退库列是否增加库存：仅药库「退库入库」为增；药房/科室「退库」为减（BR-INV-SET-008）。
     *
     * @param locationFormEnum 库位形态 code
     * @return 药库形态为 true
     */
    private boolean isReturnToWarehouseIncreasesStock(String locationFormEnum) {
        if (StringUtils.isEmpty(locationFormEnum)) {
            return false;
        }
        return LocationForm.WAREHOUSE.getCode().equals(locationFormEnum.trim());
    }

    /**
     * 添加数量/进价额列组（售价侧本版不展示）。
     *
     * @param columns   列列表
     * @param fieldBase 字段基名
     * @param titleBase 标题基名
     */
    private void addQtyCostGroup(List<PeriodBalanceDetailColumnDto> columns, String fieldBase, String titleBase) {
        PeriodBalanceDetailColumnDto qtyCol = new PeriodBalanceDetailColumnDto();
        qtyCol.setColumnCode(fieldBase + "Quantity");
        qtyCol.setColumnTitle(titleBase + "数量");
        qtyCol.setValueType(InventoryPeriodBalanceDetailConstants.COLUMN_VALUE_TYPE_QUANTITY);
        qtyCol.setGroupCode(fieldBase);
        columns.add(qtyCol);
        PeriodBalanceDetailColumnDto costPkgCol = new PeriodBalanceDetailColumnDto();
        costPkgCol.setColumnCode(fieldBase + "CostPackageUnitPrice");
        costPkgCol.setColumnTitle(titleBase + "包装进价");
        costPkgCol.setValueType(InventoryPeriodBalanceDetailConstants.COLUMN_VALUE_TYPE_PACKAGE_UNIT_PRICE_COST);
        costPkgCol.setGroupCode(fieldBase);
        columns.add(costPkgCol);
        PeriodBalanceDetailColumnDto costCol = new PeriodBalanceDetailColumnDto();
        costCol.setColumnCode(fieldBase + "CostAmount");
        costCol.setColumnTitle(titleBase + "进价额");
        costCol.setValueType(InventoryPeriodBalanceDetailConstants.COLUMN_VALUE_TYPE_AMOUNT_COST);
        costCol.setGroupCode(fieldBase);
        columns.add(costCol);
    }

    /**
     * 盘点盈/亏列。
     *
     * @param columns 列列表
     */
    private void addStocktakeGainLoss(List<PeriodBalanceDetailColumnDto> columns) {
        PeriodBalanceDetailColumnDto gainQty = new PeriodBalanceDetailColumnDto();
        gainQty.setColumnCode("stocktakeGainQuantity");
        gainQty.setColumnTitle("盘点盈数量");
        gainQty.setValueType(InventoryPeriodBalanceDetailConstants.COLUMN_VALUE_TYPE_QUANTITY);
        gainQty.setGroupCode("stocktake");
        columns.add(gainQty);
        PeriodBalanceDetailColumnDto lossQty = new PeriodBalanceDetailColumnDto();
        lossQty.setColumnCode("stocktakeLossQuantity");
        lossQty.setColumnTitle("盘点亏数量");
        lossQty.setValueType(InventoryPeriodBalanceDetailConstants.COLUMN_VALUE_TYPE_QUANTITY);
        lossQty.setGroupCode("stocktake");
        columns.add(lossQty);
        PeriodBalanceDetailColumnDto gainCost = new PeriodBalanceDetailColumnDto();
        gainCost.setColumnCode("stocktakeGainCostAmount");
        gainCost.setColumnTitle("盘点盈进价额");
        gainCost.setValueType(InventoryPeriodBalanceDetailConstants.COLUMN_VALUE_TYPE_AMOUNT_COST);
        gainCost.setGroupCode("stocktake");
        columns.add(gainCost);
        PeriodBalanceDetailColumnDto lossCost = new PeriodBalanceDetailColumnDto();
        lossCost.setColumnCode("stocktakeLossCostAmount");
        lossCost.setColumnTitle("盘点亏进价额");
        lossCost.setValueType(InventoryPeriodBalanceDetailConstants.COLUMN_VALUE_TYPE_AMOUNT_COST);
        lossCost.setGroupCode("stocktake");
        columns.add(lossCost);
    }

    /**
     * 损益盈/亏列。
     *
     * @param columns 列列表
     */
    private void addGainLoss(List<PeriodBalanceDetailColumnDto> columns) {
        PeriodBalanceDetailColumnDto gainQty = new PeriodBalanceDetailColumnDto();
        gainQty.setColumnCode("gainQuantity");
        gainQty.setColumnTitle("损益盈数量");
        gainQty.setValueType(InventoryPeriodBalanceDetailConstants.COLUMN_VALUE_TYPE_QUANTITY);
        gainQty.setGroupCode("gainLoss");
        columns.add(gainQty);
        PeriodBalanceDetailColumnDto lossQty = new PeriodBalanceDetailColumnDto();
        lossQty.setColumnCode("lossQuantity");
        lossQty.setColumnTitle("损益亏数量");
        lossQty.setValueType(InventoryPeriodBalanceDetailConstants.COLUMN_VALUE_TYPE_QUANTITY);
        lossQty.setGroupCode("gainLoss");
        columns.add(lossQty);
        PeriodBalanceDetailColumnDto gainCost = new PeriodBalanceDetailColumnDto();
        gainCost.setColumnCode("gainCostAmount");
        gainCost.setColumnTitle("损益盈进价额");
        gainCost.setValueType(InventoryPeriodBalanceDetailConstants.COLUMN_VALUE_TYPE_AMOUNT_COST);
        gainCost.setGroupCode("gainLoss");
        columns.add(gainCost);
        PeriodBalanceDetailColumnDto lossCost = new PeriodBalanceDetailColumnDto();
        lossCost.setColumnCode("lossCostAmount");
        lossCost.setColumnTitle("损益亏进价额");
        lossCost.setValueType(InventoryPeriodBalanceDetailConstants.COLUMN_VALUE_TYPE_AMOUNT_COST);
        lossCost.setGroupCode("gainLoss");
        columns.add(lossCost);
    }
}
