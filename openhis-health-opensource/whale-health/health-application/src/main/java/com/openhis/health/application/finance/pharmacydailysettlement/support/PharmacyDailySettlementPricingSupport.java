/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.pharmacydailysettlement.support;

import com.openhis.health.application.finance.pharmacydailysettlement.dto.PharmacyDailySettlementHandoverLineEnrichment;
import com.openhis.health.application.report.medicationdispense.support.MedicationDispenseReportPricingEnricher;
import com.openhis.health.application.shared.pricing.HistoricalChargeItemPurchasePriceSupport;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.PaymentReconciliationQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.share.constants.MedicationDispenseReportConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.financial.DailySettlementRefundPaymentRules;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 药房日结进/售读价与挂载 payment 净收入汇总（DD §2.8.1 / §3.2.1）。
 *
 * <p>主区进/售取自<strong>批号价</strong>（{@code LOT_NUMBER_COST} / {@code LOT_NUMBER_PRICE}，含发药时刻调价回放）
 * × 发药数量（6 位行额），按品规加总后再结算 2 位；大类金额 = 下属品规已结算金额之和。
 * <strong>禁止</strong>回落维护态 {@code PURCHASE}/{@code SALE} 参考价。
 * 头表 {@code total_income} 仍为挂载 payment 净收入（账单路径），与进/售分列。</p>
 */
@Component
@RequiredArgsConstructor
public class PharmacyDailySettlementPricingSupport {

    /** 发放锚点 ChargeItem 读侧（仅头表 payment 净收入关联）。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 药品定义读侧（大类映射）。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;

    /** 发放读侧（提交挂载 dispense 实体加载）。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** payment 读侧（挂载净收入关联）。 */
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /** 品规展示批量装配。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** 历史进/售单价（批号进价/批号售价 + 调价回放）。 */
    private final HistoricalChargeItemPurchasePriceSupport historicalChargeItemPurchasePriceSupport;

    /**
     * 按窗内发药事实汇总大类/品规进售净额与净数量（含已交挂载行）。
     *
     * <p>进/售单价仅取批号价（{@code LOT_NUMBER_COST} / {@code LOT_NUMBER_PRICE}，含发药时刻调价回放），再 × 发药数量；
     * 品规按 {@code medicationId + 进价单价 + 售价单价} 分桶（单价一致才合并）；
     * {@code inpatientMode} 仅保留签名兼容，主区进/售不读账单 ChargeItem。</p>
     *
     * @param dispenses      展示口径发药
     * @param inpatientMode  保留入参兼容（主区进/售不使用）
     * @return 大类/品规汇总快照；无事实时各 Map 为空
     */
    public PharmacyDailySettlementPurchaseSaleSnapshot buildPurchaseSaleSnapshot(
        List<MedicationDispense> dispenses,
        boolean inpatientMode) {
        PharmacyDailySettlementPurchaseSaleSnapshot snapshot = new PharmacyDailySettlementPurchaseSaleSnapshot();
        if (dispenses == null || dispenses.isEmpty()) {
            return snapshot;
        }
        List<MedicationDispenseSlipQueryModel> slipRows = this.toSlipRows(dispenses);
        Map<Long, String> categoryCodeByMedicationId = this.loadCategoryCodeByMedicationId(dispenses);
        // Pre：批号价单价 × 发药数量；品规按「药品+进售单价」分桶
        List<PurchaseSaleDetailLineAdapter> detailLines = this.buildDefinitionPricedDetailLines(slipRows);
        this.accumulateProductAndCategoryFromDetailLines(detailLines, categoryCodeByMedicationId, snapshot);
        this.attachProductDisplay(snapshot);
        return snapshot;
    }

