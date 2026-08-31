/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.finance.pharmacydailysettlement.support;

import com.openhis.health.application.finance.pharmacydailysettlement.dto.PharmacyDailySettlementHandoverLineEnrichment;
import com.openhis.health.application.outpatient.pharmacy.dispenserecord.support.PharmacyDispenseRecordChargePricingAssembler;
import com.openhis.health.application.report.medicationdispense.support.MedicationDispenseReportPricingEnricher;
import com.openhis.health.application.shared.pricing.ChargeItemPackageUnitRatioSupport;
import com.openhis.health.application.shared.pricing.HistoricalChargeItemPurchasePriceSupport;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.chargeitemdefinition.query.ChargeItemDefinitionLotBatchKey;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationdefinition.service.MedicationDefinitionQueryService;
import com.openhis.health.domain.medication.medicationdispense.entity.MedicationDispense;
import com.openhis.health.domain.medication.medicationdispense.queries.model.MedicationDispenseSlipQueryModel;
import com.openhis.health.domain.medication.medicationdispense.service.MedicationDispenseQueryService;
import com.openhis.health.domain.medication.medicationrequest.queries.model.MedicationRequestListQueryModel;
import com.openhis.health.domain.medication.medicationrequest.service.MedicationRequestQueryService;
import com.openhis.health.domain.share.constants.MedicationDispenseReportConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.MedicationDispenseType;
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
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 药房日结进/售读价与售价合计汇总（DD §2；患者侧与发药报表同口径）。
 *
 * <p><strong>售价</strong>优先发放锚点 {@code ChargeItem} 行额（账单实收，不回放）；
 * 退药写路径<strong>不</strong>生成自身账单（设计），须按原正向发放补售价后再取负轧差
 * （与发退药记录 / 发药报表 {@link PharmacyDispenseRecordChargePricingAssembler} 同口径；
 * 同数量时总额绝对值与正向 {@code total_price} 一致）。
 * 原正向仍不可得（如住院取消执行逻辑删待结算行）时：回落批号 {@code LOT_NUMBER_PRICE}，
 * 同批号调价回放与进价同构，按发药 {@code unitCode} 分支计行额；
 * <strong>禁止</strong>回落目录 {@code SALE}。
 * <strong>进价</strong>取批号 {@code LOT_NUMBER_COST}（包装锚），按发药 {@code unitCode} 分支计行额
 * （包装单位→包装价×数量；拆零→满包×包装价+零头单步，BR-MON-PKG-001）；
 * 仅当调价明细批号与发药批号一致时才做发药时刻回放；
 * <strong>禁止</strong>串批回放、禁止回落维护态 {@code PURCHASE}、禁止包装价÷ratio 得拆零价再×数量。
 * 头表 {@code total_income} = <strong>售价合计</strong>（与主区大类售价总额之和同口径，不读 payment；
 * 不为售价−进价利润）。</p>
 */
@Component
@RequiredArgsConstructor
public class PharmacyDailySettlementPricingSupport {

    /** 药品定义读侧（大类映射）。 */
    private final MedicationDefinitionQueryService medicationDefinitionQueryService;

    /** 发放读侧（提交挂载 dispense 实体加载）。 */
    private final MedicationDispenseQueryService medicationDispenseQueryService;

    /** 品规展示批量装配。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /** 按操作单位计包装锚行进价（与发药报表同口径）。 */
    private final ChargeItemPackageUnitRatioSupport chargeItemPackageUnitRatioSupport;

    /** 批号进价（含同批号调价回放）。 */
    private final HistoricalChargeItemPurchasePriceSupport historicalChargeItemPurchasePriceSupport;

    /** 收费明细读侧（患者侧售价锚点）。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 药嘱读侧（退药行解析原正向药嘱）。 */
    private final MedicationRequestQueryService medicationRequestQueryService;

    /** 退药行按原正向发放补售价（与发退药记录同口径）。 */
    private final PharmacyDispenseRecordChargePricingAssembler pharmacyDispenseRecordChargePricingAssembler;

