/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.practitionerworkload.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.openhis.health.application.shared.pricing.ChargeItemPackageUnitRatioSupport;
import com.openhis.health.application.shared.pricing.HistoricalChargeItemPurchasePriceSupport;
import com.openhis.health.application.shared.product.ChargeItemProductLookupHelper;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadPageQuery;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailChargePaymentLinkQueryModel;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;

import lombok.RequiredArgsConstructor;

/**
 * 科室/医生工作量行进价 enrich（DD-报表中心-进售价口径；只读，B.6.1 querysupport）。
 *
 * <p>进价走 {@link HistoricalChargeItemPurchasePriceSupport}（批号进价；禁目录参考价）；
 * 行额走包装锚三参 {@code computePurchaseAmountFromUnitPrices}（BR-MON-PKG-001）。
 * 诊疗/挂号无进价则跳过（不抛、不编 0）；汇总缺键按未累加处理。</p>
 */
@Component
@RequiredArgsConstructor
public class PractitionerWorkloadCostEnricher {

    /** 收费明细读侧。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 历史进价解析（无调价跳过 history；有调价批量预取）。 */
    private final HistoricalChargeItemPurchasePriceSupport historicalChargeItemPurchasePriceSupport;

    /** 包装换算比（行进价合计与展示折算）。 */
    private final ChargeItemPackageUnitRatioSupport chargeItemPackageUnitRatioSupport;

    /** 产品展示批量加载（取 packageUnitRatio）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 在账单 scope 内批量计算 chargeItem 行进价合计。
     *
     * @param query               须含 {@link PractitionerWorkloadPageQuery#getResolvedChargeItemScope()}
     *                            或已合成的 resolvedPaymentChargeItemLinks（ChargeItem-only 同构）
     * @param scopedChargeItemIds 当前筛选命中后的 chargeItem 主键；空集合时直接返回空 map
     * @return chargeItemId → 行进价（6 位 HALF_UP；退费行由调用方按符号冲减；诊疗无进价则无键）
     */
    public Map<Long, BigDecimal> computeCostByChargeItemScope(
        PractitionerWorkloadPageQuery query,
        Collection<Long> scopedChargeItemIds) {
        Map<Long, BigDecimal> purchaseByChargeItemId = new LinkedHashMap<>();
        if (query == null) {
            return purchaseByChargeItemId;
        }
        LinkedHashSet<Long> allowedChargeItemIds = this.normalizeChargeItemIdScope(scopedChargeItemIds);
        if (allowedChargeItemIds.isEmpty()) {
            return purchaseByChargeItemId;
        }
        // Pre：优先 resolvedChargeItemScope；回落合成 links（与费用明细同构）
        LinkedHashSet<Long> chargeItemIds = new LinkedHashSet<>();
        List<Long> resolvedScope = query.getResolvedChargeItemScope();
        if (resolvedScope != null) {
            for (Long chargeItemId : resolvedScope) {
                if (chargeItemId != null && allowedChargeItemIds.contains(chargeItemId)) {
                    chargeItemIds.add(chargeItemId);
                }
            }
        }
        if (chargeItemIds.isEmpty() && query.getResolvedPaymentChargeItemLinks() != null) {
            for (OutpatientFeeDetailChargePaymentLinkQueryModel link : query.getResolvedPaymentChargeItemLinks()) {
                if (link == null || link.getChargeItemId() == null) {
                    continue;
                }
                // 仅计算当前筛选 scope 内 chargeItem，避免其它医生脏数据拖垮整单
                if (!allowedChargeItemIds.contains(link.getChargeItemId())) {
                    continue;
                }
                chargeItemIds.add(link.getChargeItemId());
            }
        }
        if (chargeItemIds.isEmpty()) {
            return purchaseByChargeItemId;
        }
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(new ArrayList<>(chargeItemIds));
        List<ChargeItem> chargeItems = this.chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        // Pre：包装进价单价一次；诊疗无进价则无键（不抛）
        Map<Long, BigDecimal> purchaseUnitByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseUnitPriceByChargeItemId(chargeItems);
        // Pre：产品 Map → 判操作单位 + 包装倍数；按单位计行额（BR-MON-PKG-001）
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
        return this.historicalChargeItemPurchasePriceSupport.computePurchaseAmountFromUnitPrices(
            chargeItems,
            purchaseUnitByChargeItemId,
            packageUnitRatioByChargeItemId,
            productByCompositeKey);
    }

    /**
     * 归一化 chargeItem 主键 scope；null/空元素忽略。
     *
     * @param scopedChargeItemIds 调用方 scope
     * @return 去重非空 id 集
     */
    private LinkedHashSet<Long> normalizeChargeItemIdScope(Collection<Long> scopedChargeItemIds) {
        LinkedHashSet<Long> allowedChargeItemIds = new LinkedHashSet<>();
        if (scopedChargeItemIds == null) {
            return allowedChargeItemIds;
        }
        for (Long chargeItemId : scopedChargeItemIds) {
            if (chargeItemId != null) {
                allowedChargeItemIds.add(chargeItemId);
            }
        }
        return allowedChargeItemIds;
    }
}
