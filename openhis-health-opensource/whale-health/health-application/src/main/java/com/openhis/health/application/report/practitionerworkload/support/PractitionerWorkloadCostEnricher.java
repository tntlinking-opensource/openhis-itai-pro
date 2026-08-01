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

import com.openhis.health.application.shared.pricing.HistoricalChargeItemPurchasePriceSupport;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.PractitionerWorkloadPageQuery;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailChargePaymentLinkQueryModel;
import com.openhis.health.domain.share.constants.PractitionerWorkloadConstants;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.whale.core.application.exception.BusinessException;

import lombok.RequiredArgsConstructor;

/**
 * 科室/医生工作量行成本 enrich（BR-RPT-WKL-010；只读，B.6.1 querysupport）。
 *
 * <p>进价/成本统一走 {@link HistoricalChargeItemPurchasePriceSupport}：无调价品规直接 ACTIVE 批号进价/PURCHASE，
 * 有调价才回放调前价；<strong>禁止</strong>{@code listHistoricalDefinitionsByInstance} 无脑全量兄弟扫描。</p>
 */
@Component
@RequiredArgsConstructor
public class PractitionerWorkloadCostEnricher {

    /** 收费明细读侧。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 历史进价解析（无调价跳过 history；有调价批量预取）。 */
    private final HistoricalChargeItemPurchasePriceSupport historicalChargeItemPurchasePriceSupport;

    /**
     * 在 payment 预解析 scope 内批量计算 chargeItem 行成本。
     *
     * @param query               须含 {@link PractitionerWorkloadPageQuery#getResolvedPaymentChargeItemLinks()}
     * @param scopedChargeItemIds 当前筛选（科室/医生/就诊）命中后的 chargeItem 主键；空集合时直接返回空 map
     * @return chargeItemId → 行成本（6 位 HALF_UP；退费行由调用方按 payment 符号冲减）
     * @throws BusinessException 成本锚点无法解析
     */
    public Map<Long, BigDecimal> computeCostByChargeItemScope(
        PractitionerWorkloadPageQuery query,
        Collection<Long> scopedChargeItemIds) {
        Map<Long, BigDecimal> costByChargeItemId = new LinkedHashMap<>();
        if (query == null) {
            return costByChargeItemId;
        }
        LinkedHashSet<Long> allowedChargeItemIds = this.normalizeChargeItemIdScope(scopedChargeItemIds);
        if (allowedChargeItemIds.isEmpty()) {
            return costByChargeItemId;
        }
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentLinks = query.getResolvedPaymentChargeItemLinks();
        if (paymentLinks == null || paymentLinks.isEmpty()) {
            return costByChargeItemId;
        }
        LinkedHashSet<Long> chargeItemIds = new LinkedHashSet<>();
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link == null || link.getChargeItemId() == null) {
                continue;
            }
            // 仅计算当前筛选 scope 内 chargeItem，避免其它医生脏数据拖垮整单（BR-RPT-WKL-005）
            if (!allowedChargeItemIds.contains(link.getChargeItemId())) {
                continue;
            }
            chargeItemIds.add(link.getChargeItemId());
        }
        if (chargeItemIds.isEmpty()) {
            return costByChargeItemId;
        }
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(new ArrayList<>(chargeItemIds));
        List<ChargeItem> chargeItems = this.chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        // Pre：单价一次（内部：无调价直读批号/PURCHASE；有调价批量预取 history）；行额由单价×数量
        Map<Long, BigDecimal> purchaseUnitByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseUnitPriceByChargeItemId(chargeItems);
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            BigDecimal unit = purchaseUnitByChargeItemId.get(chargeItem.getId());
            if (unit == null) {
                throw new BusinessException(
                    PractitionerWorkloadConstants.EX_RPT_WKL_103,
                    "统计数据不完整，请联系管理员核对收费项目配置");
            }
            BigDecimal quantity = chargeItem.getQuantityValue();
            if (quantity == null) {
                // 无数量按 1 计行成本
                quantity = BigDecimal.ONE;
            }
            costByChargeItemId.put(chargeItem.getId(), MonetaryAmountSupport.lineTotal(unit, quantity));
        }
        return costByChargeItemId;
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