    /**
     * 按窗内发药事实汇总大类/品规进售净额与净数量（含已交挂载行）。
     *
     * <p>售价 = 发放锚点账单行额（缺则批号 {@code LOT_NUMBER_PRICE} 同批号回放）；
     * 进价 = 批号包装进价按 {@code unitCode} 分支计行额（同批号才调价回放）。
     * 品规按 {@code medicationId + 进价单价 + 售价单价} 分桶（单价一致才合并）。
     * {@code inpatientMode=true} 时收费态含住院 {@code PENDING_SETTLEMENT}。</p>
     *
     * @param dispenses      展示口径发药
     * @param inpatientMode  住院药房日结时为 true（收费态含待结算）
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
        // Pre：账单售价 + 批号进价；品规按「药品+进售单价」分桶
        List<PurchaseSaleDetailLineAdapter> detailLines =
            this.buildDefinitionPricedDetailLines(slipRows, inpatientMode);
        for (PurchaseSaleDetailLineAdapter line : detailLines) {
            if (line != null && line.getDispenseId() != null) {
                snapshot.detailLineByDispenseId.put(line.getDispenseId(), line);
            }
        }
        this.accumulateProductAndCategoryFromDetailLines(detailLines, categoryCodeByMedicationId, snapshot);
        this.attachProductDisplay(snapshot);
        return snapshot;
    }

    /**
     * 由进/售快照计算药房日结售价合计（各大类/品规售价总额之和）。
     *
     * <p>与大类主区同口径：品规已结算 2 位金额代数加总后再 {@link MonetaryAmountSupport#scaleSettle}；
     * 退药行售价为负，轧差后可为负。</p>
     *
     * @param snapshot 进/售汇总快照（可与主区大类/品规共用）
     * @return 售价合计（结算 2 位）；快照为空时 0
     */
    public BigDecimal sumSaleAmount(PharmacyDailySettlementPurchaseSaleSnapshot snapshot) {
        if (snapshot == null || snapshot.productBuckets.isEmpty()) {
            return BigDecimal.ZERO;
        }
        BigDecimal saleTotal = BigDecimal.ZERO;
        // Asm：按品规已结算售价加总（与大类 = 品规之和一致）
        for (ProductPurchaseSaleBucket bucket : snapshot.productBuckets.values()) {
            if (bucket == null || bucket.getSaleAmount() == null) {
                continue;
            }
            saleTotal = saleTotal.add(bucket.getSaleAmount());
        }
        return MonetaryAmountSupport.scaleSettle(saleTotal);
    }

    /**
     * 按发药实体集合现算售价合计（审批详情现算 / 提交未复用主区快照时）。
     *
     * @param dispenses     发药实体
     * @param inpatientMode 住院时收费态含待结算
     * @return 售价合计；无发药时 0
     */
    public BigDecimal sumSaleAmountFromDispenses(
        List<MedicationDispense> dispenses,
        boolean inpatientMode) {
        PharmacyDailySettlementPurchaseSaleSnapshot snapshot =
            this.buildPurchaseSaleSnapshot(dispenses, inpatientMode);
        return this.sumSaleAmount(snapshot);
    }

    /**
     * 按发药主键集合现算售价合计（提交落头表 {@code total_income}）。
     *
     * <p>与预览 {@link #sumSaleAmount}、审批详情大类售价之和同口径；退药行售价为负，轧差后可为负。</p>
     *
     * @param selectedDispenseIds 本次勾选挂载发药主键
     * @param inpatientMode       住院时收费态含待结算
     * @return 售价合计（结算 2 位）；无勾选时 0
     */
    public BigDecimal sumSaleAmountByDispenseIds(
        List<Long> selectedDispenseIds,
        boolean inpatientMode) {
        if (selectedDispenseIds == null || selectedDispenseIds.isEmpty()) {
            return BigDecimal.ZERO;
        }
        List<MedicationDispense> dispenses =
            this.medicationDispenseQueryService.listMedicationDispensesByIds(selectedDispenseIds);
        return this.sumSaleAmountFromDispenses(dispenses, inpatientMode);
    }

    /**
     * 交账弹窗明细 enrich：按未交账发药装配品规展示与进/售行额（与主区同口径：账单售价 + 批号进价）。
     *
     * @param dispenses     未交账发药实体
     * @param inpatientMode 住院时收费态含待结算
     * @return dispenseId → enrich；无发药时为空 Map
     */
    public Map<Long, PharmacyDailySettlementHandoverLineEnrichment> buildHandoverLineEnrichments(
        List<MedicationDispense> dispenses,
        boolean inpatientMode) {
        return this.buildHandoverLineEnrichments(dispenses, inpatientMode, null);
    }

