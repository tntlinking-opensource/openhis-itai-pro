/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.openhis.health.application.shared.pricing.HistoricalChargeItemPurchasePriceSupport;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.share.constants.ConsumableUsageReportConstants;
import com.openhis.health.domain.share.constants.DeptIssueReportConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;

import lombok.RequiredArgsConstructor;

/**
 * 耗材使用报表患者侧金额 enrich（BR-RPT-OCU-004 / BR-RPT-ICU-004；金额刻度 BR-MON-001）。
 *
 * <p>患者侧：售价来自 ChargeItem.total_price；进价来自 {@link HistoricalChargeItemPurchasePriceSupport}。
 * 科室领用侧：进价来自 Supply 落库价；售价来自历史 SALE 单价 × 数量（退领带符号）。</p>
 */
@Component
@RequiredArgsConstructor
public class ConsumableUsageReportAmountEnricher {

    /** 收费明细读侧。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 历史进价/售价读侧。 */
    private final HistoricalChargeItemPurchasePriceSupport historicalChargeItemPurchasePriceSupport;

    /**
     * 回填患者发放明细行进价/售价（6 位行刻度；amount 保持售价别名）。
     *
     * @param lines 待 enrich 行；须已填 encounterId / dispenseId / deviceReqId / quantity
     */
    public void enrichPatientDispenseAmounts(List<PatientDispenseAmountLine> lines) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<ChargeItem> chargeItems = this.loadChargeItems(lines);
        // Pre：单价一次解析，行额由单价推导；禁止紧接着再调 computePurchaseAmountByChargeItemId（双倍 IO）
        Map<Long, BigDecimal> purchaseUnitByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseUnitPriceByChargeItemId(chargeItems);
        Map<Long, BigDecimal> purchaseByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseAmountFromUnitPrices(
                chargeItems, purchaseUnitByChargeItemId);
        for (PatientDispenseAmountLine line : lines) {
            if (line == null) {
                continue;
            }
            BigDecimal saleAmount = MonetaryAmountSupport.scaleCalc(this.sumMatchedSaleAmount(line, chargeItems));
            line.setAmount(saleAmount);
            line.setSaleAmount(saleAmount);
            BigDecimal saleUnit = this.resolveSaleUnitPrice(line, saleAmount);
            line.setSaleUnitPrice(saleUnit);
            BigDecimal purchaseAmount = MonetaryAmountSupport.scaleCalc(
                this.sumMatchedPurchaseAmount(line, chargeItems, purchaseByChargeItemId));
            line.setPurchaseAmount(purchaseAmount);
            BigDecimal purchaseUnit = this.resolvePurchaseUnitPrice(
                line, chargeItems, purchaseUnitByChargeItemId, purchaseAmount);
            line.setPurchaseUnitPrice(purchaseUnit);
        }
    }

    /**
     * 回填科室领用明细行进价/售价（amount 保持售价别名）。
     *
     * @param line 已映射明细行；须含 deviceDefId / quantity / 进价字段
     */
    public void enrichDeptIssueAmounts(PatientDispenseAmountLine line) {
        if (line == null) {
            return;
        }
        BigDecimal purchaseAmount = MonetaryAmountSupport.scaleCalc(line.getPurchaseAmount());
        BigDecimal purchaseUnit = MonetaryAmountSupport.scaleCalc(line.getPurchaseUnitPrice());
        line.setPurchaseAmount(purchaseAmount);
        line.setPurchaseUnitPrice(purchaseUnit);
        BigDecimal saleUnit = this.historicalChargeItemPurchasePriceSupport.resolveHistoricalSaleUnitPrice(
            TableNameConstants.ADM_DEVICE_DEFINITION, line.getDeviceDefId());
        BigDecimal saleAmount = this.lineTotalOrNull(saleUnit, line.getQuantity());
        line.setSaleUnitPrice(saleUnit);
        line.setSaleAmount(saleAmount);
        line.setAmount(saleAmount);
    }

    /**
     * 按汇总 dimensionKey 累加明细行进价/售价净额。
     *
     * @param summaryRows 汇总行
     * @param detailRows  已 enrich 明细行
     * @param dimension   汇总维
     */
    public void aggregateSummaryPurchaseSaleAmounts(
        List<? extends ConsumableUsagePurchaseSaleSummaryLine> summaryRows,
        List<? extends ConsumableUsagePurchaseSaleDetailLine> detailRows,
        String dimension) {
        if (summaryRows == null || summaryRows.isEmpty() || detailRows == null || detailRows.isEmpty()) {
            return;
        }
        Map<String, ConsumableUsagePurchaseSaleSummaryLine> summaryByKey = new LinkedHashMap<>();
        for (ConsumableUsagePurchaseSaleSummaryLine summaryRow : summaryRows) {
            if (summaryRow != null && summaryRow.getDimensionKey() != null) {
                summaryByKey.put(summaryRow.getDimensionKey(), summaryRow);
            }
        }
        Map<String, List<BigDecimal>> purchaseAmountsByKey = new HashMap<>();
        Map<String, List<BigDecimal>> saleAmountsByKey = new HashMap<>();
        for (ConsumableUsagePurchaseSaleDetailLine detailRow : detailRows) {
            if (detailRow == null) {
                continue;
            }
            String bucketKey = this.resolveDetailDimensionKey(detailRow, dimension);
            if (bucketKey == null) {
                continue;
            }
            this.accumulateAmount(purchaseAmountsByKey, bucketKey, detailRow.getPurchaseAmount());
            this.accumulateAmount(saleAmountsByKey, bucketKey, detailRow.getSaleAmount());
        }
        for (Map.Entry<String, ConsumableUsagePurchaseSaleSummaryLine> entry : summaryByKey.entrySet()) {
            String key = entry.getKey();
            ConsumableUsagePurchaseSaleSummaryLine summary = entry.getValue();
            BigDecimal purchaseNet = this.sumThenSettleOrNull(purchaseAmountsByKey.get(key));
            BigDecimal saleNet = this.sumThenSettleOrNull(saleAmountsByKey.get(key));
            summary.setPurchaseNetAmount(purchaseNet);
            summary.setSaleNetAmount(saleNet);
            summary.setNetAmount(saleNet);
        }
    }

    /**
     * 按患者汇总维累加金额（先 6 位加总再结算 2 位）。
     *
     * @param amounts 行金额列表
     * @return 结算刻度合计
     */
    public BigDecimal sumThenSettle(List<BigDecimal> amounts) {
        return MonetaryAmountSupport.sumThenSettle(amounts);
    }

    /**
     * 汇总页脚进价净额（仅累加非空行）。
     *
     * @param rows 汇总行
     * @return 结算刻度或 null
     */
    public BigDecimal sumPurchaseNetAmount(List<? extends ConsumableUsagePurchaseSaleSummaryLine> rows) {
        return this.sumSummaryAmountColumn(rows, true);
    }

    /**
     * 汇总页脚售价净额（仅累加非空行；netAmount 别名）。
     *
     * @param rows 汇总行
     * @return 结算刻度或 null
     */
    public BigDecimal sumSaleNetAmount(List<? extends ConsumableUsagePurchaseSaleSummaryLine> rows) {
        return this.sumSummaryAmountColumn(rows, false);
    }

    /**
     * 批量加载与患者发放行相关的 ChargeItem。
     *
     * @param lines 明细行
     * @return 去重收费明细
     */
    private List<ChargeItem> loadChargeItems(List<PatientDispenseAmountLine> lines) {
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> serviceIds = new LinkedHashSet<>();
        for (PatientDispenseAmountLine line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getEncounterId() != null) {
                encounterIds.add(line.getEncounterId());
            }
            if (line.getDispenseId() != null) {
                serviceIds.add(line.getDispenseId());
            }
            if (line.getDeviceReqId() != null) {
                serviceIds.add(line.getDeviceReqId());
            }
        }
        Map<Long, ChargeItem> chargeItemById = new HashMap<>();
        this.mergeChargeItems(chargeItemById, this.listByEncounterIds(encounterIds));
        ChargeItemQueryCommand serviceCmd = new ChargeItemQueryCommand();
        serviceCmd.setServiceIds(serviceIds);
        serviceCmd.setExcludeSupersededSettledLines(true);
        this.mergeChargeItems(chargeItemById, this.chargeItemQueryService.listChargeItemsByServiceIds(serviceCmd));
        return new ArrayList<>(this.chargeItemQueryService.filterOutSupersededSettledLinesGroupedByEncounter(
            new ArrayList<>(chargeItemById.values())));
    }

    /**
     * 按就诊集合加载收费明细。
     */
    private List<ChargeItem> listByEncounterIds(Set<Long> encounterIds) {
        if (encounterIds.isEmpty()) {
            return List.of();
        }
        ChargeItemQueryCommand cmd = new ChargeItemQueryCommand();
        cmd.setEncounterIds(encounterIds);
        cmd.setExcludeSupersededSettledLines(true);
        return this.chargeItemQueryService.listChargeItemsByEncounterIds(cmd);
    }

    /**
     * 合并收费明细到 id Map。
     */
    private void mergeChargeItems(Map<Long, ChargeItem> chargeItemById, List<ChargeItem> chargeItems) {
        if (chargeItems == null) {
            return;
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null || Boolean.TRUE.equals(chargeItem.getIsDeleted())) {
                continue;
            }
            chargeItemById.put(chargeItem.getId(), chargeItem);
        }
    }

    /**
     * 汇总与发放行匹配的 ChargeItem 售价金额。
     */
    private BigDecimal sumMatchedSaleAmount(PatientDispenseAmountLine line, List<ChargeItem> chargeItems) {
        Set<Long> usedIds = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ChargeItem chargeItem : chargeItems) {
            if (!this.matches(line, chargeItem)) {
                continue;
            }
            if (!usedIds.add(chargeItem.getId())) {
                continue;
            }
            total = total.add(this.salePriceOf(chargeItem));
        }
        return total;
    }

    /**
     * 汇总与发放行匹配的 ChargeItem 进价金额。
     */
    private BigDecimal sumMatchedPurchaseAmount(
        PatientDispenseAmountLine line,
        List<ChargeItem> chargeItems,
        Map<Long, BigDecimal> purchaseByChargeItemId) {
        Set<Long> usedIds = new HashSet<>();
        BigDecimal total = BigDecimal.ZERO;
        for (ChargeItem chargeItem : chargeItems) {
            if (!this.matches(line, chargeItem)) {
                continue;
            }
            if (!usedIds.add(chargeItem.getId())) {
                continue;
            }
            BigDecimal purchase = purchaseByChargeItemId.get(chargeItem.getId());
            if (purchase != null) {
                total = total.add(purchase);
            }
        }
        return total;
    }

    /**
     * 解析患者侧售价单价（行额/数量；无法除时 null）。
     */
    private BigDecimal resolveSaleUnitPrice(PatientDispenseAmountLine line, BigDecimal saleAmount) {
        if (saleAmount == null || line.getQuantity() == null) {
            return null;
        }
        if (line.getQuantity().compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return MonetaryAmountSupport.unitPriceFromLineTotal(saleAmount, line.getQuantity());
    }

    /**
     * 解析患者侧进价单价。
     */
    private BigDecimal resolvePurchaseUnitPrice(
        PatientDispenseAmountLine line,
        List<ChargeItem> chargeItems,
        Map<Long, BigDecimal> purchaseUnitByChargeItemId,
        BigDecimal purchaseAmount) {
        if (purchaseAmount != null && line.getQuantity() != null && line.getQuantity().compareTo(BigDecimal.ZERO) != 0) {
            return MonetaryAmountSupport.unitPriceFromLineTotal(purchaseAmount, line.getQuantity());
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (!this.matches(line, chargeItem) || chargeItem.getId() == null) {
                continue;
            }
            BigDecimal unit = purchaseUnitByChargeItemId.get(chargeItem.getId());
            if (unit != null) {
                return unit;
            }
        }
        return null;
    }

    /**
     * ChargeItem 是否锚定本发放行。
     */
    private boolean matches(PatientDispenseAmountLine line, ChargeItem chargeItem) {
        if (line.getDispenseId() != null) {
            if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getServiceTable())
                && line.getDispenseId().equals(chargeItem.getServiceId())) {
                return true;
            }
            if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getDispenseTable())
                && line.getDispenseId().equals(chargeItem.getDispenseId())) {
                return true;
            }
        }
        if (line.getDeviceReqId() != null
            && TableNameConstants.WKF_DEVICE_REQUEST.equals(chargeItem.getServiceTable())
            && line.getDeviceReqId().equals(chargeItem.getServiceId())) {
            return true;
        }
        return false;
    }

    /**
     * 取收费明细行售价金额。
     */
    private BigDecimal salePriceOf(ChargeItem chargeItem) {
        if (chargeItem.getTotalPrice() == null) {
            return BigDecimal.ZERO;
        }
        return MonetaryAmountSupport.nullToZero(chargeItem.getTotalPrice());
    }

    /**
     * 明细行 → 汇总 dimensionKey。
     */
    private String resolveDetailDimensionKey(ConsumableUsagePurchaseSaleDetailLine detailRow, String dimension) {
        if (ConsumableUsageReportConstants.SOURCE_CODE_DEPT_ISSUE.equals(detailRow.getSourceCode())) {
            return this.resolveDeptDetailDimensionKey(detailRow, dimension);
        }
        return this.resolvePatientDetailDimensionKey(detailRow, dimension);
    }

    /**
     * 患者侧明细 → dimensionKey。
     */
    private String resolvePatientDetailDimensionKey(
        ConsumableUsagePurchaseSaleDetailLine detailRow,
        String dimension) {
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_DEPARTMENT.equals(dimension)) {
            Long orgId = detailRow.getOrganizationId();
            if (orgId == null) {
                return "DEPARTMENT:";
            }
            return "DEPARTMENT:" + orgId;
        }
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_LOCATION.equals(dimension)) {
            Long locationId = detailRow.getLocationId();
            if (locationId == null) {
                return "LOCATION:";
            }
            return "LOCATION:" + locationId;
        }
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)) {
            Long deviceDefId = detailRow.getDeviceDefId();
            if (deviceDefId == null) {
                return "ITEM:";
            }
            return "ITEM:" + deviceDefId;
        }
        Long patientId = detailRow.getPatientId();
        if (patientId == null) {
            return "PATIENT:";
        }
        return "PATIENT:" + patientId;
    }

    /**
     * 科室侧明细 → dimensionKey。
     */
    private String resolveDeptDetailDimensionKey(
        ConsumableUsagePurchaseSaleDetailLine detailRow,
        String dimension) {
        if (DeptIssueReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)) {
            Long deviceDefId = detailRow.getDeviceDefId();
            if (deviceDefId == null) {
                return "ITEM:";
            }
            return "ITEM:" + deviceDefId;
        }
        if (DeptIssueReportConstants.SUMMARY_DIMENSION_PHARMACY.equals(dimension)) {
            Long locationId = detailRow.getLocationId();
            if (locationId == null) {
                return "PHARMACY:";
            }
            return "PHARMACY:" + locationId;
        }
        Long orgId = detailRow.getOrganizationId();
        if (orgId == null) {
            return "DEPARTMENT:";
        }
        return "DEPARTMENT:" + orgId;
    }

    /**
     * 单价 × 数量行额。
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
    private void accumulateAmount(Map<String, List<BigDecimal>> bucket, String key, BigDecimal amount) {
        if (amount == null) {
            return;
        }
        bucket.computeIfAbsent(key, ignored -> new ArrayList<>()).add(amount);
    }

    /**
     * 6 位加总后结算；无有效行时 null。
     */
    private BigDecimal sumThenSettleOrNull(List<BigDecimal> amounts) {
        if (amounts == null || amounts.isEmpty()) {
            return null;
        }
        return MonetaryAmountSupport.sumThenSettle(amounts);
    }

    /**
     * 汇总列加总（purchase 或 sale）。
     */
    private BigDecimal sumSummaryAmountColumn(
        List<? extends ConsumableUsagePurchaseSaleSummaryLine> rows,
        boolean purchaseColumn) {
        List<BigDecimal> amounts = new ArrayList<>();
        for (ConsumableUsagePurchaseSaleSummaryLine row : rows) {
            if (row == null) {
                continue;
            }
            BigDecimal value;
            if (purchaseColumn) {
                value = row.getPurchaseNetAmount();
            } else {
                value = row.getSaleNetAmount();
                if (value == null) {
                    value = row.getNetAmount();
                }
            }
            if (value != null) {
                amounts.add(value);
            }
        }
        if (amounts.isEmpty()) {
            return null;
        }
        return MonetaryAmountSupport.sumThenSettle(amounts);
    }

    /**
     * 患者发放金额 enrich 行契约（Support 与 Feature DTO 之间的适配面）。
     */
    public interface PatientDispenseAmountLine extends ConsumableUsagePurchaseSaleDetailLine {

        /** @return 就诊主键 */
        Long getEncounterId();

        /** @return 发放主键 */
        Long getDispenseId();

        /** @return 耗材请求主键；可空 */
        Long getDeviceReqId();

        /** 写入匹配后的行售价别名 amount。 */
        void setAmount(BigDecimal amount);
    }

    /**
     * 科室领用金额 enrich 行契约（与患者侧共用明细 DTO）。
     */
    public interface DeptIssueAmountLine extends PatientDispenseAmountLine {
    }

    /**
     * 耗材报表明细行进价/售价 enrich 契约。
     */
    public interface ConsumableUsagePurchaseSaleDetailLine {

        /** @return 来源 code */
        String getSourceCode();

        /** @return 患者主键 */
        Long getPatientId();

        /** @return 科室组织主键 */
        Long getOrganizationId();

        /** @return 库位主键 */
        Long getLocationId();

        /** @return 耗材定义主键 */
        Long getDeviceDefId();

        /** @return 数量 */
        BigDecimal getQuantity();

        /** @return 售价别名 amount */
        BigDecimal getAmount();

        /** @return 进价单价 */
        BigDecimal getPurchaseUnitPrice();

        /** @return 进价总额 */
        BigDecimal getPurchaseAmount();

        /** @return 售价单价 */
        BigDecimal getSaleUnitPrice();

        /** @return 售价总额 */
        BigDecimal getSaleAmount();

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
     * 耗材报表汇总行进价/售价 enrich 契约。
     */
    public interface ConsumableUsagePurchaseSaleSummaryLine {

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
