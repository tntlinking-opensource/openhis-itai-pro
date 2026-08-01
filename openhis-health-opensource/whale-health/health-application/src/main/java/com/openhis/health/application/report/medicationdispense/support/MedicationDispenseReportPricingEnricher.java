/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.medicationdispense.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.openhis.health.application.shared.pricing.HistoricalChargeItemPurchasePriceSupport;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.share.constants.DeptIssueReportConstants;
import com.openhis.health.domain.share.constants.MedicationDispenseReportConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.DeptIssueReportLineQueryModel;
import com.openhis.whale.core.application.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 发药报表进价/售价 enrich（BR-PRICE-SRC-001；金额刻度 BR-MON-001）。
 *
 * <p>患者侧：售价来自 ChargeItem；进价来自 {@link HistoricalChargeItemPurchasePriceSupport}。
 * 科室领用侧：进价来自 Supply 落库价；售价来自历史 SALE 单价 × 数量（退领带符号）。</p>
 */
@Component
@RequiredArgsConstructor
public class MedicationDispenseReportPricingEnricher {

    /** 历史进价/售价读侧。 */
    private final HistoricalChargeItemPurchasePriceSupport historicalChargeItemPurchasePriceSupport;

    /** 收费明细读侧（患者侧锚点匹配）。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /**
     * 批量回填患者侧明细行进价/售价（保持 unitPrice/totalAmount 为售价别名）。
     *
     * @param lines    已装配明细行；须含 dispenseId / encounterId / quantity / unitPrice / totalAmount
     * @param slipRows 同批发放事实（encounter 分组加载 ChargeItem）
     */
    public void enrichPatientDetailPurchaseSale(
        List<? extends MedicationDispensePurchaseSaleDetailLine> lines,
        List<MedicationDispenseSlipQueryModel> slipRows) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> purchaseTotalByDispenseId = new HashMap<>();
        Map<Long, BigDecimal> purchaseUnitByDispenseId = new HashMap<>();
        this.mergePatientPurchasePricingByEncounterGroups(slipRows, purchaseUnitByDispenseId, purchaseTotalByDispenseId);
        for (MedicationDispensePurchaseSaleDetailLine line : lines) {
            if (line == null) {
                continue;
            }
            BigDecimal saleUnit = MonetaryAmountSupport.scaleCalc(line.getUnitPrice());
            BigDecimal saleAmount = MonetaryAmountSupport.scaleCalc(line.getTotalAmount());
            line.setSaleUnitPrice(saleUnit);
            line.setSaleAmount(saleAmount);
            line.setUnitPrice(saleUnit);
            line.setTotalAmount(saleAmount);
            Long dispenseId = line.getDispenseId();
            if (dispenseId != null) {
                line.setPurchaseUnitPrice(purchaseUnitByDispenseId.get(dispenseId));
                line.setPurchaseAmount(purchaseTotalByDispenseId.get(dispenseId));
            }
        }
    }

    /**
     * 回填科室领用明细行进价/售价（退领数量/金额已为负）。
     *
     * @param line           明细行
     * @param deptLine       领用领域行
     * @param signedQuantity 带符号数量
     * @param signedPurchaseTotal 带符号进价总额
     */
    public void enrichDeptDetailPurchaseSale(
        MedicationDispensePurchaseSaleDetailLine line,
        DeptIssueReportLineQueryModel deptLine,
        BigDecimal signedQuantity,
        BigDecimal signedPurchaseTotal) {
        if (line == null || deptLine == null) {
            return;
        }
        BigDecimal purchaseUnit = MonetaryAmountSupport.scaleCalc(deptLine.getPrice());
        BigDecimal purchaseAmount = MonetaryAmountSupport.scaleCalc(signedPurchaseTotal);
        line.setPurchaseUnitPrice(purchaseUnit);
        line.setPurchaseAmount(purchaseAmount);
        BigDecimal saleUnit = this.historicalChargeItemPurchasePriceSupport.resolveHistoricalSaleUnitPrice(
            TableNameConstants.MED_MEDICATION_DEFINITION, deptLine.getItemId());
        BigDecimal saleAmount = this.lineTotalOrNull(saleUnit, signedQuantity);
        line.setSaleUnitPrice(saleUnit);
        line.setSaleAmount(saleAmount);
        line.setUnitPrice(saleUnit);
        line.setTotalAmount(saleAmount);
    }

    /**
     * 按汇总 dimensionKey 累加明细行进价/售价净额。
     *
     * @param summaryRows           汇总行；须含 dimensionKey
     * @param detailRows            已 enrich 的明细行
     * @param dimension             汇总维度码
     * @param categoryCodeByMedicationId 品规→类型（CATEGORY 维）
     * @param organizationIdByPharmacyLocationId 药房→上级科室（领用 DEPARTMENT 维回落）
     */
    public void aggregateSummaryPurchaseSaleAmounts(
        List<? extends MedicationDispensePurchaseSaleSummaryLine> summaryRows,
        List<? extends MedicationDispensePurchaseSaleDetailLine> detailRows,
        String dimension,
        Map<Long, String> categoryCodeByMedicationId,
        Map<Long, Long> organizationIdByPharmacyLocationId) {
        if (summaryRows == null || summaryRows.isEmpty() || detailRows == null || detailRows.isEmpty()) {
            return;
        }
        Map<String, MedicationDispensePurchaseSaleSummaryLine> summaryByKey = new LinkedHashMap<>();
        for (MedicationDispensePurchaseSaleSummaryLine summaryRow : summaryRows) {
            if (summaryRow != null && StringUtils.isNotEmpty(summaryRow.getDimensionKey())) {
                summaryByKey.put(summaryRow.getDimensionKey(), summaryRow);
            }
        }
        Map<Long, String> categoryMap = categoryCodeByMedicationId;
        if (categoryMap == null) {
            categoryMap = Map.of();
        }
        Map<Long, Long> orgByLocation = organizationIdByPharmacyLocationId;
        if (orgByLocation == null) {
            orgByLocation = Map.of();
        }
        Map<String, List<BigDecimal>> purchaseAmountsByKey = new HashMap<>();
        Map<String, List<BigDecimal>> saleAmountsByKey = new HashMap<>();
        for (MedicationDispensePurchaseSaleDetailLine detailRow : detailRows) {
            if (detailRow == null) {
                continue;
            }
            String bucketKey = this.resolveDetailDimensionKey(
                detailRow, dimension, categoryMap, orgByLocation);
            if (bucketKey == null) {
                continue;
            }
            this.accumulateAmount(purchaseAmountsByKey, bucketKey, detailRow.getPurchaseAmount());
            this.accumulateAmount(saleAmountsByKey, bucketKey, detailRow.getSaleAmount());
        }
        for (Map.Entry<String, MedicationDispensePurchaseSaleSummaryLine> entry : summaryByKey.entrySet()) {
            String key = entry.getKey();
            MedicationDispensePurchaseSaleSummaryLine summary = entry.getValue();
            BigDecimal purchaseNet = this.sumThenSettleOrNull(purchaseAmountsByKey.get(key));
            BigDecimal saleNet = this.sumThenSettleOrNull(saleAmountsByKey.get(key));
            summary.setPurchaseNetAmount(purchaseNet);
            summary.setSaleNetAmount(saleNet);
            summary.setNetAmount(saleNet);
        }
    }

    /**
     * 解析科室领用行带符号数量。
     *
     * @param line 领用行
     * @return 带符号数量
     */
    public BigDecimal resolveDeptSignedQuantity(DeptIssueReportLineQueryModel line) {
        BigDecimal quantity = line.getItemQuantity();
        if (quantity == null) {
            quantity = BigDecimal.ZERO;
        }
        if (SupplyType.DEPT_ISSUE_RETURN.getCode().equals(line.getTypeEnum())) {
            return quantity.negate();
        }
        return quantity;
    }

    /**
     * 解析科室领用行带符号进价总额。
     *
     * @param line 领用行
     * @return 带符号进价总额
     */
    public BigDecimal resolveDeptSignedPurchaseTotal(DeptIssueReportLineQueryModel line) {
        BigDecimal total = line.getTotalPrice();
        if (total == null) {
            return null;
        }
        if (SupplyType.DEPT_ISSUE_RETURN.getCode().equals(line.getTypeEnum())) {
            return total.negate();
        }
        return total;
    }

    /**
     * 按就诊分组加载 ChargeItem 并合并进价映射。
     *
     * @param slipRows               发放事实
     * @param purchaseUnitByDispenseId 输出：dispenseId → 进价单价
     * @param purchaseTotalByDispenseId 输出：dispenseId → 进价总额
     */
    private void mergePatientPurchasePricingByEncounterGroups(
        List<MedicationDispenseSlipQueryModel> slipRows,
        Map<Long, BigDecimal> purchaseUnitByDispenseId,
        Map<Long, BigDecimal> purchaseTotalByDispenseId) {
        if (slipRows == null || slipRows.isEmpty()) {
            return;
        }
        Map<Long, List<Long>> dispenseIdsByEncounterId = new HashMap<>();
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            if (slip == null || slip.getDispenseId() == null || slip.getEncounterId() == null) {
                continue;
            }
            dispenseIdsByEncounterId
                .computeIfAbsent(slip.getEncounterId(), ignored -> new ArrayList<>())
                .add(slip.getDispenseId());
        }
        for (Map.Entry<Long, List<Long>> entry : dispenseIdsByEncounterId.entrySet()) {
            List<ChargeItem> chargeRows = this.chargeItemQueryService.listChargeItemsForMedicationDispensePricing(
                entry.getKey(), entry.getValue());
            this.mergePurchasePricingFromChargeItems(
                chargeRows, purchaseUnitByDispenseId, purchaseTotalByDispenseId);
        }
    }

    /**
     * 从 ChargeItem 合并进价到 dispenseId 映射。
     *
     * @param chargeRows               收费明细
     * @param purchaseUnitByDispenseId 单价输出
     * @param purchaseTotalByDispenseId 总额输出
     */
    private void mergePurchasePricingFromChargeItems(
        List<ChargeItem> chargeRows,
        Map<Long, BigDecimal> purchaseUnitByDispenseId,
        Map<Long, BigDecimal> purchaseTotalByDispenseId) {
        if (chargeRows == null || chargeRows.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> purchaseUnitByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseUnitPriceByChargeItemId(chargeRows);
        // Pre：行额由单价推导，禁止再调 computePurchaseAmountByChargeItemId（双倍定价/批号/调价 IO）
        Map<Long, BigDecimal> purchaseByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseAmountFromUnitPrices(
                chargeRows, purchaseUnitByChargeItemId);
        Map<Long, BigDecimal> totalByDispense = new HashMap<>();
        Map<Long, BigDecimal> qtyByDispense = new HashMap<>();
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow == null || chargeRow.getDispenseId() == null || chargeRow.getId() == null) {
                continue;
            }
            Long dispenseKey = chargeRow.getDispenseId();
            BigDecimal purchaseAmount = purchaseByChargeItemId.get(chargeRow.getId());
            if (purchaseAmount != null) {
                BigDecimal existingTotal = totalByDispense.get(dispenseKey);
                if (existingTotal == null) {
                    totalByDispense.put(dispenseKey, purchaseAmount);
                } else {
                    totalByDispense.put(dispenseKey, existingTotal.add(purchaseAmount));
                }
            }
            if (chargeRow.getQuantityValue() != null) {
                BigDecimal existingQty = qtyByDispense.get(dispenseKey);
                if (existingQty == null) {
                    qtyByDispense.put(dispenseKey, chargeRow.getQuantityValue());
                } else {
                    qtyByDispense.put(dispenseKey, existingQty.add(chargeRow.getQuantityValue()));
                }
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : totalByDispense.entrySet()) {
            Long dispenseKey = entry.getKey();
            BigDecimal totalVal = MonetaryAmountSupport.scaleCalc(entry.getValue());
            purchaseTotalByDispenseId.put(dispenseKey, totalVal);
            BigDecimal qtyVal = qtyByDispense.get(dispenseKey);
            if (qtyVal != null && qtyVal.compareTo(BigDecimal.ZERO) > 0) {
                purchaseUnitByDispenseId.put(
                    dispenseKey, MonetaryAmountSupport.unitPriceFromLineTotal(totalVal, qtyVal));
            } else {
                BigDecimal unitFromChargeItem = this.firstPurchaseUnitForDispense(
                    chargeRows, purchaseUnitByChargeItemId, dispenseKey);
                if (unitFromChargeItem != null) {
                    purchaseUnitByDispenseId.put(dispenseKey, unitFromChargeItem);
                }
            }
        }
    }

    /**
     * 取 dispense 下首条可解析进价单价（数量缺失时回落）。
     *
     * @param chargeRows                  收费明细
     * @param purchaseUnitByChargeItemId  chargeItemId → 进价单价
     * @param dispenseKey                 发放主键
     * @return 进价单价或 null
     */
    private BigDecimal firstPurchaseUnitForDispense(
        List<ChargeItem> chargeRows,
        Map<Long, BigDecimal> purchaseUnitByChargeItemId,
        Long dispenseKey) {
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow == null || chargeRow.getId() == null) {
                continue;
            }
            if (!dispenseKey.equals(chargeRow.getDispenseId())) {
                continue;
            }
            BigDecimal unit = purchaseUnitByChargeItemId.get(chargeRow.getId());
            if (unit != null) {
                return unit;
            }
        }
        return null;
    }

    /**
     * 明细行 → 汇总 dimensionKey（与领域分桶键同构）。
     *
     * @param detailRow  明细行
     * @param dimension  汇总维
     * @param categoryCodeByMedicationId 品规→类型
     * @param organizationIdByPharmacyLocationId 药房→科室
     * @return 分桶键；无法解析时 null
     */
    private String resolveDetailDimensionKey(
        MedicationDispensePurchaseSaleDetailLine detailRow,
        String dimension,
        Map<Long, String> categoryCodeByMedicationId,
        Map<Long, Long> organizationIdByPharmacyLocationId) {
        if (MedicationDispenseReportConstants.SOURCE_DEPT_ISSUE.equals(detailRow.getSourceCode())) {
            return this.resolveDeptIssueDetailDimensionKey(
                detailRow, dimension, organizationIdByPharmacyLocationId);
        }
        return this.resolvePatientDetailDimensionKey(detailRow, dimension, categoryCodeByMedicationId);
    }

    /**
     * 患者侧明细 → dimensionKey。
     */
    private String resolvePatientDetailDimensionKey(
        MedicationDispensePurchaseSaleDetailLine detailRow,
        String dimension,
        Map<Long, String> categoryCodeByMedicationId) {
        if (MedicationDispenseReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)) {
            Long medicationId = detailRow.getMedicationId();
            if (medicationId == null) {
                return "ITEM:";
            }
            return "ITEM:" + medicationId;
        }
        if (MedicationDispenseReportConstants.SUMMARY_DIMENSION_CATEGORY.equals(dimension)) {
            String categoryCode = "";
            if (detailRow.getMedicationId() != null) {
                String mapped = categoryCodeByMedicationId.get(detailRow.getMedicationId());
                if (mapped != null) {
                    categoryCode = mapped;
                }
            }
            return "CATEGORY:" + categoryCode;
        }
        Long locationId = detailRow.getPharmacyLocationId();
        if (locationId == null) {
            return "PHARMACY:";
        }
        return "PHARMACY:" + locationId;
    }

    /**
     * 科室领用明细 → dimensionKey。
     */
    private String resolveDeptIssueDetailDimensionKey(
        MedicationDispensePurchaseSaleDetailLine detailRow,
        String dimension,
        Map<Long, Long> organizationIdByPharmacyLocationId) {
        if (DeptIssueReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)) {
            Long medicationId = detailRow.getMedicationId();
            if (medicationId == null) {
                return "ITEM:";
            }
            return "ITEM:" + medicationId;
        }
        if (DeptIssueReportConstants.SUMMARY_DIMENSION_PHARMACY.equals(dimension)) {
            Long locationId = detailRow.getPharmacyLocationId();
            if (locationId == null) {
                return "PHARMACY:";
            }
            return "PHARMACY:" + locationId;
        }
        Long orgId = detailRow.getDepartmentId();
        if (orgId == null && detailRow.getPharmacyLocationId() != null) {
            orgId = organizationIdByPharmacyLocationId.get(detailRow.getPharmacyLocationId());
        }
        if (orgId == null) {
            return "DEPARTMENT:";
        }
        return "DEPARTMENT:" + orgId;
    }

    /**
     * 单价 × 数量行额；单价缺失时 null。
     *
     * @param unitPrice 单价
     * @param quantity  数量
     * @return 行额或 null
     */
    private BigDecimal lineTotalOrNull(BigDecimal unitPrice, BigDecimal quantity) {
        if (unitPrice == null || quantity == null) {
            return null;
        }
        return MonetaryAmountSupport.lineTotal(unitPrice, quantity);
    }

    /**
     * 向分桶累加金额列表。
     */
    private void accumulateAmount(
        Map<String, List<BigDecimal>> bucket,
        String key,
        BigDecimal amount) {
        if (amount == null) {
            return;
        }
        bucket.computeIfAbsent(key, ignored -> new ArrayList<>()).add(amount);
    }

    /**
     * 6 位加总后结算；无有效行时 null。
     *
     * @param amounts 行金额
     * @return 结算刻度或 null
     */
    private BigDecimal sumThenSettleOrNull(List<BigDecimal> amounts) {
        if (amounts == null || amounts.isEmpty()) {
            return null;
        }
        return MonetaryAmountSupport.sumThenSettle(amounts);
    }

    /**
     * 发药报表明细行进价/售价 enrich 契约。
     */
    public interface MedicationDispensePurchaseSaleDetailLine {

        /** @return 来源 code */
        String getSourceCode();

        /** @return 发放主键 */
        Long getDispenseId();

        /** @return 药品定义主键 */
        Long getMedicationId();

        /** @return 药房主键 */
        Long getPharmacyLocationId();

        /** @return 科室组织主键 */
        Long getDepartmentId();

        /** @return 售价别名单价 */
        BigDecimal getUnitPrice();

        /** @return 售价别名总额 */
        BigDecimal getTotalAmount();

        /** @return 进价单价 */
        BigDecimal getPurchaseUnitPrice();

        /** @return 进价总额 */
        BigDecimal getPurchaseAmount();

        /** @return 售价单价 */
        BigDecimal getSaleUnitPrice();

        /** @return 售价总额 */
        BigDecimal getSaleAmount();

        /** @param unitPrice 售价别名单价 */
        void setUnitPrice(BigDecimal unitPrice);

        /** @param totalAmount 售价别名总额 */
        void setTotalAmount(BigDecimal totalAmount);

        /** @param purchaseUnitPrice 进价单价 */
        void setPurchaseUnitPrice(BigDecimal purchaseUnitPrice);

        /** @param purchaseAmount 进价总额 */
        void setPurchaseAmount(BigDecimal purchaseAmount);

        /** @param saleUnitPrice 售价单价 */
        void setSaleUnitPrice(BigDecimal saleUnitPrice);

        /** @param saleAmount 售价总额 */
        void setSaleAmount(BigDecimal saleAmount);
    }

    /**
     * 发药报表汇总行进价/售价 enrich 契约。
     */
    public interface MedicationDispensePurchaseSaleSummaryLine {

        /** @return 维度键 */
        String getDimensionKey();

        /** @return 进价净额 */
        BigDecimal getPurchaseNetAmount();

        /** @return 售价净额 */
        BigDecimal getSaleNetAmount();

        /** @return 售价净额别名 */
        BigDecimal getNetAmount();

        /** @param purchaseNetAmount 进价净额 */
        void setPurchaseNetAmount(BigDecimal purchaseNetAmount);

        /** @param saleNetAmount 售价净额 */
        void setSaleNetAmount(BigDecimal saleNetAmount);

        /** @param netAmount 售价净额别名 */
        void setNetAmount(BigDecimal netAmount);
    }
}