    /**
     * 交账弹窗明细 enrich；可复用主区快照中已算行价，避免对未交账子集二次读价。
     *
     * @param dispenses     未交账发药实体
     * @param inpatientMode 住院时收费态含待结算
     * @param snapshot      主区进/售快照；非空时优先复用其行价
     * @return dispenseId → enrich
     */
    public Map<Long, PharmacyDailySettlementHandoverLineEnrichment> buildHandoverLineEnrichments(
        List<MedicationDispense> dispenses,
        boolean inpatientMode,
        PharmacyDailySettlementPurchaseSaleSnapshot snapshot) {
        Map<Long, PharmacyDailySettlementHandoverLineEnrichment> enrichments = new LinkedHashMap<>();
        if (dispenses == null || dispenses.isEmpty()) {
            return enrichments;
        }
        Map<Long, PurchaseSaleDetailLineAdapter> lineByDispenseId = new HashMap<>();
        if (snapshot != null && !snapshot.detailLineByDispenseId.isEmpty()) {
            lineByDispenseId.putAll(snapshot.detailLineByDispenseId);
        } else {
            List<MedicationDispenseSlipQueryModel> slipRows = this.toSlipRows(dispenses);
            List<PurchaseSaleDetailLineAdapter> detailLines =
                this.buildDefinitionPricedDetailLines(slipRows, inpatientMode);
            for (PurchaseSaleDetailLineAdapter line : detailLines) {
                if (line != null && line.getDispenseId() != null) {
                    lineByDispenseId.put(line.getDispenseId(), line);
                }
            }
        }
        // Pre：批量装载品规展示（名称/规格/厂家/编码）；优先复用主区快照已挂展示
        Map<Long, ProductPurchaseSaleBucket> productBucketByMedicationId = new HashMap<>();
        if (snapshot != null) {
            for (ProductPurchaseSaleBucket bucket : snapshot.productBuckets.values()) {
                if (bucket != null && bucket.getMedicationId() != null
                    && !productBucketByMedicationId.containsKey(bucket.getMedicationId())) {
                    productBucketByMedicationId.put(bucket.getMedicationId(), bucket);
                }
            }
        }
        LinkedHashSet<Long> missingMedicationIds = new LinkedHashSet<>();
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || dispense.getMedicationId() == null) {
                continue;
            }
            if (!productBucketByMedicationId.containsKey(dispense.getMedicationId())) {
                missingMedicationIds.add(dispense.getMedicationId());
            }
        }
        Map<String, ProductDefinitionDisplayDto> productByKey = Map.of();
        if (!missingMedicationIds.isEmpty()) {
            List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
            for (Long medicationId : missingMedicationIds) {
                ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
                lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
                lookup.setProductDefinitionId(medicationId);
                lookups.add(lookup);
            }
            productByKey = this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
        }
        for (MedicationDispense dispense : dispenses) {
            if (dispense == null || dispense.getId() == null) {
                continue;
            }
            PharmacyDailySettlementHandoverLineEnrichment enrichment =
                new PharmacyDailySettlementHandoverLineEnrichment();
            enrichment.setUnitCode(dispense.getUnitCode());
            Long medicationId = dispense.getMedicationId();
            if (medicationId != null) {
                ProductPurchaseSaleBucket bucket = productBucketByMedicationId.get(medicationId);
                if (bucket != null) {
                    enrichment.setMedicationName(bucket.getMedicationName());
                    enrichment.setMedicationBusNo(bucket.getBusNo());
                    enrichment.setSpecification(bucket.getSpecification());
                    enrichment.setManufacturer(bucket.getManufacturer());
                } else {
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
     * 按批号包装进价 + 账单售价（缺则批号售价回落）装配明细行。
     *
     * <p>进价按 unitCode 分支计行额，可回放；售价有账单不回放，无账单回落批号售价并可回放。</p>
     *
     * @param slipRows       发放 slip
     * @param inpatientMode  住院时含待结算账单
     * @return 明细适配行
     */
    private List<PurchaseSaleDetailLineAdapter> buildDefinitionPricedDetailLines(
        List<MedicationDispenseSlipQueryModel> slipRows,
        boolean inpatientMode) {
        List<PurchaseSaleDetailLineAdapter> detailLines = new ArrayList<>();
        if (slipRows == null || slipRows.isEmpty()) {
            return detailLines;
        }
        LinkedHashSet<Long> medicationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> dispenseIds = new LinkedHashSet<>();
        LinkedHashSet<ChargeItemDefinitionLotBatchKey> lotKeys = new LinkedHashSet<>();
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            if (slip == null || slip.getMedicationId() == null) {
                continue;
            }
            medicationIds.add(slip.getMedicationId());
            if (slip.getDispenseId() != null) {
                dispenseIds.add(slip.getDispenseId());
            }
            if (!StringUtils.isEmpty(slip.getLotNumber())) {
                ChargeItemDefinitionLotBatchKey lotKey = ChargeItemDefinitionLotBatchKey.maybeOf(
                    TableNameConstants.MED_MEDICATION_DEFINITION,
                    slip.getMedicationId(),
                    slip.getLotNumber());
                if (lotKey != null) {
                    lotKeys.add(lotKey);
                }
            }
        }
        // Pre：账单售价一次按发放锚点批量加载（与发药报表患者侧同口径）
        Map<Long, BigDecimal> saleUnitByDispenseId = new HashMap<>();
        Map<Long, BigDecimal> saleTotalByDispenseId = new HashMap<>();
        this.mergeChargeSalePricingByDispenseIds(
            dispenseIds, inpatientMode, saleUnitByDispenseId, saleTotalByDispenseId);
        // Pre：退药行常无自有 dispense 账单锚点，按原正向发放补售价（漏补则日结少扣退费）
        this.supplementMissingRefundSalePricing(slipRows, saleUnitByDispenseId, saleTotalByDispenseId);
        // Pre：单独查调价品规+批号键；warm 时与本批 lotKeys 匹配后再拉历史
        HistoricalChargeItemPurchasePriceSupport.LotPriceResolveSession lotSession =
            this.historicalChargeItemPurchasePriceSupport.beginLotPriceSession(
                TableNameConstants.MED_MEDICATION_DEFINITION, medicationIds);
        // Pre：批号进/售一次预热，并仅对命中调价键的品规预取历史
        this.historicalChargeItemPurchasePriceSupport.warmCurrentLotPrices(lotSession, lotKeys);
        // Pre：品规展示一次装载 — 判 unitCode 是否包装 + 取 packageUnitRatio
        List<ProductDefinitionBatchLookup> productLookups = new ArrayList<>();
        for (Long medicationId : medicationIds) {
            ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
            lookup.setProductTableName(TableNameConstants.MED_MEDICATION_DEFINITION);
            lookup.setProductDefinitionId(medicationId);
            productLookups.add(lookup);
        }
        Map<String, ProductDefinitionDisplayDto> productByCompositeKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(productLookups);
        Map<String, BigDecimal> purchaseUnitCache = new HashMap<>();
        Map<String, BigDecimal> saleUnitCache = new HashMap<>();
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
            BigDecimal saleUnit = saleUnitByDispenseId.get(slip.getDispenseId());
            BigDecimal saleAmount = this.signedChargeSaleAmount(
                slip.getDispenseEnum(),
                saleTotalByDispenseId.get(slip.getDispenseId()));
            // 批号包装进价（历史回放）；非拆零单价
            BigDecimal packagePurchaseUnit =
                this.resolvePurchaseUnitFromLotPrice(slip, purchaseUnitCache, lotSession);
            ProductDefinitionDisplayDto product = null;
            if (slip.getMedicationId() != null) {
                String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                    TableNameConstants.MED_MEDICATION_DEFINITION, slip.getMedicationId());
                if (compositeKey != null) {
                    product = productByCompositeKey.get(compositeKey);
                }
            }
            BigDecimal packageUnitRatio = null;
            if (product != null) {
                packageUnitRatio = product.getPackageUnitRatio();
            }
            // 展示进价：与发药 unitCode 同阶（包装回包装价；拆零才折展示拆零价）
            BigDecimal displayPurchaseUnit =
                this.chargeItemPackageUnitRatioSupport.toAlignedPurchaseDisplayUnitPrice(
                    packagePurchaseUnit, packageUnitRatio, slip.getUnitCode(), product);
            // 账单仍缺售价：回落批号 LOT_NUMBER_PRICE（同批号调价回放）+ unitCode 分支行额
            if (saleAmount == null) {
                BigDecimal packageSaleUnit =
                    this.resolveSaleUnitFromLotPrice(slip, saleUnitCache, lotSession);
                if (packageSaleUnit != null) {
                    saleUnit = this.chargeItemPackageUnitRatioSupport.toAlignedPurchaseDisplayUnitPrice(
                        packageSaleUnit, packageUnitRatio, slip.getUnitCode(), product);
                    saleAmount = this.chargeItemPackageUnitRatioSupport.computePurchaseLineAmount(
                        packageSaleUnit,
                        packageUnitRatio,
                        signedQuantity,
                        slip.getUnitCode(),
                        product);
                }
            }
            // 单价可空：禁止 scaleCalc(null)→0 伪装「零售价」
            if (saleUnit != null) {
                line.setSaleUnitPrice(MonetaryAmountSupport.scaleCalc(saleUnit));
            }
            if (displayPurchaseUnit != null) {
                line.setPurchaseUnitPrice(MonetaryAmountSupport.scaleCalc(displayPurchaseUnit));
            }
            if (saleAmount != null) {
                line.setSaleAmount(MonetaryAmountSupport.scaleCalc(saleAmount));
                line.setUnitPrice(line.getSaleUnitPrice());
                line.setTotalAmount(line.getSaleAmount());
            }
            // 行进价：包装单位直接×数量；拆零走满包+零头；禁止包装价÷ratio 再×数量
            if (packagePurchaseUnit != null) {
                BigDecimal purchaseAmount =
                    this.chargeItemPackageUnitRatioSupport.computePurchaseLineAmount(
                        packagePurchaseUnit,
                        packageUnitRatio,
                        signedQuantity,
                        slip.getUnitCode(),
                        product);
                line.setPurchaseAmount(MonetaryAmountSupport.scaleCalc(purchaseAmount));
            }
            detailLines.add(line);
        }
        return detailLines;
    }

    /**
     * 按发放主键批量合并账单售价（单价/总额）；住院可含待结算态。
     *
     * @param dispenseIds           发放主键
     * @param inpatientMode         是否含 PENDING_SETTLEMENT
     * @param saleUnitByDispenseId  输出：dispenseId → 售价单价
     * @param saleTotalByDispenseId 输出：dispenseId → 售价总额（账单符号，退药行再由调用方取负）
     */
    private void mergeChargeSalePricingByDispenseIds(
        Collection<Long> dispenseIds,
        boolean inpatientMode,
        Map<Long, BigDecimal> saleUnitByDispenseId,
        Map<Long, BigDecimal> saleTotalByDispenseId) {
        if (dispenseIds == null || dispenseIds.isEmpty()) {
            return;
        }
        List<ChargeItem> chargeRows =
            this.chargeItemQueryService.listChargeItemsForMedicationDispensePricingByDispenseIds(
                dispenseIds, inpatientMode);
        if (chargeRows == null || chargeRows.isEmpty()) {
            return;
        }
        Map<Long, BigDecimal> totalByDispense = new HashMap<>();
        Map<Long, BigDecimal> qtyByDispense = new HashMap<>();
        for (ChargeItem chargeRow : chargeRows) {
            if (chargeRow == null || chargeRow.getDispenseId() == null) {
                continue;
            }
            Long dispenseKey = chargeRow.getDispenseId();
            if (chargeRow.getTotalPrice() != null) {
                BigDecimal existingTotal = totalByDispense.get(dispenseKey);
                if (existingTotal == null) {
                    totalByDispense.put(dispenseKey, chargeRow.getTotalPrice());
                } else {
                    totalByDispense.put(dispenseKey, existingTotal.add(chargeRow.getTotalPrice()));
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
            BigDecimal totalVal = entry.getValue();
            saleTotalByDispenseId.put(dispenseKey, totalVal);
            BigDecimal qtyVal = qtyByDispense.get(dispenseKey);
            if (qtyVal != null && qtyVal.compareTo(BigDecimal.ZERO) > 0) {
                saleUnitByDispenseId.put(
                    dispenseKey, MonetaryAmountSupport.unitPriceFromLineTotal(totalVal, qtyVal));
            }
        }
    }

    /**
     * 账单售价按发放类型取符号：退药行总额取绝对值后取负，与数量符号一致。
     *
     * @param dispenseEnum 发放类型
     * @param chargeTotal  账单总额（可空）
     * @return 带符号售价总额；入参空时 {@code null}
     */
    private BigDecimal signedChargeSaleAmount(MedicationDispenseType dispenseEnum, BigDecimal chargeTotal) {
        if (chargeTotal == null) {
            return null;
        }
        if (MedicationDispenseType.REFUND.equals(dispenseEnum)) {
            return chargeTotal.abs().negate();
        }
        return chargeTotal;
    }

    /**
     * 对仍缺售价的退药行，按原正向发放账单补单价/行额（写入映射后由调用方取负）。
     *
     * <p>退药 {@code MedicationDispense} 通常不挂自有 ChargeItem；仅按退药 {@code dispense_id}
     * 读价会得到空售价，大类/品规售价净额漏扣退费。与发退药记录补价同管线。</p>
     *
     * @param slipRows              发放 slip
     * @param saleUnitByDispenseId  输出：dispenseId → 售价单价（可写）
     * @param saleTotalByDispenseId 输出：dispenseId → 售价总额（可写；退药为正额绝对值，后续取负）
     */
    private void supplementMissingRefundSalePricing(
        List<MedicationDispenseSlipQueryModel> slipRows,
        Map<Long, BigDecimal> saleUnitByDispenseId,
        Map<Long, BigDecimal> saleTotalByDispenseId) {
        if (slipRows == null || slipRows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> medReqIdsNeedingSupplement = new LinkedHashSet<>();
        for (MedicationDispenseSlipQueryModel slip : slipRows) {
            if (slip == null || slip.getDispenseId() == null || slip.getMedReqId() == null) {
                continue;
            }
            if (!this.isRefundSlipRow(slip)) {
                continue;
            }
            // 0 元占位不视为已有售价，须补原正向账单
            BigDecimal existingSaleTotal = saleTotalByDispenseId.get(slip.getDispenseId());
            BigDecimal existingSaleUnit = saleUnitByDispenseId.get(slip.getDispenseId());
            boolean hasUsableSale = false;
            if (existingSaleUnit != null && existingSaleUnit.compareTo(BigDecimal.ZERO) != 0) {
                hasUsableSale = true;
            }
            if (existingSaleTotal != null && existingSaleTotal.compareTo(BigDecimal.ZERO) != 0) {
                hasUsableSale = true;
            }
            if (hasUsableSale) {
                continue;
            }
            medReqIdsNeedingSupplement.add(slip.getMedReqId());
        }
        if (medReqIdsNeedingSupplement.isEmpty()) {
            return;
        }
        // Pre：仅加载缺售价退药行的药嘱（含 refundMedicineId），禁止全窗扫药嘱
        Map<Long, MedicationRequestListQueryModel> medicationRequestByMedReqId = new HashMap<>();
        for (MedicationRequestListQueryModel request
            : this.medicationRequestQueryService.listMedicationRequestListModelsByIds(medReqIdsNeedingSupplement)) {
            if (request != null && request.getId() != null) {
                medicationRequestByMedReqId.put(request.getId(), request);
            }
        }
        this.pharmacyDispenseRecordChargePricingAssembler.supplementRefundPricingFromOriginMedicationRequests(
            slipRows,
            medicationRequestByMedReqId,
            saleUnitByDispenseId,
            saleTotalByDispenseId);
    }

    /**
     * 是否退药语义行（{@code dispenseEnum=REFUND} 或 {@code status=REFUNDED}）。
     *
     * @param slip 发放 slip
     * @return 退药行为 true
     */
    private boolean isRefundSlipRow(MedicationDispenseSlipQueryModel slip) {
        if (slip == null) {
            return false;
        }
        if (MedicationDispenseType.REFUND.equals(slip.getDispenseEnum())) {
            return true;
        }
        return DispenseStatus.REFUNDED.equals(slip.getStatusEnum());
    }

    /**
     * 批号包装进价单价：发药批号 {@code LOT_NUMBER_COST} + 发药时刻调价回放；无批号/无价则空（禁止 PURCHASE 参考价）。
     *
     * @param slip              发放事实
     * @param purchaseUnitCache 缓存
     * @param lotSession        调价预取会话
     * @return 包装单位进价单价；无法解析时 {@code null}
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
     * 批号包装售价单价：发药批号 {@code LOT_NUMBER_PRICE} + 发药时刻调价回放；无批号/无价则空（禁止 SALE 参考价）。
     *
     * <p>仅账单缺失回落路径调用（药房日结白名单）；有 {@code ChargeItem} 时不以本方法冒充账单实收。</p>
     *
     * @param slip          发放事实
     * @param saleUnitCache 缓存
     * @param lotSession    调价预取会话
     * @return 包装单位售价单价；无法解析时 {@code null}
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
            // Pre：仅批号售价；禁止回落 SALE；无调价不查历史（与进价同构）
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

        /**
         * 行级进/售（供弹窗明细复用，避免二次读价）；键 = dispenseId。
         */
        private final Map<Long, PurchaseSaleDetailLineAdapter> detailLineByDispenseId = new HashMap<>();
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
