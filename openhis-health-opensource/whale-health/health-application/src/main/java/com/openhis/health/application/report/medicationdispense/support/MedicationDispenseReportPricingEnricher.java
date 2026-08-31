/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.medicationdispense.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.openhis.health.application.shared.pricing.ChargeItemPackageUnitRatioSupport;
import com.openhis.health.application.shared.pricing.HistoricalChargeItemPurchasePriceSupport;
import com.openhis.health.application.shared.product.ChargeItemProductLookupHelper;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionLotBatchKey;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.share.constants.DeptIssueReportConstants;
import com.openhis.health.domain.share.constants.MedicationDispenseReportConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.DeptIssueReportLineQueryModel;
import com.openhis.whale.core.application.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 发药报表进价/售价 enrich（BR-PRICE-SRC-001；金额刻度 BR-MON-001；操作单位分支 #4M-OP）。
 *
 * <p>患者侧：售价优先 ChargeItem；进价来自 {@link HistoricalChargeItemPurchasePriceSupport}
 *（批号 {@code LOT_NUMBER_COST}，缺则同批号 {@code LOT_NUMBER_PRICE}）。
 * 账单缺失时：进价按发放批号回落；住院发药报表另允许批号 {@code LOT_NUMBER_PRICE} 回落售价
 *（正/退同行，与药房日结对齐，避免取消执行删账后汇总只计退药负额）；禁止目录 SALE。
 * 行进价合计委托 {@link ChargeItemPackageUnitRatioSupport#computePurchaseLineAmount}。
 * 科室领用侧：仅 Supply 落库进价；售价字段一律置空。</p>
 *
 * <p>DOC-CONFLICT→已按 DD-报表中心-进售价口径与开立科室-2026-08 收敛。</p>
 */
@Component
@RequiredArgsConstructor
public class MedicationDispenseReportPricingEnricher {

    /** 历史进价读侧（禁目录参考价）。 */
    private final HistoricalChargeItemPurchasePriceSupport historicalChargeItemPurchasePriceSupport;

    /** 收费明细读侧（患者侧锚点匹配）。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 包装倍数与拆零展示单价。 */
    private final ChargeItemPackageUnitRatioSupport chargeItemPackageUnitRatioSupport;

    /** 产品展示（取 packageUnitRatio）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 批量回填患者侧明细行进价/售价（保持 unitPrice/totalAmount 为售价别名）。
     *
     * <p>门诊发药报表：账单缺失时仅可回落批号<strong>进价</strong>；售价仍只认账单。</p>
     *
     * @param lines    已装配明细行；须含 dispenseId / encounterId / quantity / unitPrice / totalAmount
     * @param slipRows 同批发放事实（按 dispenseId 一次批量加载 ChargeItem，禁止按就诊 N+1）
     */
    public void enrichPatientDetailPurchaseSale(
        List<? extends MedicationDispensePurchaseSaleDetailLine> lines,
        List<MedicationDispenseSlipQueryModel> slipRows) {
        this.enrichPatientDetailPurchaseSale(lines, slipRows, null, false);
    }

    /**
     * 批量回填患者侧明细行进价/售价；住院可对账单缺失行回落批号售价（与药房日结同口径）。
     *
     * @param lines                 已装配明细行
     * @param slipRows              同批发放事实
     * @param productByMedicationId 品规→展示（包装比）；可空则批号回落跳过行额分支
     * @param allowInpatientLotSaleWhenChargeMissing 为 true 时住院账单缺失允许 {@code LOT_NUMBER_PRICE}（含正向）
     */
    public void enrichPatientDetailPurchaseSale(
        List<? extends MedicationDispensePurchaseSaleDetailLine> lines,
        List<MedicationDispenseSlipQueryModel> slipRows,
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId,
        boolean allowInpatientLotSaleWhenChargeMissing) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> purchaseTotalByDispenseId = new HashMap<>();
        Map<Long, BigDecimal> purchaseUnitByDispenseId = new HashMap<>();
        this.mergePatientPurchasePricingByDispenseIds(slipRows, purchaseUnitByDispenseId, purchaseTotalByDispenseId);
        this.applyPurchaseSaleAliases(lines, purchaseUnitByDispenseId, purchaseTotalByDispenseId);
        // Asm：账单缺失批号进价回落；住院另可回落批号售价（正/退均落，避免取消执行删账后汇总只剩退药负额）
        this.supplementLotPricingWhenChargeMissing(
            lines, slipRows, productByMedicationId, allowInpatientLotSaleWhenChargeMissing);
    }

    /**
     * 在调用方已批量加载的 ChargeItem 上写回进价/售价（汇总路径与售价同批复用，禁止二次打库）。
     *
     * @param lines      已装配明细行；须含 dispenseId / quantity / unitPrice / totalAmount
     * @param chargeRows 同批发放锚点收费明细
     */
    public void enrichPatientDetailPurchaseSaleFromChargeItems(
        List<? extends MedicationDispensePurchaseSaleDetailLine> lines,
        List<ChargeItem> chargeRows) {
        this.enrichPatientDetailPurchaseSaleFromChargeItems(lines, chargeRows, null, null, false);
    }

    /**
     * 汇总路径：复用已加载 ChargeItem；住院可对账单缺失行补批号进/售价。
     *
     * @param lines                 明细行
     * @param chargeRows            已加载收费行
     * @param slipRows              发放事实（批号回落用）；可空则不补
     * @param productByMedicationId 品规展示；可空
     * @param allowInpatientLotSaleWhenChargeMissing 是否允许批号售价（含正向）
     */
    public void enrichPatientDetailPurchaseSaleFromChargeItems(
        List<? extends MedicationDispensePurchaseSaleDetailLine> lines,
        List<ChargeItem> chargeRows,
        List<MedicationDispenseSlipQueryModel> slipRows,
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId,
        boolean allowInpatientLotSaleWhenChargeMissing) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> purchaseTotalByDispenseId = new HashMap<>();
        Map<Long, BigDecimal> purchaseUnitByDispenseId = new HashMap<>();
        this.mergePurchasePricingFromChargeItems(
            chargeRows, purchaseUnitByDispenseId, purchaseTotalByDispenseId);
        this.applyPurchaseSaleAliases(lines, purchaseUnitByDispenseId, purchaseTotalByDispenseId);
        // Pre：仅账单仍缺进价（住院另含缺售价）的行才开批号会话，避免全窗重复解析
        List<MedicationDispensePurchaseSaleDetailLine> needingLot = new ArrayList<>();
        for (MedicationDispensePurchaseSaleDetailLine line : lines) {
            if (line == null) {
                continue;
            }
            if (this.needsLotPricingFallback(line, allowInpatientLotSaleWhenChargeMissing)) {
                needingLot.add(line);
            }
        }
        if (needingLot.isEmpty()) {
            return;
        }
        Map<Long, ProductDefinitionDisplayDto> products = productByMedicationId;
        if (products == null || products.isEmpty()) {
            products = this.loadProductsForLotFallback(needingLot, slipRows);
        }
        this.supplementLotPricingWhenChargeMissing(
            needingLot, slipRows, products, allowInpatientLotSaleWhenChargeMissing);
    }

    /**
     * 判断汇总/明细行是否仍需批号进价（住院另含批号售价）回落。
     *
     * @param line                                   已写 ChargeItem 别名后的行
     * @param allowInpatientLotSaleWhenChargeMissing 住院是否允许批号售价
     * @return true 需要批号回落
     */
    private boolean needsLotPricingFallback(
        MedicationDispensePurchaseSaleDetailLine line,
        boolean allowInpatientLotSaleWhenChargeMissing) {
        boolean needPurchase =
            line.getPurchaseUnitPrice() == null && line.getPurchaseAmount() == null;
        if (needPurchase) {
            return true;
        }
        // 住院：账单缺失售价时允许批号 LOT_NUMBER_PRICE
        if (allowInpatientLotSaleWhenChargeMissing
            && line.getSaleUnitPrice() == null
            && line.getSaleAmount() == null) {
            return true;
        }
        return false;
    }

    /**
     * 仅为待批号回落行装载品规展示（包装比）；去重 medicationId。
     *
     * @param needingLot 待回落行
     * @param slipRows   发放事实
     * @return medicationId → 品规展示
     */
    private Map<Long, ProductDefinitionDisplayDto> loadProductsForLotFallback(
        List<? extends MedicationDispensePurchaseSaleDetailLine> needingLot,
        List<MedicationDispenseSlipQueryModel> slipRows) {
        Map<Long, MedicationDispenseSlipQueryModel> slipByDispenseId = new HashMap<>();
        if (slipRows != null) {
            for (MedicationDispenseSlipQueryModel slip : slipRows) {
                if (slip != null && slip.getDispenseId() != null) {
                    slipByDispenseId.put(slip.getDispenseId(), slip);
                }
            }
        }
        LinkedHashSet<Long> medicationIds = new LinkedHashSet<>();
        for (MedicationDispensePurchaseSaleDetailLine line : needingLot) {
            if (line == null || line.getDispenseId() == null) {
                continue;
            }
            MedicationDispenseSlipQueryModel slip = slipByDispenseId.get(line.getDispenseId());
            if (slip != null && slip.getMedicationId() != null) {
                medicationIds.add(slip.getMedicationId());
            } else if (line.getMedicationId() != null) {
                medicationIds.add(line.getMedicationId());
            }
        }
        if (medicationIds.isEmpty()) {
            return Map.of();
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>(medicationIds.size());
        for (Long medicationId : medicationIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(medicationId);
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> productByKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        Map<Long, ProductDefinitionDisplayDto> productById = new HashMap<>();
        for (Long medicationId : medicationIds) {
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, medicationId);
            ProductDefinitionDisplayDto product = productByKey.get(key);
            if (product != null) {
                productById.put(medicationId, product);
            }
        }
        return productById;
    }

    /**
     * 回填科室领用明细行进价（退领数量/金额已为负）；售价一律置空。
     *
     * <p>进价取 Supply 落库价；展示单价与操作单位对齐（#4M-OP）。领用非销售，禁止编售价 / 目录 SALE。</p>
     *
     * @param line                明细行
     * @param deptLine            领用领域行
     * @param signedQuantity      带符号数量（本方法不用于算售价）
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
        BigDecimal packagePurchaseUnit = MonetaryAmountSupport.scaleCalc(deptLine.getPrice());
        ProductDefinitionDisplayDto product = this.resolveMedicationProductDisplay(deptLine.getItemId());
        BigDecimal ratio = this.resolvePackageUnitRatio(product);
        BigDecimal purchaseUnit = this.chargeItemPackageUnitRatioSupport.toAlignedPurchaseDisplayUnitPrice(
            packagePurchaseUnit, ratio, deptLine.getUnitCode(), product);
        BigDecimal purchaseAmount = MonetaryAmountSupport.scaleCalc(signedPurchaseTotal);
        line.setPurchaseUnitPrice(purchaseUnit);
        line.setPurchaseAmount(purchaseAmount);
        // 领用侧不算售价：别名与售价列一律空
        line.setSaleUnitPrice(null);
        line.setSaleAmount(null);
        line.setUnitPrice(null);
        line.setTotalAmount(null);
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
        if (summaryRows == null || summaryRows.isEmpty()) {
            return;
        }
        SummaryAmountBuckets buckets = new SummaryAmountBuckets();
        this.accumulateDetailRowsIntoSummaryBuckets(
            buckets,
            detailRows,
            dimension,
            categoryCodeByMedicationId,
            organizationIdByPharmacyLocationId);
        this.applySummaryAmountBuckets(summaryRows, buckets);
    }

    /**
     * 将一批已定价明细累加进汇总金额桶（可多次调用后一次 {@link #applySummaryAmountBuckets}）。
     *
     * @param buckets               金额/展示单价累加桶
     * @param detailRows            本批已 enrich 明细
     * @param dimension             汇总维度码
     * @param categoryCodeByMedicationId 品规→类型
     * @param organizationIdByPharmacyLocationId 药房→科室
     */
    public void accumulateDetailRowsIntoSummaryBuckets(
        SummaryAmountBuckets buckets,
        List<? extends MedicationDispensePurchaseSaleDetailLine> detailRows,
        String dimension,
        Map<Long, String> categoryCodeByMedicationId,
        Map<Long, Long> organizationIdByPharmacyLocationId) {
        if (buckets == null || detailRows == null || detailRows.isEmpty()) {
            return;
        }
        Map<Long, String> categoryMap = categoryCodeByMedicationId;
        if (categoryMap == null) {
            categoryMap = Map.of();
        }
        Map<Long, Long> orgByLocation = organizationIdByPharmacyLocationId;
        if (orgByLocation == null) {
            orgByLocation = Map.of();
        }
        for (MedicationDispensePurchaseSaleDetailLine detailRow : detailRows) {
            if (detailRow == null) {
                continue;
            }
            String bucketKey = this.resolveDetailDimensionKey(
                detailRow, dimension, categoryMap, orgByLocation);
            if (bucketKey == null) {
                continue;
            }
            this.accumulateAmount(buckets.purchaseAmountsByKey, bucketKey, detailRow.getPurchaseAmount());
            this.accumulateAmount(buckets.saleAmountsByKey, bucketKey, detailRow.getSaleAmount());
            this.collectSummaryDisplayUnitPrice(
                buckets.purchaseUnitsByKey, bucketKey, detailRow.getPurchaseUnitPrice());
            this.collectSummaryDisplayUnitPrice(
                buckets.saleUnitsByKey, bucketKey, detailRow.getSaleUnitPrice());
        }
    }

    /**
     * 汇总专用：按发放事实直累加进/售价桶，禁止物化全窗明细 DTO。
     *
     * @param buckets                          金额桶
     * @param slipRows                         同窗发放事实
     * @param chargeRows                       已加载 ChargeItem（与售价同批）
     * @param dimension                        汇总维
     * @param categoryCodeByMedicationId       CATEGORY 维品规→类型
     * @param organizationIdByPharmacyLocationId 领用科室维回落；患者侧可空
     * @param saleUnitByDispenseId             已合并售价单价
     * @param saleTotalByDispenseId            已合并售价总额（退药补价后为正，本方法按退药取负）
     * @param allowInpatientLotSaleWhenChargeMissing 住院批号售价回落
     */
    public void accumulatePatientSlipsIntoSummaryBuckets(
        SummaryAmountBuckets buckets,
        List<MedicationDispenseSlipQueryModel> slipRows,
        List<ChargeItem> chargeRows,
        String dimension,
        Map<Long, String> categoryCodeByMedicationId,
        Map<Long, Long> organizationIdByPharmacyLocationId,
        Map<Long, BigDecimal> saleUnitByDispenseId,
        Map<Long, BigDecimal> saleTotalByDispenseId,
        boolean allowInpatientLotSaleWhenChargeMissing) {
        if (buckets == null || slipRows == null || slipRows.isEmpty()) {
            return;
        }
        Map<Long, String> categoryMap = categoryCodeByMedicationId;
        if (categoryMap == null) {
            categoryMap = Map.of();
        }
        Map<Long, BigDecimal> purchaseUnitByDispenseId = new HashMap<>();
        Map<Long, BigDecimal> purchaseTotalByDispenseId = new HashMap<>();
        if (chargeRows != null && !chargeRows.isEmpty()) {
            this.mergePurchasePricingFromChargeItems(
                chargeRows, purchaseUnitByDispenseId, purchaseTotalByDispenseId);
        }
        Map<Long, BigDecimal> saleUnitMap = saleUnitByDispenseId;
        if (saleUnitMap == null) {
            saleUnitMap = Map.of();
        }
        Map<Long, BigDecimal> saleTotalMap = saleTotalByDispenseId;
        if (saleTotalMap == null) {
            saleTotalMap = Map.of();
        }
        List<PatientSlipPricingState> needingLot = new ArrayList<>();
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            if (slip == null || slip.getDispenseId() == null) {
                continue;
            }
            Long dispenseId = slip.getDispenseId();
            boolean refundRow = this.isRefundSlipRow(slip);
            BigDecimal saleUnit = saleUnitMap.get(dispenseId);
            BigDecimal saleTotal = saleTotalMap.get(dispenseId);
            if (saleUnit != null) {
                saleUnit = MonetaryAmountSupport.scaleCalc(saleUnit);
            }
            if (saleTotal != null) {
                saleTotal = MonetaryAmountSupport.scaleCalc(saleTotal);
                if (refundRow) {
                    saleTotal = saleTotal.negate();
                }
            }
            BigDecimal purchaseUnit = purchaseUnitByDispenseId.get(dispenseId);
            BigDecimal purchaseTotal = purchaseTotalByDispenseId.get(dispenseId);
            if (purchaseTotal != null
                && saleTotal != null
                && saleTotal.signum() < 0
                && purchaseTotal.signum() > 0) {
                purchaseTotal = purchaseTotal.negate();
            }
            if (this.slipNeedsLotPricingFallback(
                purchaseUnit, purchaseTotal, saleUnit, saleTotal, allowInpatientLotSaleWhenChargeMissing)) {
                needingLot.add(new PatientSlipPricingState(
                    slip, purchaseUnit, purchaseTotal, saleUnit, saleTotal));
                continue;
            }
            String bucketKey = this.resolvePatientSlipDimensionKey(slip, dimension, categoryMap);
            if (bucketKey == null) {
                continue;
            }
            this.accumulateSlipPricingIntoBuckets(
                buckets, bucketKey, purchaseUnit, purchaseTotal, saleUnit, saleTotal);
        }
        if (!needingLot.isEmpty()) {
            this.supplementLotPricingIntoSummaryBucketsForSlips(
                buckets,
                needingLot,
                dimension,
                categoryMap,
                allowInpatientLotSaleWhenChargeMissing);
        }
    }

    /**
     * 汇总专用：科室领用事实直累加进价桶（批量品规展示，禁止逐行打库）。
     *
     * @param buckets                          金额桶
     * @param lines                            领用事实行
     * @param dimension                        汇总维
     * @param organizationIdByPharmacyLocationId 药房→科室回落
     */
    public void accumulateDeptLinesIntoSummaryBuckets(
        SummaryAmountBuckets buckets,
        List<DeptIssueReportLineQueryModel> lines,
        String dimension,
        Map<Long, Long> organizationIdByPharmacyLocationId) {
        if (buckets == null || lines == null || lines.isEmpty()) {
            return;
        }
        Map<Long, Long> orgByLocation = organizationIdByPharmacyLocationId;
        if (orgByLocation == null) {
            orgByLocation = Map.of();
        }
        LinkedHashSet<Long> itemIds = new LinkedHashSet<>();
        for (DeptIssueReportLineQueryModel line : lines) {
            if (line != null && line.getItemId() != null) {
                itemIds.add(line.getItemId());
            }
        }
        Map<Long, ProductDefinitionDisplayDto> productByItemId =
            this.loadProductByMedicationIds(itemIds);
        for (DeptIssueReportLineQueryModel line : lines) {
            if (line == null) {
                continue;
            }
            BigDecimal signedPurchaseTotal = this.resolveDeptSignedPurchaseTotal(line);
            BigDecimal packagePurchaseUnit = MonetaryAmountSupport.scaleCalc(line.getPrice());
            ProductDefinitionDisplayDto product = productByItemId.get(line.getItemId());
            BigDecimal ratio = this.resolvePackageUnitRatio(product);
            BigDecimal purchaseUnit = this.chargeItemPackageUnitRatioSupport.toAlignedPurchaseDisplayUnitPrice(
                packagePurchaseUnit, ratio, line.getUnitCode(), product);
            BigDecimal purchaseAmount = MonetaryAmountSupport.scaleCalc(signedPurchaseTotal);
            String bucketKey = this.resolveDeptLineDimensionKey(line, dimension, orgByLocation);
            if (bucketKey == null) {
                continue;
            }
            this.accumulateSlipPricingIntoBuckets(
                buckets, bucketKey, purchaseUnit, purchaseAmount, null, null);
        }
    }

    /**
     * 将累加桶写回汇总行进/售净额与展示单价。
     *
     * @param summaryRows 汇总行
     * @param buckets     已累加金额桶
     */
    public void applySummaryAmountBuckets(
        List<? extends MedicationDispensePurchaseSaleSummaryLine> summaryRows,
        SummaryAmountBuckets buckets) {
        if (summaryRows == null || summaryRows.isEmpty() || buckets == null) {
            return;
        }
        for (MedicationDispensePurchaseSaleSummaryLine summaryRow : summaryRows) {
            if (summaryRow == null || StringUtils.isEmpty(summaryRow.getDimensionKey())) {
                continue;
            }
            String key = summaryRow.getDimensionKey();
            BigDecimal purchaseNet = this.sumThenSettleOrNull(buckets.purchaseAmountsByKey.get(key));
            BigDecimal saleNet = this.sumThenSettleOrNull(buckets.saleAmountsByKey.get(key));
            summaryRow.setPurchaseNetAmount(purchaseNet);
            summaryRow.setSaleNetAmount(saleNet);
            summaryRow.setNetAmount(saleNet);
            summaryRow.setPurchaseUnitPrice(
                this.resolveUniqueDisplayUnitPrice(buckets.purchaseUnitsByKey.get(key)));
            summaryRow.setSaleUnitPrice(
                this.resolveUniqueDisplayUnitPrice(buckets.saleUnitsByKey.get(key)));
        }
    }

    /**
     * 汇总金额分块累加桶（避免全窗明细 DTO 常驻后再一次聚合）。
     */
    public static final class SummaryAmountBuckets {

        /** 分桶 → 进价行额列表。 */
        private final Map<String, List<BigDecimal>> purchaseAmountsByKey = new HashMap<>();

        /** 分桶 → 售价行额列表。 */
        private final Map<String, List<BigDecimal>> saleAmountsByKey = new HashMap<>();

        /** 分桶 → 进价展示单价集合。 */
        private final Map<String, Set<BigDecimal>> purchaseUnitsByKey = new HashMap<>();

        /** 分桶 → 售价展示单价集合。 */
        private final Map<String, Set<BigDecimal>> saleUnitsByKey = new HashMap<>();
    }

    /**
     * 收集同桶明细行的批号/账单单价（已 enrich）。
     *
     * @param bucketUnits 分桶 → 展示单价集合
     * @param bucketKey   分桶键
     * @param unitPrice   明细行进/售单价
     */
    private void collectSummaryDisplayUnitPrice(
        Map<String, Set<BigDecimal>> bucketUnits,
        String bucketKey,
        BigDecimal unitPrice) {
        if (unitPrice == null) {
            return;
        }
        bucketUnits
            .computeIfAbsent(bucketKey, ignored -> new LinkedHashSet<>())
            .add(MonetaryAmountSupport.scaleCalc(unitPrice));
    }

    /**
     * 同桶多行批号/账单单价一致时返回该单价；不一致则 null。
     *
     * @param candidates 候选展示单价
     * @return 唯一单价或 null
     */
    private BigDecimal resolveUniqueDisplayUnitPrice(Set<BigDecimal> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }
        BigDecimal unique = null;
        for (BigDecimal candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            BigDecimal scaled = MonetaryAmountSupport.scaleCalc(candidate);
            if (unique == null) {
                unique = scaled;
            } else if (unique.compareTo(scaled) != 0) {
                return null;
            }
        }
        return unique;
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
     * 按发放主键一次批量加载 ChargeItem 并合并进价映射（含住院待结算态，避免按就诊循环）。
     *
     * @param slipRows                  发放事实
     * @param purchaseUnitByDispenseId  输出：dispenseId → 进价单价
     * @param purchaseTotalByDispenseId 输出：dispenseId → 进价总额
     */
    private void mergePatientPurchasePricingByDispenseIds(
        List<MedicationDispenseSlipQueryModel> slipRows,
        Map<Long, BigDecimal> purchaseUnitByDispenseId,
        Map<Long, BigDecimal> purchaseTotalByDispenseId) {
        if (slipRows == null || slipRows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> dispenseIds = new LinkedHashSet<>();
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            if (slip != null && slip.getDispenseId() != null) {
                dispenseIds.add(slip.getDispenseId());
            }
        }
        if (dispenseIds.isEmpty()) {
            return;
        }
        // Pre：跨就诊一次 IN；includePendingSettlement 覆盖住院待结算挂费
        List<ChargeItem> chargeRows =
            this.chargeItemQueryService.listChargeItemsForMedicationDispensePricingByDispenseIds(
                dispenseIds, true);
        this.mergePurchasePricingFromChargeItems(
            chargeRows, purchaseUnitByDispenseId, purchaseTotalByDispenseId);
    }

    /**
     * 将售价别名与进价单价/总额写回明细行。
     *
     * <p>售价缺失保持 {@code null}（前端展示 —），禁止 {@code scaleCalc(null)} 编成 0.00。</p>
     *
     * @param lines                       明细行
     * @param purchaseUnitByDispenseId    dispenseId → 进价单价
     * @param purchaseTotalByDispenseId   dispenseId → 进价总额
     */
    private void applyPurchaseSaleAliases(
        List<? extends MedicationDispensePurchaseSaleDetailLine> lines,
        Map<Long, BigDecimal> purchaseUnitByDispenseId,
        Map<Long, BigDecimal> purchaseTotalByDispenseId) {
        for (MedicationDispensePurchaseSaleDetailLine line : lines) {
            if (line == null) {
                continue;
            }
            // 售价：有账单价才落；null 保持空，避免假 0.00
            if (line.getUnitPrice() != null) {
                BigDecimal saleUnit = MonetaryAmountSupport.scaleCalc(line.getUnitPrice());
                line.setSaleUnitPrice(saleUnit);
                line.setUnitPrice(saleUnit);
            } else {
                line.setSaleUnitPrice(null);
            }
            if (line.getTotalAmount() != null) {
                BigDecimal saleAmount = MonetaryAmountSupport.scaleCalc(line.getTotalAmount());
                line.setSaleAmount(saleAmount);
                line.setTotalAmount(saleAmount);
            } else {
                line.setSaleAmount(null);
            }
            Long dispenseId = line.getDispenseId();
            if (dispenseId != null) {
                BigDecimal purchaseUnit = purchaseUnitByDispenseId.get(dispenseId);
                BigDecimal purchaseAmount = purchaseTotalByDispenseId.get(dispenseId);
                // 退药行进价总额与售价同号（ReadAssembler 售价总额已取负）
                if (purchaseAmount != null
                    && line.getTotalAmount() != null
                    && line.getTotalAmount().signum() < 0
                    && purchaseAmount.signum() > 0) {
                    purchaseAmount = purchaseAmount.negate();
                }
                line.setPurchaseUnitPrice(purchaseUnit);
                line.setPurchaseAmount(purchaseAmount);
            }
        }
    }

    /**
     * 患者发放事实 → dimensionKey（与明细行分桶键同构）。
     */
    private String resolvePatientSlipDimensionKey(
        MedicationDispenseSlipQueryModel slip,
        String dimension,
        Map<Long, String> categoryCodeByMedicationId) {
        if (MedicationDispenseReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)) {
            Long medicationId = slip.getMedicationId();
            if (medicationId == null) {
                return "ITEM:";
            }
            return "ITEM:" + medicationId;
        }
        if (MedicationDispenseReportConstants.SUMMARY_DIMENSION_CATEGORY.equals(dimension)) {
            String categoryCode = "";
            if (slip.getMedicationId() != null) {
                String mapped = categoryCodeByMedicationId.get(slip.getMedicationId());
                if (mapped != null) {
                    categoryCode = mapped;
                }
            }
            return "CATEGORY:" + categoryCode;
        }
        Long locationId = slip.getLocationId();
        if (locationId == null) {
            return "PHARMACY:";
        }
        return "PHARMACY:" + locationId;
    }

    /**
     * 科室领用事实 → dimensionKey。
     */
    private String resolveDeptLineDimensionKey(
        DeptIssueReportLineQueryModel line,
        String dimension,
        Map<Long, Long> organizationIdByPharmacyLocationId) {
        if (DeptIssueReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)) {
            Long itemId = line.getItemId();
            if (itemId == null) {
                return "ITEM:";
            }
            return "ITEM:" + itemId;
        }
        if (DeptIssueReportConstants.SUMMARY_DIMENSION_PHARMACY.equals(dimension)) {
            Long locationId = line.getPurposeLocationId();
            if (locationId == null) {
                return "PHARMACY:";
            }
            return "PHARMACY:" + locationId;
        }
        Long orgId = line.getConsumingOrganizationId();
        if (orgId == null && line.getPurposeLocationId() != null) {
            orgId = organizationIdByPharmacyLocationId.get(line.getPurposeLocationId());
        }
        if (orgId == null) {
            return "DEPARTMENT:";
        }
        return "DEPARTMENT:" + orgId;
    }

    /**
     * 判断发放事实是否仍需批号进价（住院另含批号售价）回落。
     */
    private boolean slipNeedsLotPricingFallback(
        BigDecimal purchaseUnit,
        BigDecimal purchaseTotal,
        BigDecimal saleUnit,
        BigDecimal saleTotal,
        boolean allowInpatientLotSaleWhenChargeMissing) {
        boolean needPurchase = purchaseUnit == null && purchaseTotal == null;
        if (needPurchase) {
            return true;
        }
        if (allowInpatientLotSaleWhenChargeMissing && saleUnit == null && saleTotal == null) {
            return true;
        }
        return false;
    }

    /**
     * 将单条发放/领用定价累加进汇总金额桶。
     */
    private void accumulateSlipPricingIntoBuckets(
        SummaryAmountBuckets buckets,
        String bucketKey,
        BigDecimal purchaseUnit,
        BigDecimal purchaseTotal,
        BigDecimal saleUnit,
        BigDecimal saleTotal) {
        this.accumulateAmount(buckets.purchaseAmountsByKey, bucketKey, purchaseTotal);
        this.accumulateAmount(buckets.saleAmountsByKey, bucketKey, saleTotal);
        this.collectSummaryDisplayUnitPrice(buckets.purchaseUnitsByKey, bucketKey, purchaseUnit);
        this.collectSummaryDisplayUnitPrice(buckets.saleUnitsByKey, bucketKey, saleUnit);
    }

    /**
     * 批号回落后直写汇总桶（禁止再物化明细 DTO）。
     */
    private void supplementLotPricingIntoSummaryBucketsForSlips(
        SummaryAmountBuckets buckets,
        List<PatientSlipPricingState> states,
        String dimension,
        Map<Long, String> categoryCodeByMedicationId,
        boolean allowInpatientLotSaleWhenChargeMissing) {
        if (buckets == null || states == null || states.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> medicationIds = new LinkedHashSet<>();
        LinkedHashSet<ChargeItemDefinitionLotBatchKey> lotKeys = new LinkedHashSet<>();
        for (PatientSlipPricingState state : states) {
            MedicationDispenseSlipQueryModel slip = state.slip;
            if (slip != null
                && slip.getMedicationId() != null
                && !StringUtils.isEmpty(slip.getLotNumber())) {
                medicationIds.add(slip.getMedicationId());
                ChargeItemDefinitionLotBatchKey lotKey = ChargeItemDefinitionLotBatchKey.maybeOf(
                    TableNameConstants.MED_MEDICATION_DEFINITION,
                    slip.getMedicationId(),
                    slip.getLotNumber());
                // Pre：收集结果批号键，供会话与调价键 map 匹配
                if (lotKey != null) {
                    lotKeys.add(lotKey);
                }
            }
        }
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId =
            this.loadProductByMedicationIds(medicationIds);
        // Pre：先拎调价品规+批号，再按本批 lotKeys 匹配后才拉历史
        HistoricalChargeItemPurchasePriceSupport.LotPriceResolveSession lotSession =
            this.historicalChargeItemPurchasePriceSupport.beginLotPriceSession(
                TableNameConstants.MED_MEDICATION_DEFINITION, medicationIds, lotKeys);
        Map<String, BigDecimal> packagePurchaseCache = new HashMap<>();
        Map<String, BigDecimal> packageSaleCache = new HashMap<>();
        for (PatientSlipPricingState state : states) {
            MedicationDispenseSlipQueryModel slip = state.slip;
            if (slip == null || slip.getDispenseId() == null) {
                continue;
            }
            boolean refundRow = this.isRefundSlipRow(slip);
            ProductDefinitionDisplayDto product = productByMedicationId.get(slip.getMedicationId());
            BigDecimal packageUnitRatio = null;
            if (product != null) {
                packageUnitRatio = product.getPackageUnitRatio();
            }
            BigDecimal quantity = slip.getDispenseQuantity();
            if (quantity == null) {
                quantity = slip.getQuantity();
            }
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            String cacheKey = this.buildLotPriceCacheKey(slip);
            boolean skipPurchaseLotBecauseOpRefundSaleMissing =
                !allowInpatientLotSaleWhenChargeMissing
                    && refundRow
                    && state.saleUnit == null
                    && state.saleTotal == null;
            if (!skipPurchaseLotBecauseOpRefundSaleMissing
                && state.purchaseUnit == null
                && state.purchaseTotal == null
                && slip.getMedicationId() != null
                && !StringUtils.isEmpty(slip.getLotNumber())) {
                BigDecimal packagePurchase = packagePurchaseCache.get(cacheKey);
                if (!packagePurchaseCache.containsKey(cacheKey)) {
                    packagePurchase = this.historicalChargeItemPurchasePriceSupport.resolveLotPurchaseUnitPrice(
                        TableNameConstants.MED_MEDICATION_DEFINITION,
                        slip.getMedicationId(),
                        slip.getLotNumber(),
                        slip.getDispenseTime(),
                        lotSession);
                    packagePurchaseCache.put(cacheKey, packagePurchase);
                }
                if (packagePurchase != null) {
                    BigDecimal displayPurchase =
                        this.chargeItemPackageUnitRatioSupport.toAlignedPurchaseDisplayUnitPrice(
                            packagePurchase, packageUnitRatio, slip.getUnitCode(), product);
                    BigDecimal purchaseLine = this.chargeItemPackageUnitRatioSupport.computePurchaseLineAmount(
                        packagePurchase, packageUnitRatio, quantity, slip.getUnitCode(), product);
                    if (displayPurchase != null) {
                        state.purchaseUnit = MonetaryAmountSupport.scaleCalc(displayPurchase);
                    }
                    if (purchaseLine != null) {
                        BigDecimal signedPurchase = purchaseLine;
                        if (refundRow) {
                            signedPurchase = purchaseLine.negate();
                        }
                        state.purchaseTotal = MonetaryAmountSupport.scaleCalc(signedPurchase);
                    }
                }
            }
            if (allowInpatientLotSaleWhenChargeMissing
                && state.saleUnit == null
                && state.saleTotal == null
                && slip.getMedicationId() != null
                && !StringUtils.isEmpty(slip.getLotNumber())) {
                BigDecimal packageSale = packageSaleCache.get(cacheKey);
                if (!packageSaleCache.containsKey(cacheKey)) {
                    packageSale = this.historicalChargeItemPurchasePriceSupport.resolveLotSaleUnitPrice(
                        TableNameConstants.MED_MEDICATION_DEFINITION,
                        slip.getMedicationId(),
                        slip.getLotNumber(),
                        slip.getDispenseTime(),
                        lotSession);
                    packageSaleCache.put(cacheKey, packageSale);
                }
                if (packageSale != null) {
                    BigDecimal displaySale =
                        this.chargeItemPackageUnitRatioSupport.toAlignedPurchaseDisplayUnitPrice(
                            packageSale, packageUnitRatio, slip.getUnitCode(), product);
                    BigDecimal saleLine = this.chargeItemPackageUnitRatioSupport.computePurchaseLineAmount(
                        packageSale, packageUnitRatio, quantity, slip.getUnitCode(), product);
                    if (displaySale != null) {
                        state.saleUnit = MonetaryAmountSupport.scaleCalc(displaySale);
                    }
                    if (saleLine != null) {
                        BigDecimal signedSale = saleLine;
                        if (refundRow) {
                            signedSale = saleLine.negate();
                        }
                        state.saleTotal = MonetaryAmountSupport.scaleCalc(signedSale);
                    }
                }
            }
            String bucketKey = this.resolvePatientSlipDimensionKey(slip, dimension, categoryCodeByMedicationId);
            if (bucketKey == null) {
                continue;
            }
            this.accumulateSlipPricingIntoBuckets(
                buckets,
                bucketKey,
                state.purchaseUnit,
                state.purchaseTotal,
                state.saleUnit,
                state.saleTotal);
        }
    }

    /**
     * 批量装载品规展示（去重 medicationId）。
     */
    private Map<Long, ProductDefinitionDisplayDto> loadProductByMedicationIds(Collection<Long> medicationIds) {
        if (medicationIds == null || medicationIds.isEmpty()) {
            return Map.of();
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (Long medicationId : medicationIds) {
            if (medicationId == null) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(medicationId);
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return Map.of();
        }
        Map<String, ProductDefinitionDisplayDto> byCompositeKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        Map<Long, ProductDefinitionDisplayDto> byMedicationId = new HashMap<>();
        for (Long medicationId : medicationIds) {
            if (medicationId == null) {
                continue;
            }
            String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, medicationId);
            if (compositeKey != null) {
                ProductDefinitionDisplayDto product = byCompositeKey.get(compositeKey);
                if (product != null) {
                    byMedicationId.put(medicationId, product);
                }
            }
        }
        return byMedicationId;
    }

    /**
     * 汇总批号回落前患者侧已解析的售价/进价快照。
     */
    private static final class PatientSlipPricingState {

        /** 发放事实。 */
        private final MedicationDispenseSlipQueryModel slip;

        /** 进价单价。 */
        private BigDecimal purchaseUnit;

        /** 进价总额。 */
        private BigDecimal purchaseTotal;

        /** 售价单价。 */
        private BigDecimal saleUnit;

        /** 售价总额。 */
        private BigDecimal saleTotal;

        /**
         * @param slip          发放事实
         * @param purchaseUnit  已解析进价单价
         * @param purchaseTotal 已解析进价总额
         * @param saleUnit      已解析售价单价
         * @param saleTotal     已解析售价总额
         */
        private PatientSlipPricingState(
            MedicationDispenseSlipQueryModel slip,
            BigDecimal purchaseUnit,
            BigDecimal purchaseTotal,
            BigDecimal saleUnit,
            BigDecimal saleTotal) {
            this.slip = slip;
            this.purchaseUnit = purchaseUnit;
            this.purchaseTotal = purchaseTotal;
            this.saleUnit = saleUnit;
            this.saleTotal = saleTotal;
        }
    }

    /**
     * 账单缺失时按发放批号回落进价；住院白名单另回落批号售价（正/退同行）。
     *
     * <p>取消执行删待结算账单后，正向 {@code COMPLETED} 与退药 {@code REFUNDED} 往往均无 ChargeItem。
     * 若仅退药回落批号价、正向仍空，汇总净额会少掉该品一整笔（日结两侧均回落则轧差为 0）。
     * 进价：门/住账单缺失均可回落 {@code LOT_NUMBER_COST}；售价：仅住院白名单回落 {@code LOT_NUMBER_PRICE}。</p>
     *
     * <p>门诊禁批号售价：退药行若售价仍空（原正向补价失败），禁止单独回落批号进价，
     * 否则会出现「进价冲了、售价不冲」的非对称净额（#4M-RFND）。</p>
     *
     * @param lines                                  明细行
     * @param slipRows                               发放事实
     * @param productByMedicationId                  品规展示
     * @param allowInpatientLotSaleWhenChargeMissing 是否允许批号售价（含正向）
     */
    private void supplementLotPricingWhenChargeMissing(
        List<? extends MedicationDispensePurchaseSaleDetailLine> lines,
        List<MedicationDispenseSlipQueryModel> slipRows,
        Map<Long, ProductDefinitionDisplayDto> productByMedicationId,
        boolean allowInpatientLotSaleWhenChargeMissing) {
        if (lines == null || lines.isEmpty() || slipRows == null || slipRows.isEmpty()) {
            return;
        }
        Map<Long, MedicationDispenseSlipQueryModel> slipByDispenseId = new HashMap<>();
        LinkedHashSet<Long> medicationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> needingDispenseIds = new LinkedHashSet<>();
        for (MedicationDispensePurchaseSaleDetailLine line : lines) {
            if (line != null && line.getDispenseId() != null) {
                needingDispenseIds.add(line.getDispenseId());
            }
        }
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            if (slip == null || slip.getDispenseId() == null) {
                continue;
            }
            // Gate：本批未要求回落的发放行不进批号会话
            if (!needingDispenseIds.contains(slip.getDispenseId())) {
                continue;
            }
            // Pre：正/退均纳入候选；无批号无法回落（禁目录 PURCHASE/SALE）
            if (slip.getMedicationId() == null || StringUtils.isEmpty(slip.getLotNumber())) {
                continue;
            }
            slipByDispenseId.put(slip.getDispenseId(), slip);
            medicationIds.add(slip.getMedicationId());
        }
        if (slipByDispenseId.isEmpty()) {
            return;
        }
        LinkedHashSet<ChargeItemDefinitionLotBatchKey> lotKeys = new LinkedHashSet<>();
        for (MedicationDispenseSlipQueryModel slip : slipByDispenseId.values()) {
            // Pre：本批需回落的发放行组批号键
            if (slip == null || slip.getMedicationId() == null || StringUtils.isEmpty(slip.getLotNumber())) {
                continue;
            }
            ChargeItemDefinitionLotBatchKey lotKey = ChargeItemDefinitionLotBatchKey.maybeOf(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                slip.getMedicationId(),
                slip.getLotNumber());
            if (lotKey != null) {
                lotKeys.add(lotKey);
            }
        }
        HistoricalChargeItemPurchasePriceSupport.LotPriceResolveSession lotSession =
            this.historicalChargeItemPurchasePriceSupport.beginLotPriceSession(
                TableNameConstants.MED_MEDICATION_DEFINITION, medicationIds, lotKeys);
        Map<String, BigDecimal> packagePurchaseCache = new HashMap<>();
        Map<String, BigDecimal> packageSaleCache = new HashMap<>();
        for (MedicationDispensePurchaseSaleDetailLine line : lines) {
            if (line == null || line.getDispenseId() == null) {
                continue;
            }
            MedicationDispenseSlipQueryModel slip = slipByDispenseId.get(line.getDispenseId());
            if (slip == null) {
                continue;
            }
            ProductDefinitionDisplayDto product = null;
            if (productByMedicationId != null) {
                product = productByMedicationId.get(slip.getMedicationId());
            }
            BigDecimal packageUnitRatio = null;
            if (product != null) {
                packageUnitRatio = product.getPackageUnitRatio();
            }
            BigDecimal quantity = slip.getDispenseQuantity();
            if (quantity == null) {
                quantity = slip.getQuantity();
            }
            if (quantity == null || quantity.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            boolean refundRow = this.isRefundSlipRow(slip);
            String cacheKey = this.buildLotPriceCacheKey(slip);
            // Gate：门诊退药且售价仍空 → 跳过批号进价，避免进价单边冲减
            boolean skipPurchaseLotBecauseOpRefundSaleMissing =
                !allowInpatientLotSaleWhenChargeMissing
                    && refundRow
                    && line.getSaleUnitPrice() == null
                    && line.getSaleAmount() == null;
            // Asm：进价缺失 → 批号进价（正/退；退药行额取负）
            if (!skipPurchaseLotBecauseOpRefundSaleMissing
                && line.getPurchaseUnitPrice() == null
                && line.getPurchaseAmount() == null) {
                BigDecimal packagePurchase = packagePurchaseCache.get(cacheKey);
                if (!packagePurchaseCache.containsKey(cacheKey)) {
                    packagePurchase = this.historicalChargeItemPurchasePriceSupport.resolveLotPurchaseUnitPrice(
                        TableNameConstants.MED_MEDICATION_DEFINITION,
                        slip.getMedicationId(),
                        slip.getLotNumber(),
                        slip.getDispenseTime(),
                        lotSession);
                    packagePurchaseCache.put(cacheKey, packagePurchase);
                }
                if (packagePurchase != null) {
                    BigDecimal displayPurchase =
                        this.chargeItemPackageUnitRatioSupport.toAlignedPurchaseDisplayUnitPrice(
                            packagePurchase, packageUnitRatio, slip.getUnitCode(), product);
                    BigDecimal purchaseLine = this.chargeItemPackageUnitRatioSupport.computePurchaseLineAmount(
                        packagePurchase, packageUnitRatio, quantity, slip.getUnitCode(), product);
                    if (displayPurchase != null) {
                        line.setPurchaseUnitPrice(MonetaryAmountSupport.scaleCalc(displayPurchase));
                    }
                    if (purchaseLine != null) {
                        BigDecimal signedPurchase = purchaseLine;
                        if (refundRow) {
                            signedPurchase = purchaseLine.negate();
                        }
                        line.setPurchaseAmount(MonetaryAmountSupport.scaleCalc(signedPurchase));
                    }
                }
            }
            // Asm：住院白名单且售价仍空 → 批号售价（正/退；禁目录 SALE）
            if (allowInpatientLotSaleWhenChargeMissing
                && line.getSaleUnitPrice() == null
                && line.getSaleAmount() == null) {
                BigDecimal packageSale = packageSaleCache.get(cacheKey);
                if (!packageSaleCache.containsKey(cacheKey)) {
                    packageSale = this.historicalChargeItemPurchasePriceSupport.resolveLotSaleUnitPrice(
                        TableNameConstants.MED_MEDICATION_DEFINITION,
                        slip.getMedicationId(),
                        slip.getLotNumber(),
                        slip.getDispenseTime(),
                        lotSession);
                    packageSaleCache.put(cacheKey, packageSale);
                }
                if (packageSale != null) {
                    BigDecimal displaySale =
                        this.chargeItemPackageUnitRatioSupport.toAlignedPurchaseDisplayUnitPrice(
                            packageSale, packageUnitRatio, slip.getUnitCode(), product);
                    BigDecimal saleLine = this.chargeItemPackageUnitRatioSupport.computePurchaseLineAmount(
                        packageSale, packageUnitRatio, quantity, slip.getUnitCode(), product);
                    if (displaySale != null) {
                        BigDecimal scaledSaleUnit = MonetaryAmountSupport.scaleCalc(displaySale);
                        line.setSaleUnitPrice(scaledSaleUnit);
                        line.setUnitPrice(scaledSaleUnit);
                    }
                    if (saleLine != null) {
                        BigDecimal signedSale = saleLine;
                        if (refundRow) {
                            signedSale = saleLine.negate();
                        }
                        BigDecimal scaledSaleAmount = MonetaryAmountSupport.scaleCalc(signedSale);
                        line.setSaleAmount(scaledSaleAmount);
                        line.setTotalAmount(scaledSaleAmount);
                    }
                }
            }
        }
    }

    /**
     * 判定发放事实是否退药行。
     *
     * @param slip 发放事实
     * @return 退药为 true
     */
    private boolean isRefundSlipRow(MedicationDispenseSlipQueryModel slip) {
        if (slip.getDispenseEnum() == MedicationDispenseType.REFUND) {
            return true;
        }
        return slip.getStatusEnum() == DispenseStatus.REFUNDED;
    }

    /**
     * 批号价缓存键：品规 + 发药时刻 + 批号（正/退共用解析会话）。
     *
     * @param slip 发放事实
     * @return 缓存键
     */
    private String buildLotPriceCacheKey(MedicationDispenseSlipQueryModel slip) {
        StringBuilder key = new StringBuilder();
        key.append(slip.getMedicationId());
        key.append('|');
        if (slip.getDispenseTime() != null) {
            key.append(slip.getDispenseTime());
        }
        key.append('|');
        if (!StringUtils.isEmpty(slip.getLotNumber())) {
            key.append(slip.getLotNumber().trim());
        }
        return key.toString();
    }

    /**
     * 从 ChargeItem 合并进价到 dispenseId 映射（按操作单位计行额 + 对齐展示单价）。
     *
     * @param chargeRows                收费明细
     * @param purchaseUnitByDispenseId  展示用进价单价输出
     * @param purchaseTotalByDispenseId 进价总额输出
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
        // Pre：产品倍数一次加载，供操作单位分支行额与展示单价
        LinkedHashSet<ProductDefinitionBatchLookup> lookups = new LinkedHashSet<>();
        for (ChargeItem chargeRow : chargeRows) {
            ProductDefinitionBatchLookup lookup =
                ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeRow);
            if (lookup != null) {
                lookups.add(lookup);
            }
        }
        Map<String, ProductDefinitionDisplayDto> productByCompositeKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(new ArrayList<>(lookups));
        Map<Long, BigDecimal> packageUnitRatioByChargeItemId =
            this.chargeItemPackageUnitRatioSupport.mapPackageUnitRatioByChargeItemId(
                chargeRows, productByCompositeKey);
        // Pre：行额按 quantity_unit 分支；禁止一律拆零满包公式
        Map<Long, BigDecimal> purchaseByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseAmountFromUnitPrices(
                chargeRows,
                purchaseUnitByChargeItemId,
                packageUnitRatioByChargeItemId,
                productByCompositeKey);
        Map<Long, BigDecimal> totalByDispense = new HashMap<>();
        Map<Long, BigDecimal> displayUnitByDispense = new HashMap<>();
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
            // 展示单价：与收费 quantity_unit 对齐（包装收费禁止一律折拆零）
            if (!displayUnitByDispense.containsKey(dispenseKey)) {
                BigDecimal packageUnit = purchaseUnitByChargeItemId.get(chargeRow.getId());
                if (packageUnit != null) {
                    BigDecimal ratio = packageUnitRatioByChargeItemId.get(chargeRow.getId());
                    ProductDefinitionDisplayDto product =
                        this.chargeItemPackageUnitRatioSupport.resolveProduct(chargeRow, productByCompositeKey);
                    displayUnitByDispense.put(
                        dispenseKey,
                        this.chargeItemPackageUnitRatioSupport.toChargeAlignedPurchaseDisplayUnitPrice(
                            packageUnit, ratio, chargeRow, product));
                }
            }
        }
        for (Map.Entry<Long, BigDecimal> entry : totalByDispense.entrySet()) {
            Long dispenseKey = entry.getKey();
            purchaseTotalByDispenseId.put(dispenseKey, MonetaryAmountSupport.scaleCalc(entry.getValue()));
            BigDecimal displayUnit = displayUnitByDispense.get(dispenseKey);
            if (displayUnit != null) {
                purchaseUnitByDispenseId.put(dispenseKey, displayUnit);
            }
        }
        // 有单价无行额时仍写展示单价
        for (Map.Entry<Long, BigDecimal> entry : displayUnitByDispense.entrySet()) {
            if (!purchaseUnitByDispenseId.containsKey(entry.getKey()) && entry.getValue() != null) {
                purchaseUnitByDispenseId.put(entry.getKey(), entry.getValue());
            }
        }
    }

    /**
     * 解析药品品规产品展示（领用侧操作单位对齐用）。
     *
     * @param medicationId 药品定义主键
     * @return 产品展示；无法解析时 null
     */
    private ProductDefinitionDisplayDto resolveMedicationProductDisplay(Long medicationId) {
        if (medicationId == null) {
            return null;
        }
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
        lookup.setProductDefinitionId(medicationId);
        Map<String, ProductDefinitionDisplayDto> productByKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
        String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.MED_MEDICATION_DEFINITION, medicationId);
        if (compositeKey == null) {
            return null;
        }
        return productByKey.get(compositeKey);
    }

    /**
     * 从产品展示解析包装倍数。
     *
     * @param product 产品展示
     * @return 包装倍数；无法解析时 null
     */
    private BigDecimal resolvePackageUnitRatio(ProductDefinitionDisplayDto product) {
        if (product == null || product.getPackageUnitRatio() == null) {
            return null;
        }
        if (product.getPackageUnitRatio().compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return product.getPackageUnitRatio();
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

        /** @return 净数量 */
        BigDecimal getNetQuantity();

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

        /** @param purchaseUnitPrice 进价单价 */
        void setPurchaseUnitPrice(BigDecimal purchaseUnitPrice);

        /** @param saleUnitPrice 售价单价 */
        void setSaleUnitPrice(BigDecimal saleUnitPrice);
    }
}
