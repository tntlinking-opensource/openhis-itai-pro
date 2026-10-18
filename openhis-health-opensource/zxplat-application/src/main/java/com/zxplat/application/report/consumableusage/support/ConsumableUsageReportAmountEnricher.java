/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.consumableusage.support;

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

import com.zxplat.application.shared.pricing.ChargeItemPackageUnitRatioSupport;
import com.zxplat.application.shared.pricing.HistoricalChargeItemPurchasePriceSupport;
import com.zxplat.application.shared.product.ChargeItemProductLookupHelper;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.share.constants.ConsumableUsageReportConstants;
import com.zxplat.domain.share.constants.DeptIssueReportConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.framework.core.application.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 耗材使用报表患者侧金额 enrich（BR-RPT-OCU-004 / BR-RPT-ICU-004；金额刻度 BR-MON-001；包装锚 BR-MON-PKG-001）。
 *
 * <p>患者侧：售价来自 ChargeItem.total_price；进价来自 {@link HistoricalChargeItemPurchasePriceSupport}
 *（批号 {@code LOT_NUMBER_COST}，缺则同批号 {@code LOT_NUMBER_PRICE}）；
 * 行进价合计走包装锚三参；展示进价单价与操作单位对齐（#4M-OP）。
 * 匹配：仅发放精确锚（本发放 / basedOn）；无命中保持 null 禁止假 0.00。禁止请求级兼容回挂。
 * 科室领用侧：仅 Supply 落库进价；售价字段一律置空（领用非销售，禁止目录 SALE / 批号售价）。</p>
 *
 * <p><strong>账批质检</strong>（仅明细分页 {@code checkLotMismatch=true}；不改正额）：
 * {@link #applyChargeDispenseLotMismatch} 比对收费锚点批号与实发批号；退耗经 {@code basedOnDispenseId}
 * 回挂源发放账批。汇总路径须传 {@code false}，避免全窗额外 definition IN。</p>
 *
 * <p>DOC-CONFLICT→已按 DD-报表中心-进售价口径与开立科室-2026-08 收敛。</p>
 */
@Component
@RequiredArgsConstructor
public class ConsumableUsageReportAmountEnricher {

    /** 收费明细读侧。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 历史进价读侧（禁目录参考价）。 */
    private final HistoricalChargeItemPurchasePriceSupport historicalChargeItemPurchasePriceSupport;

    /** 包装倍数与拆零展示单价。 */
    private final ChargeItemPackageUnitRatioSupport chargeItemPackageUnitRatioSupport;

    /** 产品展示（取 packageUnitRatio）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 患者发放行 {@code dispenseId → account_id}（与金额匹配同口径，供费用性质装配）。
     *
     * @param lines 待解析行
     * @return 发放主键 → 挂账账户；无匹配不含键
     */
    public Map<Long, Long> mapAccountIdByDispenseId(List<? extends PatientDispenseAmountLine> lines) {
        Map<Long, Long> accountByDispenseId = new HashMap<>();
        if (lines == null || lines.isEmpty()) {
            return accountByDispenseId;
        }
        List<PatientDispenseAmountLine> castLines = new ArrayList<>(lines.size());
        for (PatientDispenseAmountLine line : lines) {
            if (line != null) {
                castLines.add(line);
            }
        }
        List<ChargeItem> chargeItems = this.loadChargeItems(castLines);
        for (PatientDispenseAmountLine line : castLines) {
            if (line.getDispenseId() == null || accountByDispenseId.containsKey(line.getDispenseId())) {
                continue;
            }
            for (ChargeItem chargeItem : chargeItems) {
                if (!this.matches(line, chargeItem)) {
                    continue;
                }
                if (chargeItem.getAccountId() == null) {
                    continue;
                }
                accountByDispenseId.put(line.getDispenseId(), chargeItem.getAccountId());
                break;
            }
        }
        return accountByDispenseId;
    }

    /**
     * 回填患者发放明细行进价/售价（6 位行刻度；amount 保持售价别名）。
     *
     * <p>按发放/basedOn 精确锚匹配账单。无命中时售/进价保持 {@code null}（前端「—」），
     * 禁止 {@code scaleCalc} 编成假 0.00；禁止请求级兼容回挂。
     * 默认<strong>不做</strong>账批质检（汇总路径）；明细分页须传 {@code checkLotMismatch=true}。</p>
     *
     * @param lines 待 enrich 行；须已填 encounterId / dispenseId / deviceReqId / quantity
     */
    public void enrichPatientDispenseAmounts(List<PatientDispenseAmountLine> lines) {
        this.enrichPatientDispenseAmounts(lines, false);
    }

    /**
     * 回填患者发放明细行进价/售价；可选账批≠实发批质检。
     *
     * @param lines             待 enrich 行
     * @param checkLotMismatch  true=本页明细分页质检（额外一次 definition IN）；汇总须 false
     */
    public void enrichPatientDispenseAmounts(
        List<PatientDispenseAmountLine> lines,
        boolean checkLotMismatch) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        List<ChargeItem> chargeItems = this.loadChargeItems(lines);
        // Pre：包装进价单价一次解析
        Map<Long, BigDecimal> purchaseUnitByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseUnitPriceByChargeItemId(chargeItems);
        // Pre：产品 Map 一次 — 判操作单位 + 包装倍数
        LinkedHashSet<ProductDefinitionBatchLookup> lookups = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            ProductDefinitionBatchLookup lookup =
                ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeItem);
            if (lookup != null) {
                lookups.add(lookup);
            }
        }
        Map<String, ProductDefinitionDisplayDto> productByCompositeKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(new ArrayList<>(lookups));
        Map<Long, BigDecimal> packageUnitRatioByChargeItemId =
            this.chargeItemPackageUnitRatioSupport.mapPackageUnitRatioByChargeItemId(
                chargeItems, productByCompositeKey);
        // Pre：按操作单位计行额（包装×数量 / 拆零满包+零头）；禁止折拆零价再乘
        Map<Long, BigDecimal> purchaseByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseAmountFromUnitPrices(
                chargeItems,
                purchaseUnitByChargeItemId,
                packageUnitRatioByChargeItemId,
                productByCompositeKey);
        // Pre：跨行全局消费，避免同账单被多发放行各加一遍
        Set<Long> consumedChargeItemIds = new HashSet<>();
        // Asm：逐行精确锚匹配账单并写回进售价
        for (PatientDispenseAmountLine line : lines) {
            // Gate：空行跳过
            if (line == null) {
                continue;
            }
            List<ChargeItem> matched =
                this.collectMatchedCharges(line, chargeItems, consumedChargeItemIds);
            this.applyMatchedChargeAmounts(
                line,
                matched,
                chargeItems,
                purchaseByChargeItemId,
                purchaseUnitByChargeItemId,
                packageUnitRatioByChargeItemId,
                productByCompositeKey);
        }
        // Asm：仅明细分页打账批质检；汇总跳过以免全窗多一次 definition IN
        if (checkLotMismatch) {
            this.applyChargeDispenseLotMismatch(lines, chargeItems);
        }
    }

    /**
     * 回填科室领用明细行进价；售价一律置空。
     *
     * <p>进价取 Supply 落库价；展示单价与操作单位对齐（#4M-OP）。领用非销售，禁止编售价 / 目录 SALE。</p>
     *
     * @param line 已映射明细行；须含 deviceDefId / quantity / unitCode / 进价字段
     */
    public void enrichDeptIssueAmounts(PatientDispenseAmountLine line) {
        if (line == null) {
            return;
        }
        BigDecimal packagePurchaseUnit = MonetaryAmountSupport.scaleCalc(line.getPurchaseUnitPrice());
        ProductDefinitionDisplayDto product = this.resolveDeviceProductDisplay(line.getDeviceDefId());
        BigDecimal ratio = this.resolvePackageUnitRatio(product);
        BigDecimal purchaseUnit = this.chargeItemPackageUnitRatioSupport.toAlignedPurchaseDisplayUnitPrice(
            packagePurchaseUnit, ratio, line.getUnitCode(), product);
        BigDecimal purchaseAmount = MonetaryAmountSupport.scaleCalc(line.getPurchaseAmount());
        line.setPurchaseAmount(purchaseAmount);
        line.setPurchaseUnitPrice(purchaseUnit);
        // 领用侧不算售价
        line.setSaleUnitPrice(null);
        line.setSaleAmount(null);
        line.setAmount(null);
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
        this.aggregateSummaryPurchaseSaleAmounts(summaryRows, detailRows, dimension, null);
    }

    /**
     * 按汇总 dimensionKey 累加明细行进价/售价净额（CATEGORY 维须传品规→类型）。
     *
     * @param summaryRows                汇总行
     * @param detailRows                 已 enrich 明细行
     * @param dimension                  汇总维
     * @param categoryCodeByDeviceDefId  CATEGORY 维品规→类型；可空
     */
    public void aggregateSummaryPurchaseSaleAmounts(
        List<? extends ConsumableUsagePurchaseSaleSummaryLine> summaryRows,
        List<? extends ConsumableUsagePurchaseSaleDetailLine> detailRows,
        String dimension,
        Map<Long, String> categoryCodeByDeviceDefId) {
        if (summaryRows == null || summaryRows.isEmpty()) {
            return;
        }
        SummaryAmountBuckets buckets = new SummaryAmountBuckets();
        this.accumulateDetailRowsIntoSummaryBuckets(
            buckets, detailRows, dimension, categoryCodeByDeviceDefId);
        this.applySummaryAmountBuckets(summaryRows, buckets);
    }

    /**
     * 将一批已定价明细累加进汇总金额桶（可多次调用后一次 {@link #applySummaryAmountBuckets}）。
     *
     * @param buckets                   金额/展示单价累加桶
     * @param detailRows                本批已 enrich 明细
     * @param dimension                 汇总维
     * @param categoryCodeByDeviceDefId CATEGORY 维品规→类型
     */
    public void accumulateDetailRowsIntoSummaryBuckets(
        SummaryAmountBuckets buckets,
        List<? extends ConsumableUsagePurchaseSaleDetailLine> detailRows,
        String dimension,
        Map<Long, String> categoryCodeByDeviceDefId) {
        if (buckets == null || detailRows == null || detailRows.isEmpty()) {
            return;
        }
        for (ConsumableUsagePurchaseSaleDetailLine detailRow : detailRows) {
            if (detailRow == null) {
                continue;
            }
            String bucketKey =
                this.resolveDetailDimensionKey(detailRow, dimension, categoryCodeByDeviceDefId);
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
     * 将累加桶写回汇总行进/售净额与展示单价。
     *
     * @param summaryRows 汇总行
     * @param buckets     已累加金额桶
     */
    public void applySummaryAmountBuckets(
        List<? extends ConsumableUsagePurchaseSaleSummaryLine> summaryRows,
        SummaryAmountBuckets buckets) {
        if (summaryRows == null || summaryRows.isEmpty() || buckets == null) {
            return;
        }
        for (ConsumableUsagePurchaseSaleSummaryLine summaryRow : summaryRows) {
            if (summaryRow == null || summaryRow.getDimensionKey() == null) {
                continue;
            }
            String key = summaryRow.getDimensionKey();
            BigDecimal purchaseNet = this.sumThenSettleOrNull(buckets.purchaseAmountsByKey.get(key));
            BigDecimal saleNet = this.sumThenSettleOrNull(buckets.saleAmountsByKey.get(key));
            summaryRow.setPurchaseNetAmount(purchaseNet);
            summaryRow.setSaleNetAmount(saleNet);
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
     * 汇总页脚售价净额（仅累加非空行）。
     *
     * @param rows 汇总行
     * @return 结算刻度或 null
     */
    public BigDecimal sumSaleNetAmount(List<? extends ConsumableUsagePurchaseSaleSummaryLine> rows) {
        return this.sumSummaryAmountColumn(rows, false);
    }

    /**
     * 批量加载与患者发放行相关的 ChargeItem（仅发放锚点；禁止按请求兼容拉账单）。
     *
     * @param lines 明细行
     * @return 去重收费明细
     */
    private List<ChargeItem> loadChargeItems(List<PatientDispenseAmountLine> lines) {
        LinkedHashSet<Long> dispenseIds = new LinkedHashSet<>();
        for (PatientDispenseAmountLine line : lines) {
            if (line == null) {
                continue;
            }
            if (line.getDispenseId() != null) {
                dispenseIds.add(line.getDispenseId());
            }
            // Pre：退药行账单锚在源发放；须一并纳入 dispense_id IN
            if (line.getBasedOnDispenseId() != null) {
                dispenseIds.add(line.getBasedOnDispenseId());
            }
        }
        Map<Long, ChargeItem> chargeItemById = new HashMap<>();
        // Pre：无发放锚则不拉账单（禁止请求级兼容）
        if (dispenseIds.isEmpty()) {
            return new ArrayList<>();
        }
        // Pre：dispense_table 锚点一次 IN（报表汇总主路径）
        this.mergeChargeItems(
            chargeItemById,
            this.chargeItemQueryService.listChargeItemsForDeviceDispensePricingByDispenseIds(
                dispenseIds, true));
        // Pre：service 表锚定发放主键时仍需 service_id IN
        ChargeItemQueryCommand dispenseAsServiceCmd = new ChargeItemQueryCommand();
        dispenseAsServiceCmd.setServiceIds(dispenseIds);
        dispenseAsServiceCmd.setExcludeSupersededSettledLines(true);
        this.mergeChargeItems(
            chargeItemById,
            this.chargeItemQueryService.listChargeItemsByServiceIds(dispenseAsServiceCmd));
        return new ArrayList<>(this.chargeItemQueryService.filterOutSupersededSettledLinesGroupedByEncounter(
            new ArrayList<>(chargeItemById.values())));
    }

    /**
     * 合并收费明细到 id Map。
     *
     * @param chargeItemById 去重容器
     * @param chargeItems    待合并行
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
     * 收集本行命中的收费明细，并记入全局已消费集合。
     *
     * <p>精确锚（本发放 / basedOn）命中；禁止请求级兼容回挂。</p>
     *
     * @param line                   发放行
     * @param chargeItems            候选账单
     * @param consumedChargeItemIds  跨行已消费主键
     * @return 本行命中列表（顺序与候选一致）
     */
    private List<ChargeItem> collectMatchedCharges(
        PatientDispenseAmountLine line,
        List<ChargeItem> chargeItems,
        Set<Long> consumedChargeItemIds) {
        List<ChargeItem> matched = new ArrayList<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            if (consumedChargeItemIds.contains(chargeItem.getId())) {
                continue;
            }
            if (!this.matches(line, chargeItem)) {
                continue;
            }
            matched.add(chargeItem);
            consumedChargeItemIds.add(chargeItem.getId());
        }
        return matched;
    }

    /**
     * 将已命中收费明细的进售价写回发放行；无命中时保持 null（禁止假 0.00）。
     *
     * @param line                           发放行
     * @param matchedCharges                 本行命中账单（可空）
     * @param allChargeItems                 全量候选（展示进价单价扫描用）
     * @param purchaseByChargeItemId         行进价
     * @param purchaseUnitByChargeItemId     包装进价单价
     * @param packageUnitRatioByChargeItemId 包装倍数
     * @param productByCompositeKey          产品 Map
     */
    private void applyMatchedChargeAmounts(
        PatientDispenseAmountLine line,
        List<ChargeItem> matchedCharges,
        List<ChargeItem> allChargeItems,
        Map<Long, BigDecimal> purchaseByChargeItemId,
        Map<Long, BigDecimal> purchaseUnitByChargeItemId,
        Map<Long, BigDecimal> packageUnitRatioByChargeItemId,
        Map<String, ProductDefinitionDisplayDto> productByCompositeKey) {
        if (line == null) {
            return;
        }
        if (matchedCharges == null || matchedCharges.isEmpty()) {
            return;
        }
        BigDecimal saleTotal = BigDecimal.ZERO;
        BigDecimal purchaseTotal = BigDecimal.ZERO;
        boolean anyPurchase = false;
        for (ChargeItem chargeItem : matchedCharges) {
            saleTotal = saleTotal.add(this.saleAmountForLine(line, chargeItem));
            BigDecimal purchase = purchaseByChargeItemId.get(chargeItem.getId());
            if (purchase != null) {
                purchaseTotal = purchaseTotal.add(this.scaleAmountForRefundLine(line, chargeItem, purchase));
                anyPurchase = true;
            }
        }
        BigDecimal saleAmount = MonetaryAmountSupport.scaleCalc(saleTotal);
        line.setAmount(saleAmount);
        line.setSaleAmount(saleAmount);
        line.setSaleUnitPrice(this.resolveSaleUnitPrice(line, saleAmount));
        if (anyPurchase) {
            line.setPurchaseAmount(MonetaryAmountSupport.scaleCalc(purchaseTotal));
        }
        // 展示进价单价：优先在本行命中账单上取；与收费 quantity_unit 对齐
        BigDecimal purchaseUnit = this.resolvePurchaseDisplayUnitPrice(
            line,
            matchedCharges,
            purchaseUnitByChargeItemId,
            packageUnitRatioByChargeItemId,
            productByCompositeKey);
        if (purchaseUnit == null) {
            purchaseUnit = this.resolvePurchaseDisplayUnitPrice(
                line,
                allChargeItems,
                purchaseUnitByChargeItemId,
                packageUnitRatioByChargeItemId,
                productByCompositeKey);
        }
        line.setPurchaseUnitPrice(purchaseUnit);
    }

    /**
     * 行售价：账单总额；退药按数量折算。
     *
     * @param line       发放行
     * @param chargeItem 命中账单
     * @return 行售价（正数；退药符号由 Support 再取负）
     */
    private BigDecimal saleAmountForLine(PatientDispenseAmountLine line, ChargeItem chargeItem) {
        return this.scaleAmountForRefundLine(line, chargeItem, this.salePriceOf(chargeItem));
    }

    /**
     * 退药行按本行数量 / 账单数量线性折算金额；正向行原样返回。
     *
     * @param line       发放行
     * @param chargeItem 命中账单
     * @param amount     账单全额（进或售）
     * @return 折算后金额
     */
    private BigDecimal scaleAmountForRefundLine(
        PatientDispenseAmountLine line,
        ChargeItem chargeItem,
        BigDecimal amount) {
        if (amount == null || line == null || line.getBasedOnDispenseId() == null) {
            return MonetaryAmountSupport.nullToZero(amount);
        }
        BigDecimal billQty = chargeItem.getQuantityValue();
        BigDecimal lineQty = line.getQuantity();
        if (billQty == null || billQty.signum() == 0 || lineQty == null) {
            return amount;
        }
        BigDecimal absLineQty = lineQty.abs();
        if (absLineQty.compareTo(billQty) == 0) {
            return amount;
        }
        return MonetaryAmountSupport.proportionAtCalcScale(amount, absLineQty, billQty);
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
     * 解析与收费数量单位对齐的展示进价单价。
     *
     * @param line                           明细行
     * @param chargeItems                    已加载收费明细
     * @param purchaseUnitByChargeItemId     包装进价单价
     * @param packageUnitRatioByChargeItemId 包装倍数
     * @param productByCompositeKey          产品展示 Map
     * @return 展示进价单价；无法解析时 null
     */
    private BigDecimal resolvePurchaseDisplayUnitPrice(
        PatientDispenseAmountLine line,
        List<ChargeItem> chargeItems,
        Map<Long, BigDecimal> purchaseUnitByChargeItemId,
        Map<Long, BigDecimal> packageUnitRatioByChargeItemId,
        Map<String, ProductDefinitionDisplayDto> productByCompositeKey) {
        for (ChargeItem chargeItem : chargeItems) {
            if (!this.matches(line, chargeItem) || chargeItem.getId() == null) {
                continue;
            }
            BigDecimal packageUnit = purchaseUnitByChargeItemId.get(chargeItem.getId());
            if (packageUnit == null) {
                continue;
            }
            BigDecimal ratio = packageUnitRatioByChargeItemId.get(chargeItem.getId());
            ProductDefinitionDisplayDto product = null;
            ProductDefinitionBatchLookup lookup =
                ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeItem);
            if (lookup != null
                && productByCompositeKey != null
                && lookup.getProductTableName() != null
                && lookup.getProductDefinitionId() != null) {
                String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                    lookup.getProductTableName(), lookup.getProductDefinitionId());
                if (compositeKey != null) {
                    product = productByCompositeKey.get(compositeKey);
                }
            }
            return this.chargeItemPackageUnitRatioSupport.toChargeAlignedPurchaseDisplayUnitPrice(
                packageUnit, ratio, chargeItem, product);
        }
        return null;
    }

    /**
     * 解析耗材品规产品展示（领用侧操作单位对齐用）。
     *
     * @param deviceDefId 耗材定义主键
     * @return 产品展示；无法解析时 null
     */
    private ProductDefinitionDisplayDto resolveDeviceProductDisplay(Long deviceDefId) {
        if (deviceDefId == null) {
            return null;
        }
        ProductDefinitionBatchLookup lookup = new ProductDefinitionBatchLookup();
        lookup.setProductTableName(TableNameConstants.ADM_DEVICE_DEFINITION);
        lookup.setProductDefinitionId(deviceDefId);
        Map<String, ProductDefinitionDisplayDto> productByKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(List.of(lookup));
        String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
            TableNameConstants.ADM_DEVICE_DEFINITION, deviceDefId);
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
     * ChargeItem 是否锚定本发放行。
     *
     * <p>仅：① 本发放 {@code dispense_id}/{@code service} 锚；② 退药 {@code based_on} 源发放锚。
     * 禁止请求级兼容回挂（即使账单 {@code dispense_id} 为空）。</p>
     */
    private boolean matches(PatientDispenseAmountLine line, ChargeItem chargeItem) {
        if (this.matchesDeviceDispenseAnchor(line.getDispenseId(), chargeItem)) {
            return true;
        }
        // 退药行：账单仍挂源 COMPLETED 发放，经 based_on 回挂后再由调用方按数量符号取负
        if (this.matchesDeviceDispenseAnchor(line.getBasedOnDispenseId(), chargeItem)) {
            return true;
        }
        return false;
    }

    /**
     * 发放主键是否与 ChargeItem 的发放/服务锚一致。
     *
     * @param dispenseId 发放主键；空则否
     * @param chargeItem 收费明细
     * @return 是否命中
     */
    private boolean matchesDeviceDispenseAnchor(Long dispenseId, ChargeItem chargeItem) {
        if (dispenseId == null || chargeItem == null) {
            return false;
        }
        if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getServiceTable())
            && dispenseId.equals(chargeItem.getServiceId())) {
            return true;
        }
        if (TableNameConstants.WKF_DEVICE_DISPENSE.equals(chargeItem.getDispenseTable())
            && dispenseId.equals(chargeItem.getDispenseId())) {
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
     * 明细分页：比对收费锚点批号与发放批号；不一致仅打标，不改正额。
     *
     * <p>复用 {@link #enrichPatientDispenseAmounts} 已加载的 ChargeItem；额外仅一次
     * definitionId IN。科室领用不走本方法。退耗经 {@code basedOnDispenseId} 回挂源发放账批。
     * 任一侧无批号 → 不打 {@code lotMismatch}（禁止假阳性）。</p>
     *
     * @param lines       患者发放行
     * @param chargeItems 本页收费行
     */
    private void applyChargeDispenseLotMismatch(
        List<? extends PatientDispenseAmountLine> lines,
        List<ChargeItem> chargeItems) {
        if (lines == null || lines.isEmpty()) {
            return;
        }
        Map<Long, String> chargeLotByDispenseId =
            this.historicalChargeItemPurchasePriceSupport.mapChargeAnchorLotByDispenseId(chargeItems);
        // Gate：本页无账批可解析则整批跳过
        if (chargeLotByDispenseId.isEmpty()) {
            return;
        }
        // Asm：逐行比对实发批与账批；仅质检展示
        for (PatientDispenseAmountLine line : lines) {
            // Gate：空行跳过
            if (line == null) {
                continue;
            }
            // Gate：科室领用无 ChargeItem
            if (ConsumableUsageReportConstants.SOURCE_CODE_DEPT_ISSUE.equals(line.getSourceCode())) {
                continue;
            }
            String chargeLot = null;
            // Pre：优先本发放 dispenseId 取账批
            if (line.getDispenseId() != null) {
                chargeLot = chargeLotByDispenseId.get(line.getDispenseId());
            }
            // Pre：退耗账单锚在源发放，回挂 basedOn
            if (StringUtils.isEmpty(chargeLot) && line.getBasedOnDispenseId() != null) {
                chargeLot = chargeLotByDispenseId.get(line.getBasedOnDispenseId());
            }
            String dispenseLot = line.getLotNumber();
            // Gate：任一侧无批号则无法判定
            if (StringUtils.isEmpty(chargeLot) || StringUtils.isEmpty(dispenseLot)) {
                continue;
            }
            String dispenseLotTrimmed = dispenseLot.trim();
            line.setChargeLotNumber(chargeLot);
            // Asm：账批 ≠ 实发批 → 仅打标
            if (!chargeLot.equals(dispenseLotTrimmed)) {
                line.setLotMismatch(Boolean.TRUE);
            }
        }
    }

    /**
     * 明细行 → 汇总 dimensionKey。
     *
     * @param detailRow                  明细行
     * @param dimension                  汇总维
     * @param categoryCodeByDeviceDefId  CATEGORY 维品规→类型；可空
     * @return 分桶键
     */
    private String resolveDetailDimensionKey(
        ConsumableUsagePurchaseSaleDetailLine detailRow,
        String dimension,
        Map<Long, String> categoryCodeByDeviceDefId) {
        if (ConsumableUsageReportConstants.SOURCE_CODE_DEPT_ISSUE.equals(detailRow.getSourceCode())) {
            return this.resolveDeptDetailDimensionKey(detailRow, dimension, categoryCodeByDeviceDefId);
        }
        return this.resolvePatientDetailDimensionKey(detailRow, dimension, categoryCodeByDeviceDefId);
    }

    /**
     * 患者侧明细 → dimensionKey。
     *
     * @param detailRow                  明细行
     * @param dimension                  汇总维
     * @param categoryCodeByDeviceDefId  CATEGORY 维品规→类型；可空
     * @return 分桶键
     */
    private String resolvePatientDetailDimensionKey(
        ConsumableUsagePurchaseSaleDetailLine detailRow,
        String dimension,
        Map<Long, String> categoryCodeByDeviceDefId) {
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
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_CATEGORY.equals(dimension)) {
            return this.resolveCategoryDimensionKey(detailRow.getDeviceDefId(), categoryCodeByDeviceDefId);
        }
        Long patientId = detailRow.getPatientId();
        if (patientId == null) {
            return "PATIENT:";
        }
        return "PATIENT:" + patientId;
    }

    /**
     * 科室侧明细 → dimensionKey（LOCATION 维统一 {@code LOCATION:}，禁止仅认 PHARMACY）。
     *
     * @param detailRow                  明细行
     * @param dimension                  请求维（可为 LOCATION / PHARMACY）
     * @param categoryCodeByDeviceDefId  CATEGORY 维品规→类型；可空
     * @return 分桶键
     */
    private String resolveDeptDetailDimensionKey(
        ConsumableUsagePurchaseSaleDetailLine detailRow,
        String dimension,
        Map<Long, String> categoryCodeByDeviceDefId) {
        if (DeptIssueReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)
            || ConsumableUsageReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)) {
            Long deviceDefId = detailRow.getDeviceDefId();
            if (deviceDefId == null) {
                return "ITEM:";
            }
            return "ITEM:" + deviceDefId;
        }
        // 耗材房：请求 LOCATION 或领域 PHARMACY 一律对外 LOCATION: 前缀
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_LOCATION.equals(dimension)
            || DeptIssueReportConstants.SUMMARY_DIMENSION_PHARMACY.equals(dimension)) {
            Long locationId = detailRow.getLocationId();
            if (locationId == null) {
                return "LOCATION:";
            }
            return "LOCATION:" + locationId;
        }
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_CATEGORY.equals(dimension)) {
            return this.resolveCategoryDimensionKey(detailRow.getDeviceDefId(), categoryCodeByDeviceDefId);
        }
        Long orgId = detailRow.getOrganizationId();
        if (orgId == null) {
            return "DEPARTMENT:";
        }
        return "DEPARTMENT:" + orgId;
    }

    /**
     * CATEGORY 维分桶键：品规映射类型码；缺失落入空类型桶。
     *
     * @param deviceDefId                耗材定义主键
     * @param categoryCodeByDeviceDefId  品规→类型
     * @return {@code CATEGORY:} + code
     */
    private String resolveCategoryDimensionKey(
        Long deviceDefId,
        Map<Long, String> categoryCodeByDeviceDefId) {
        String categoryCode = "";
        if (deviceDefId != null && categoryCodeByDeviceDefId != null) {
            String mapped = categoryCodeByDeviceDefId.get(deviceDefId);
            if (mapped != null) {
                categoryCode = mapped;
            }
        }
        return "CATEGORY:" + categoryCode;
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

        /**
         * 退药源发放主键（{@code based_on_id}）；正向行返回 null。
         *
         * @return 源发放主键；可空
         */
        Long getBasedOnDispenseId();

        /** @return 耗材请求主键；可空 */
        Long getDeviceReqId();

        /** 写入匹配后的行售价别名 amount。 */
        void setAmount(BigDecimal amount);

        /**
         * 写入收费锚点批号（质检展示；可空）。
         *
         * @param chargeLotNumber 账批号
         */
        void setChargeLotNumber(String chargeLotNumber);

        /**
         * 写入账批≠实发批标记（仅 true 时前端标红；金额口径不变）。
         *
         * @param lotMismatch 是否不一致
         */
        void setLotMismatch(Boolean lotMismatch);
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

        /** @return 批号；科室领用侧读 {@code LOT_NUMBER_PRICE} 必填 */
        String getLotNumber();

        /** @return 数量 */
        BigDecimal getQuantity();

        /** @return 操作单位码 */
        String getUnitCode();

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

        /** @return 净数量 */
        BigDecimal getNetQuantity();

        /** @return 进价净额 */
        BigDecimal getPurchaseNetAmount();

        /** @return 售价净额 */
        BigDecimal getSaleNetAmount();

        /** @param purchaseNetAmount 进价净额 */
        void setPurchaseNetAmount(BigDecimal purchaseNetAmount);

        /** @param saleNetAmount 售价净额 */
        void setSaleNetAmount(BigDecimal saleNetAmount);

        /** @param purchaseUnitPrice 进价单价 */
        void setPurchaseUnitPrice(BigDecimal purchaseUnitPrice);

        /** @param saleUnitPrice 售价单价 */
        void setSaleUnitPrice(BigDecimal saleUnitPrice);
    }
}