    /**
     * 计算挂载发药集合关联 payment 净收入（DD §3.2.1 列表快照）。
     *
     * <p>关联路径：{@code MedicationDispense.id} → {@code ChargeItem}
     *（{@code dispense_id} 或 {@code service_id} = dispense.id，且 {@code dispense_table} /
     * {@code service_table} = {@code med_medication_dispense}）
     * → {@code PaymentReconciliation.charge_item_ids} 包含 chargeItem.id
     * → 对命中 payment 去重后累加 signed {@code netAmount}
     *（正向 {@code tendered_amount − returned_amount}；逆向退费取负，与收款日结预览净额同构）。</p>
     *
     * @param selectedDispenseIds 本次勾选挂载发药主键
     * @param inpatientMode       住院药房日结时为 true
     * @return 挂载集合关联 payment 净收入合计；无账单锚点时 0
     */
    public BigDecimal sumMountedDispensePaymentNetIncome(
        List<Long> selectedDispenseIds,
        boolean inpatientMode) {
        if (selectedDispenseIds == null || selectedDispenseIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<MedicationDispense> dispenses =
            this.medicationDispenseQueryService.listMedicationDispensesByIds(selectedDispenseIds);
        if (dispenses.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<MedicationDispenseSlipQueryModel> slipRows = this.toSlipRows(dispenses);
        LinkedHashSet<Long> linkedChargeItemIds = this.collectLinkedChargeItemIds(slipRows, inpatientMode);
        if (linkedChargeItemIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        for (MedicationDispense dispense : dispenses) {
            if (dispense != null && dispense.getEncounterId() != null) {
                encounterIds.add(dispense.getEncounterId());
            }
        }
        LinkedHashMap<Long, PaymentReconciliationQueryModel> paymentsById = new LinkedHashMap<>();
        LinkedHashMap<Long, PaymentReconciliationQueryModel> matchedPaymentsById = new LinkedHashMap<>();
        for (Long encounterId : encounterIds) {
            List<PaymentReconciliationQueryModel> payments =
                this.paymentReconciliationQueryService.findPaymentsByEncounter(encounterId);
            for (PaymentReconciliationQueryModel payment : payments) {
                if (payment == null || payment.getId() == null) {
                    continue;
                }
                paymentsById.put(payment.getId(), payment);
                if (!this.isSettlementPaymentStatus(payment.getStatusEnum())) {
                    continue;
                }
                if (this.paymentLinksAnyChargeItem(payment.getChargeItemIds(), linkedChargeItemIds)) {
                    matchedPaymentsById.put(payment.getId(), payment);
                }
            }
        }
        if (matchedPaymentsById.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (PaymentReconciliationQueryModel payment : matchedPaymentsById.values()) {
            total = total.add(this.computeSettlementPaymentNetAmount(payment, paymentsById));
        }
        return MonetaryAmountSupport.scaleCalc(total);
    }

    /**
     * 交账弹窗明细 enrich：按未交账发药装配品规展示与进/售行额（与主区同口径：批号价 × 数量）。
     *
     * @param dispenses     未交账发药实体
     * @param inpatientMode 保留入参兼容（弹窗进/售不读账单）
     * @return dispenseId → enrich；无发药时为空 Map
     */
    public Map<Long, PharmacyDailySettlementHandoverLineEnrichment> buildHandoverLineEnrichments(
        List<MedicationDispense> dispenses,
        boolean inpatientMode) {
        Map<Long, PharmacyDailySettlementHandoverLineEnrichment> enrichments = new LinkedHashMap<>();
        if (dispenses == null || dispenses.isEmpty()) {
            return enrichments;
        }
        List<MedicationDispenseSlipQueryModel> slipRows = this.toSlipRows(dispenses);
        List<PurchaseSaleDetailLineAdapter> detailLines = this.buildDefinitionPricedDetailLines(slipRows);
        Map<Long, PurchaseSaleDetailLineAdapter> lineByDispenseId = new HashMap<>();
        for (PurchaseSaleDetailLineAdapter line : detailLines) {
            if (line != null && line.getDispenseId() != null) {
                lineByDispenseId.put(line.getDispenseId(), line);
            }
        }
        // Pre：批量装载品规展示（名称/规格/厂家/编码）
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        LinkedHashSet<Long> medicationIds = new LinkedHashSet<>();
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || dispense.getMedicationId() == null) {
                continue;
            }
            if (!medicationIds.add(dispense.getMedicationId())) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(dispense.getMedicationId());
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> productByKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || dispense.getId() == null) {
                continue;
            }
            PharmacyDailySettlementHandoverLineEnrichment enrichment =
                new PharmacyDailySettlementHandoverLineEnrichment();
            enrichment.setUnitCode(dispense.getUnitCode());
            Long medicationId = dispense.getMedicationId();
            if (medicationId != null) {
                String key = ProductDefinitionKeyUtil.buildCompositeKey(
                    TableNameConstants.MED_MEDICATION_DEFINITION, medicationId);
                ProductDefinitionDisplayDto product = productByKey.get(key);
                if (product != null) {
                    enrichment.setMedicationName(product.getProductName());
                    enrichment.setMedicationBusNo(product.getProductBusNo());
                    enrichment.setSpecification(product.getTotalVolume());
                    enrichment.setManufacturer(product.getManufacturer());
                }
            }
            PurchaseSaleDetailLineAdapter priced = lineByDispenseId.get(dispense.getId());
            if (priced != null) {
                enrichment.setPurchaseAmount(priced.getPurchaseAmount());
                enrichment.setSaleAmount(priced.getSaleAmount());
                enrichment.setPurchaseUnitPrice(priced.getPurchaseUnitPrice());
                enrichment.setSaleUnitPrice(priced.getSaleUnitPrice());
            }
            enrichments.put(dispense.getId(), enrichment);
        }
        return enrichments;
    }

    /**
     * 按批号价装配行进/售（单价 × 发药数量；含调价回放；不读账单 ChargeItem；不回落参考价）。
     *
     * @param slipRows 发放 slip
     * @return 明细适配行
     */
    private List<PurchaseSaleDetailLineAdapter> buildDefinitionPricedDetailLines(
        List<MedicationDispenseSlipQueryModel> slipRows) {
        List<PurchaseSaleDetailLineAdapter> detailLines = new ArrayList<>();
        if (slipRows == null || slipRows.isEmpty()) {
            return detailLines;
        }
        LinkedHashSet<Long> medicationIds = new LinkedHashSet<>();
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            if (slip != null && slip.getMedicationId() != null) {
                medicationIds.add(slip.getMedicationId());
            }
        }
        // Pre：一次筛有调价品规并预取历史；无调价者后续只读批号价
        HistoricalChargeItemPurchasePriceSupport.LotPriceResolveSession lotSession =
            this.historicalChargeItemPurchasePriceSupport.beginLotPriceSession(
                TableNameConstants.MED_MEDICATION_DEFINITION, medicationIds);
        Map<String, BigDecimal> saleUnitCache = new HashMap<>();
        Map<String, BigDecimal> purchaseUnitCache = new HashMap<>();
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            if (slip == null || slip.getDispenseId() == null) {
                continue;
            }
            PurchaseSaleDetailLineAdapter line = new PurchaseSaleDetailLineAdapter();
            line.setSourceCode(MedicationDispenseReportConstants.SOURCE_PATIENT_DISPENSE);
            line.setDispenseId(slip.getDispenseId());
            line.setMedicationId(slip.getMedicationId());
            line.setPharmacyLocationId(slip.getLocationId());
            BigDecimal signedQuantity = this.signedDispenseQuantity(
                slip.getDispenseEnum(),
                slip.getDispenseQuantity());
            line.setSignedQuantity(signedQuantity);
            BigDecimal saleUnit = this.resolveSaleUnitFromLotPrice(slip, saleUnitCache, lotSession);
            BigDecimal purchaseUnit = this.resolvePurchaseUnitFromLotPrice(slip, purchaseUnitCache, lotSession);
            line.setSaleUnitPrice(MonetaryAmountSupport.scaleCalc(saleUnit));
            line.setPurchaseUnitPrice(MonetaryAmountSupport.scaleCalc(purchaseUnit));
            if (saleUnit != null) {
                BigDecimal saleAmount = MonetaryAmountSupport.lineTotal(saleUnit, signedQuantity);
                line.setSaleAmount(saleAmount);
                line.setUnitPrice(line.getSaleUnitPrice());
                line.setTotalAmount(saleAmount);
            }
            if (purchaseUnit != null) {
                line.setPurchaseAmount(MonetaryAmountSupport.lineTotal(purchaseUnit, signedQuantity));
            }
            detailLines.add(line);
        }
        return detailLines;
    }

    /**
     * 批号售价单价：发药批号 {@code LOT_NUMBER_PRICE} + 发药时刻调价回放；无批号/无价则空（禁止 SALE 参考价）。
     *
     * @param slip          发放事实
     * @param saleUnitCache 缓存
     * @param lotSession    调价预取会话
     * @return 售价单价；无法解析时 {@code null}
     */
    private BigDecimal resolveSaleUnitFromLotPrice(
        MedicationDispenseSlipQueryModel slip,
        Map<String, BigDecimal> saleUnitCache,
        HistoricalChargeItemPurchasePriceSupport.LotPriceResolveSession lotSession) {
        Long medicationId = slip.getMedicationId();
        if (medicationId == null) {
            return null;
        }
        String lotNumber = slip.getLotNumber();
        String cacheKey = this.buildUnitCacheKey(medicationId, slip.getDispenseTime(), lotNumber);
        if (saleUnitCache.containsKey(cacheKey)) {
            return saleUnitCache.get(cacheKey);
        }
        BigDecimal unit = null;
        if (!StringUtils.isEmpty(lotNumber)) {
            // Pre：仅批号售价；禁止回落 SALE 参考价；无调价不查历史
            unit = this.historicalChargeItemPurchasePriceSupport.resolveLotSaleUnitPrice(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                medicationId,
                lotNumber,
                slip.getDispenseTime(),
                lotSession);
        }
        saleUnitCache.put(cacheKey, unit);
        return unit;
    }

    /**
     * 批号进价单价：发药批号 {@code LOT_NUMBER_COST} + 发药时刻调价回放；无批号/无价则空（禁止 PURCHASE 参考价）。
     *
     * @param slip              发放事实
     * @param purchaseUnitCache 缓存
     * @param lotSession        调价预取会话
     * @return 进价单价；无法解析时 {@code null}
     */
    private BigDecimal resolvePurchaseUnitFromLotPrice(
        MedicationDispenseSlipQueryModel slip,
        Map<String, BigDecimal> purchaseUnitCache,
        HistoricalChargeItemPurchasePriceSupport.LotPriceResolveSession lotSession) {
        Long medicationId = slip.getMedicationId();
        if (medicationId == null) {
            return null;
        }
        String lotNumber = slip.getLotNumber();
        String cacheKey = this.buildUnitCacheKey(medicationId, slip.getDispenseTime(), lotNumber);
        if (purchaseUnitCache.containsKey(cacheKey)) {
            return purchaseUnitCache.get(cacheKey);
        }
        BigDecimal unit = null;
        if (!StringUtils.isEmpty(lotNumber)) {
            // Pre：仅批号进价；禁止回落 PURCHASE 参考价；无调价不查历史
            unit = this.historicalChargeItemPurchasePriceSupport.resolveLotPurchaseUnitPrice(
                TableNameConstants.MED_MEDICATION_DEFINITION,
                medicationId,
                lotNumber,
                slip.getDispenseTime(),
                lotSession);
        }
        purchaseUnitCache.put(cacheKey, unit);
        return unit;
    }

    /**
     * 单价缓存键：品规 + 发药时刻 + 批号。
     *
     * @param medicationId 品规主键
     * @param dispenseTime 发药时刻
     * @param lotNumber    批号；可空
     * @return 缓存键
     */
    private String buildUnitCacheKey(
        Long medicationId,
        java.time.LocalDateTime dispenseTime,
        String lotNumber) {
        StringBuilder key = new StringBuilder();
        key.append(medicationId);
        key.append('|');
        if (dispenseTime != null) {
            key.append(dispenseTime);
        }
        key.append('|');
        if (!StringUtils.isEmpty(lotNumber)) {
            key.append(lotNumber.trim());
        }
        return key.toString();
    }


    /**
     * 收集挂载 dispense 关联的 ChargeItem 主键集合。
     */
    private LinkedHashSet<Long> collectLinkedChargeItemIds(
        List<MedicationDispenseSlipQueryModel> slipRows,
        boolean inpatientMode) {
        LinkedHashSet<Long> chargeItemIds = new LinkedHashSet<>();
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
            Long encounterId = entry.getKey();
            List<Long> dispenseIds = entry.getValue();
            List<ChargeItem> chargeRows =
                this.chargeItemQueryService.listChargeItemsForMedicationDispensePricing(encounterId, dispenseIds);
            this.collectChargeItemIds(chargeRows, chargeItemIds);
            if (inpatientMode) {
                List<ChargeItem> pendingRows =
                    this.chargeItemQueryService.listPendingSettlementChargeItemsForMedicationDispensePricing(
                        encounterId, dispenseIds);
                this.collectChargeItemIds(pendingRows, chargeItemIds);
            }
        }
        return chargeItemIds;
    }

    /**
     * 从 ChargeItem 列表提取主键。
     */
    private void collectChargeItemIds(List<ChargeItem> chargeRows, Set<Long> chargeItemIds) {
        if (chargeRows == null || chargeRows.isEmpty()) {
            return;
        }
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow != null && chargeRow.getId() != null) {
                chargeItemIds.add(chargeRow.getId());
            }
        }
    }

    /**
     * 按明细行累加品规/大类：品规键 = medicationId + 进价单价 + 售价单价（单价一致才合并）。
     *
     * @param detailLines                已按「单价×数量」重算的明细（含带符号数量）
     * @param categoryCodeByMedicationId 品规→大类
     * @param snapshot                   汇总快照
     */
    private void accumulateProductAndCategoryFromDetailLines(
        List<PurchaseSaleDetailLineAdapter> detailLines,
        Map<Long, String> categoryCodeByMedicationId,
        PharmacyDailySettlementPurchaseSaleSnapshot snapshot) {
        for (PurchaseSaleDetailLineAdapter detailLine : detailLines) {
            if (detailLine == null) {
                continue;
            }
            Long medicationId = detailLine.getMedicationId();
            if (medicationId == null) {
                continue;
            }
            String productKey = this.buildProductBucketKey(
                medicationId,
                detailLine.getPurchaseUnitPrice(),
                detailLine.getSaleUnitPrice());
            ProductPurchaseSaleBucket productBucket = snapshot.productBuckets.computeIfAbsent(
                productKey,
                ignored -> new ProductPurchaseSaleBucket());
            // Pre：桶身份字段只在首次写入
            if (productBucket.getMedicationId() == null) {
                productBucket.setMedicationId(medicationId);
                productBucket.setPurchaseUnitPrice(detailLine.getPurchaseUnitPrice());
                productBucket.setSaleUnitPrice(detailLine.getSaleUnitPrice());
            }
            BigDecimal signedQuantity = detailLine.getSignedQuantity();
            if (signedQuantity == null) {
                signedQuantity = BigDecimal.ZERO;
            }
            productBucket.netQuantity = this.addNullable(productBucket.netQuantity, signedQuantity);
            this.addAmount(productBucket.purchaseAmounts, detailLine.getPurchaseAmount());
            this.addAmount(productBucket.saleAmounts, detailLine.getSaleAmount());
            // 大类桶：数量随明细累加；金额稍后由品规结算额回填
            String categoryCode = "";
            String mapped = categoryCodeByMedicationId.get(medicationId);
            if (mapped != null) {
                categoryCode = mapped;
            }
            CategoryPurchaseSaleBucket categoryBucket = snapshot.categoryBuckets.computeIfAbsent(
                categoryCode,
                ignored -> new CategoryPurchaseSaleBucket());
            categoryBucket.netQuantity = this.addNullable(categoryBucket.netQuantity, signedQuantity);
        }
        for (ProductPurchaseSaleBucket bucket : snapshot.productBuckets.values()) {
            bucket.netQuantity = MonetaryAmountSupport.scaleCalc(bucket.netQuantity);
        }
        for (CategoryPurchaseSaleBucket bucket : snapshot.categoryBuckets.values()) {
            bucket.netQuantity = MonetaryAmountSupport.scaleCalc(bucket.netQuantity);
        }
        // 品规：行额 6 位加总后再四舍五入到 2 位
        this.finalizeProductBucketAmounts(snapshot.productBuckets);
        // 大类：下属品规已结算金额之和（与下表加总一致）
        this.rebuildCategoryAmountsFromProducts(snapshot, categoryCodeByMedicationId);
    }

    /**
     * 品规分桶键：药品主键 + 进价单价 + 售价单价（刻度后 plain 串，空单价用空段）。
     *
     * @param medicationId       药品主键
     * @param purchaseUnitPrice  进价单价
     * @param saleUnitPrice      售价单价
     * @return 分桶键
     */
    private String buildProductBucketKey(
        Long medicationId,
        BigDecimal purchaseUnitPrice,
        BigDecimal saleUnitPrice) {
        StringBuilder key = new StringBuilder();
        key.append(medicationId);
        key.append('|');
        key.append(this.unitPriceKeySegment(purchaseUnitPrice));
        key.append('|');
        key.append(this.unitPriceKeySegment(saleUnitPrice));
        return key.toString();
    }

    /**
     * 单价键段：null→空串；非空→计算刻度 plainString（保证同价可合并）。
     *
     * @param unitPrice 单价
     * @return 键段
     */
    private String unitPriceKeySegment(BigDecimal unitPrice) {
        if (unitPrice == null) {
            return "";
        }
        return MonetaryAmountSupport.scaleCalc(unitPrice).toPlainString();
    }

    /**
     * 大类进/售金额 = 下属品规已结算金额之和（不再独立 sumThenSettle 行额，避免与品规表不一致）。
     *
     * @param snapshot                   汇总快照
     * @param categoryCodeByMedicationId 品规→大类
     */
    private void rebuildCategoryAmountsFromProducts(
        PharmacyDailySettlementPurchaseSaleSnapshot snapshot,
        Map<Long, String> categoryCodeByMedicationId) {
        for (CategoryPurchaseSaleBucket categoryBucket : snapshot.categoryBuckets.values()) {
            categoryBucket.setPurchaseAmount(BigDecimal.ZERO);
            categoryBucket.setSaleAmount(BigDecimal.ZERO);
            categoryBucket.getPurchaseAmounts().clear();
            categoryBucket.getSaleAmounts().clear();
        }
        for (ProductPurchaseSaleBucket productBucket : snapshot.productBuckets.values()) {
            if (productBucket == null || productBucket.getMedicationId() == null) {
                continue;
            }
            Long medicationId = productBucket.getMedicationId();
            String categoryCode = "";
            String mapped = categoryCodeByMedicationId.get(medicationId);
            if (mapped != null) {
                categoryCode = mapped;
            }
            CategoryPurchaseSaleBucket categoryBucket = snapshot.categoryBuckets.computeIfAbsent(
                categoryCode,
                ignored -> new CategoryPurchaseSaleBucket());
            categoryBucket.setPurchaseAmount(
                this.addNullable(categoryBucket.getPurchaseAmount(), productBucket.getPurchaseAmount()));
            categoryBucket.setSaleAmount(
                this.addNullable(categoryBucket.getSaleAmount(), productBucket.getSaleAmount()));
        }
        for (CategoryPurchaseSaleBucket categoryBucket : snapshot.categoryBuckets.values()) {
            categoryBucket.setPurchaseAmount(
                MonetaryAmountSupport.scaleSettle(MonetaryAmountSupport.nullToZero(categoryBucket.getPurchaseAmount())));
            categoryBucket.setSaleAmount(
                MonetaryAmountSupport.scaleSettle(MonetaryAmountSupport.nullToZero(categoryBucket.getSaleAmount())));
        }
    }

    /**
     * 回填品规展示字段（名称/编码/规格/厂家）。
     */
    private void attachProductDisplay(PharmacyDailySettlementPurchaseSaleSnapshot snapshot) {
        if (snapshot.productBuckets.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> medicationIds = new LinkedHashSet<>();
        for (ProductPurchaseSaleBucket bucket : snapshot.productBuckets.values()) {
            if (bucket != null && bucket.getMedicationId() != null) {
                medicationIds.add(bucket.getMedicationId());
            }
        }
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (Long medicationId : medicationIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(medicationId);
            lookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> productByKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        for (ProductPurchaseSaleBucket bucket : snapshot.productBuckets.values()) {
            if (bucket == null || bucket.getMedicationId() == null) {
                continue;
            }
            String key = ProductDefinitionKeyUtil.buildCompositeKey(
                TableNameConstants.MED_MEDICATION_DEFINITION, bucket.getMedicationId());
            ProductDefinitionDisplayDto product = productByKey.get(key);
            if (product == null) {
                continue;
            }
            bucket.medicationName = product.getProductName();
            bucket.busNo = product.getProductBusNo();
            bucket.specification = product.getTotalVolume();
            bucket.manufacturer = product.getManufacturer();
        }
    }

    /**
     * medicationId → category_code 批量映射。
     */
    private Map<Long, String> loadCategoryCodeByMedicationId(List<MedicationDispense> dispenses) {
        LinkedHashSet<Long> medicationIds = new LinkedHashSet<>();
        for (MedicationDispense dispense : dispenses) {
            if (dispense != null && dispense.getMedicationId() != null) {
                medicationIds.add(dispense.getMedicationId());
            }
        }
        Map<Long, String> categoryCodeByMedicationId = new HashMap<>();
        if (medicationIds.isEmpty()) {
            return categoryCodeByMedicationId;
        }
        for (MedicationDefinition definition : this.medicationDefinitionQueryService.findAllByIds(medicationIds)) {
            if (definition != null && definition.getId() != null) {
                categoryCodeByMedicationId.put(definition.getId(), definition.getCategoryCode());
            }
        }
        return categoryCodeByMedicationId;
    }

    /**
     * 发放实体 → slip 读模型（与 MedicationDispenseQueryService#toSlipQueryModel 同构）。
     */
    private List<MedicationDispenseSlipQueryModel> toSlipRows(List<MedicationDispense> dispenses) {
        List<MedicationDispenseSlipQueryModel> slipRows = new ArrayList<>();
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null) {
                continue;
            }
            MedicationDispenseSlipQueryModel slip = new MedicationDispenseSlipQueryModel();
            slip.setDispenseId(dispense.getId());
            slip.setBusNo(dispense.getBusNo());
            slip.setMedReqId(dispense.getMedReqId());
            slip.setStatusEnum(dispense.getStatusEnum());
            slip.setNotPerformedReasonEnum(dispense.getNotPerformedReasonEnum());
            slip.setStatusChangedTime(dispense.getStatusChangedTime());
            slip.setDispenseEnum(dispense.getDispenseEnum());
            slip.setMedicationId(dispense.getMedicationId());
            slip.setPatientId(dispense.getPatientId());
            slip.setEncounterId(dispense.getEncounterId());
            slip.setLocationId(dispense.getLocationId());
            slip.setQuantity(dispense.getQuantity());
            slip.setUnitCode(dispense.getUnitCode());
            slip.setDispenseQuantity(dispense.getDispenseQuantity());
            slip.setLotNumber(dispense.getLotNumber());
            slip.setTraceNo(dispense.getTraceNo());
            slip.setPreparerId(dispense.getPreparerId());
            slip.setPrepareTime(dispense.getPrepareTime());
            slip.setDispenserId(dispense.getPractitionerId());
            slip.setDispenseTime(dispense.getDispenseTime());
            slip.setSummaryNo(dispense.getSummaryNo());
            slipRows.add(slip);
        }
        return slipRows;
    }

    /**
     * 是否纳入交账 payment 净收入的状态。
     */
    private boolean isSettlementPaymentStatus(PaymentStatus statusEnum) {
        return PaymentStatus.SUCCESS.equals(statusEnum) || PaymentStatus.REFUNDED.equals(statusEnum);
    }

    /**
     * payment 快照 charge_item_ids 是否与目标集合有交集。
     */
    private boolean paymentLinksAnyChargeItem(String chargeItemIdsSnapshot, Set<Long> targetChargeItemIds) {
        if (targetChargeItemIds == null || targetChargeItemIds.isEmpty()) {
            return false;
        }
        for (Long chargeItemId : this.parseChargeItemIds(chargeItemIdsSnapshot)) {
            if (chargeItemId != null && targetChargeItemIds.contains(chargeItemId)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 解析 payment.charge_item_ids 逗号分隔串。
     */
    private List<Long> parseChargeItemIds(String rawChargeItemIds) {
        if (StringUtils.isEmpty(rawChargeItemIds)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String segment : rawChargeItemIds.split(",")) {
            String trimmed = StringUtils.trimToNull(segment);
            if (trimmed == null) {
                continue;
            }
            try {
                ids.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignored) {
                // 跳过非法片段
            }
        }
        return ids;
    }

    /**
     * 单条 payment 交账净额（正向 tendered−returned；逆向取负，基数对齐原单 display/tendered）。
     *
     * @param payment       命中 payment 读模型
     * @param paymentsById  同就诊已加载 payment（含 relation 原单）
     * @return 带符号净额
     */
    private BigDecimal computeSettlementPaymentNetAmount(
        PaymentReconciliationQueryModel payment,
        Map<Long, PaymentReconciliationQueryModel> paymentsById) {
        if (payment == null) {
            return BigDecimal.ZERO;
        }
        if (DailySettlementRefundPaymentRules.isRefundPaymentForSettlement(
            payment.getPaymentEnum(),
            payment.getRelationId(),
            payment.getStatusEnum())) {
            BigDecimal amount = this.resolveReversePreviewAmountBasis(payment, paymentsById);
            if (amount == null) {
                amount = BigDecimal.ZERO;
            }
            return amount.abs().negate();
        }
        BigDecimal tendered = payment.getTenderedAmount();
        if (tendered == null) {
            tendered = BigDecimal.ZERO;
        }
        BigDecimal returned = payment.getReturnedAmount();
        if (returned == null) {
            returned = BigDecimal.ZERO;
        }
        return tendered.subtract(returned);
    }

    /**
     * 逆向退费净额基数：优先原单 display/tendered，否则回落本单。
     */
    private BigDecimal resolveReversePreviewAmountBasis(
        PaymentReconciliationQueryModel reverse,
        Map<Long, PaymentReconciliationQueryModel> paymentsById) {
        if (reverse != null && reverse.getRelationId() != null && paymentsById != null) {
            PaymentReconciliationQueryModel original = paymentsById.get(reverse.getRelationId());
            if (original != null) {
                BigDecimal originalAmount = original.getDisplayAmount();
                if (originalAmount == null) {
                    originalAmount = original.getTenderedAmount();
                }
                if (originalAmount != null) {
                    return originalAmount;
                }
            }
        }
        if (reverse == null) {
            return null;
        }
        BigDecimal amount = reverse.getDisplayAmount();
        if (amount == null) {
            amount = reverse.getTenderedAmount();
        }
        return amount;
    }

    /**
     * 向金额列表追加一行（忽略 null）。
     */
    private void addAmount(List<BigDecimal> amounts, BigDecimal amount) {
        if (amount == null) {
            return;
        }
        amounts.add(amount);
    }

    /**
     * 可空 BigDecimal 相加。
     */
    private BigDecimal addNullable(BigDecimal left, BigDecimal right) {
        if (left == null) {
            if (right == null) {
                return BigDecimal.ZERO;
            }
            return right;
        }
        if (right == null) {
            return left;
        }
        return left.add(right);
    }

    /**
     * 品规桶进/售行额结算（6 位加总 → 2 位 HALF_UP）。
     *
     * @param productBuckets 品规分桶（键含单价）
     */
    private void finalizeProductBucketAmounts(Map<String, ProductPurchaseSaleBucket> productBuckets) {
        for (ProductPurchaseSaleBucket bucket : productBuckets.values()) {
            bucket.purchaseAmount = MonetaryAmountSupport.sumThenSettle(bucket.purchaseAmounts);
            bucket.saleAmount = MonetaryAmountSupport.sumThenSettle(bucket.saleAmounts);
        }
    }

    /**
     * 退药行数量取负（REFUND 类型）。
     */
    private BigDecimal signedDispenseQuantity(
        MedicationDispenseType dispenseEnum,
        BigDecimal dispenseQuantity) {
        if (dispenseQuantity == null) {
            return BigDecimal.ZERO;
        }
        if (MedicationDispenseType.REFUND.equals(dispenseEnum)) {
            return dispenseQuantity.negate();
        }
        return dispenseQuantity;
    }

    /** 大类/品规进售汇总快照。 */
    @Getter
    public static class PharmacyDailySettlementPurchaseSaleSnapshot {

        /** category_code → 汇总桶。 */
        private final Map<String, CategoryPurchaseSaleBucket> categoryBuckets = new LinkedHashMap<>();

        /**
         * 品规汇总桶；键 = medicationId|进价单价|售价单价（单价一致才合并）。
         */
        private final Map<String, ProductPurchaseSaleBucket> productBuckets = new LinkedHashMap<>();
    }

    /** 大类汇总桶（中间态 + 结算结果）。 */
    @Getter
    @Setter
    static class CategoryPurchaseSaleBucket {

        /** 进价行列表（6 位加总前）。 */
        private final List<BigDecimal> purchaseAmounts = new ArrayList<>();

        /** 售价行列表（6 位加总前）。 */
        private final List<BigDecimal> saleAmounts = new ArrayList<>();

        /** 净数量（大类主区不展示，仍保留轧差供对账）。 */
        private BigDecimal netQuantity = BigDecimal.ZERO;

        /** 进价净额。 */
        private BigDecimal purchaseAmount;

        /** 售价净额。 */
        private BigDecimal saleAmount;
    }

    /** 品规汇总桶（同药不同进/售单价分桶）。 */
    @Getter
    @Setter
    static class ProductPurchaseSaleBucket {

        /** 药品主键。 */
        private Long medicationId;

        /** 进价单价（分桶键之一；同桶内一致）。 */
        private BigDecimal purchaseUnitPrice;

        /** 售价单价（分桶键之一；同桶内一致）。 */
        private BigDecimal saleUnitPrice;

        /** 进价行列表。 */
        private final List<BigDecimal> purchaseAmounts = new ArrayList<>();

        /** 售价行列表。 */
        private final List<BigDecimal> saleAmounts = new ArrayList<>();

        /** 净数量（一发一退可为 0，仍保留行）。 */
        private BigDecimal netQuantity = BigDecimal.ZERO;

        /** 进价净额。 */
        private BigDecimal purchaseAmount;

        /** 售价净额。 */
        private BigDecimal saleAmount;

        /** 药品名称。 */
        private String medicationName;

        /** 目录 bus_no。 */
        private String busNo;

        /** 规格。 */
        private String specification;

        /** 生产厂家。 */
        private String manufacturer;
    }

    /**
     * enricher 明细契约适配（患者发药行）。
     */
    @Getter
    @Setter
    static class PurchaseSaleDetailLineAdapter
        implements MedicationDispenseReportPricingEnricher.MedicationDispensePurchaseSaleDetailLine {

        private String sourceCode;
        private Long dispenseId;
        private Long medicationId;
        private Long pharmacyLocationId;
        private Long departmentId;
        private BigDecimal unitPrice;
        private BigDecimal totalAmount;
        private BigDecimal purchaseUnitPrice;
        private BigDecimal purchaseAmount;
        private BigDecimal saleUnitPrice;
        private BigDecimal saleAmount;
        /** 带符号发药数量（退药为负；用于品规净数量分桶）。 */
        private BigDecimal signedQuantity;
    }
}
