/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.report;

import com.openhis.health.application.shared.pricing.ChargeItemPackageUnitRatioSupport;
import com.openhis.health.application.shared.pricing.HistoricalChargeItemPurchasePriceSupport;
import com.openhis.health.application.shared.product.ChargeItemProductLookupHelper;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 费用明细命中 scope 进价合计（明细分页页脚；BR-PRICE-SRC-001 / BR-MON-PKG-001）。
 *
 * <p>仅批量解析历史进价后代数加总；<strong>禁止</strong>物化 RowQueryModel、禁止就诊块/产品展示 enrich。
 * 诊疗/挂号无进价（Map 缺键）按 0 计入，与行小计 {@code nullToZero} 同口径。</p>
 */
@Component
@RequiredArgsConstructor
public class FeeDetailMatchedScopePurchaseTotalSupport {

    /** 收费行装载（命中就诊 / 主键 IN）。 */
    private final ChargeItemQueryService chargeItemQueryService;

    /** 历史进价（批号 LOT_NUMBER_COST；禁目录 PURCHASE）。 */
    private final HistoricalChargeItemPurchasePriceSupport historicalChargeItemPurchasePriceSupport;

    /** 包装→拆零倍数（行进价包装锚）。 */
    private final ChargeItemPackageUnitRatioSupport chargeItemPackageUnitRatioSupport;

    /** 产品展示 Map（仅取换算比所需字段）。 */
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;

    /**
     * 对明细分页命中 scope 求进价合计（结算刻度 2 位）。
     *
     * <p>装载策略：优先 {@code resolvedChargeItemScope} 若其覆盖命中全集（硬筛路径由调用方写入命中 id）；
     * 否则按 {@code resolvedFeeDetailMatchedEncounterIds} + 日窗 IN 装载。无命中返回 0。</p>
     *
     * @param query 已 prepare*PageScopes 的读查询
     * @return 进价合计（settle）；无命中为 0
     */
    public BigDecimal sumPurchaseAmountForMatchedScope(OutpatientFeeDetailPageQuery query) {
        List<ChargeItem> chargeItems = this.loadMatchedScopeChargeItems(query);
        if (chargeItems.isEmpty()) {
            return MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO);
        }
        LinkedHashSet<ProductDefinitionBatchLookup> productLookups = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null) {
                continue;
            }
            ProductDefinitionBatchLookup lookup =
                ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeItem);
            if (lookup != null) {
                productLookups.add(lookup);
            }
        }
        Map<String, ProductDefinitionDisplayDto> productByCompositeKey =
            this.productDefinitionDisplayTerminologyAssembler.loadAsMap(new ArrayList<>(productLookups));
        Map<Long, BigDecimal> packageUnitRatioByChargeItemId =
            this.chargeItemPackageUnitRatioSupport.mapPackageUnitRatioByChargeItemId(
                chargeItems, productByCompositeKey);
        // Pre：单价一次再行额，禁止同请求双调定价 IO
        Map<Long, BigDecimal> purchaseUnitByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseUnitPriceByChargeItemId(chargeItems);
        // 必须传 product Map：包装操作单位禁止走「假定拆零量」兼容分支（#4M-OP）
        Map<Long, BigDecimal> purchaseAmountByChargeItemId =
            this.historicalChargeItemPurchasePriceSupport.computePurchaseAmountFromUnitPrices(
                chargeItems,
                purchaseUnitByChargeItemId,
                packageUnitRatioByChargeItemId,
                productByCompositeKey);
        BigDecimal purchaseSum = BigDecimal.ZERO;
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            BigDecimal purchaseRaw = purchaseAmountByChargeItemId.get(chargeItem.getId());
            // 诊疗/挂号无进价：按 0 计入页脚（与就诊小计 nullToZero 一致）
            if (purchaseRaw == null) {
                continue;
            }
            purchaseSum = purchaseSum.add(purchaseRaw);
        }
        return MonetaryAmountSupport.scaleSettle(purchaseSum);
    }

    /**
     * 装载命中 scope 内收费行（仅实体，不做展示 enrich）。
     *
     * @param query 明细分页 Query
     * @return 收费行列表；无命中为空列表
     */
    private List<ChargeItem> loadMatchedScopeChargeItems(OutpatientFeeDetailPageQuery query) {
        if (query == null || query.getBillDateFrom() == null || query.getBillDateTo() == null) {
            return List.of();
        }
        List<Long> matchedChargeItemIds = query.getResolvedFeeDetailMatchedChargeItemIds();
        // Pre：硬筛路径已收集命中 chargeItemId 全集 → 按 id IN 装载
        if (matchedChargeItemIds != null && !matchedChargeItemIds.isEmpty()) {
            ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
            loadCmd.setIds(new ArrayList<>(matchedChargeItemIds));
            List<ChargeItem> byIds = this.chargeItemQueryService.loadChargeItemsByIds(loadCmd);
            if (byIds == null) {
                return List.of();
            }
            return byIds;
        }
        List<Long> matchedEncounterIds = query.getResolvedFeeDetailMatchedEncounterIds();
        if (matchedEncounterIds == null || matchedEncounterIds.isEmpty()) {
            return List.of();
        }
        LocalDateTime enteredFrom = query.getBillDateFrom().atStartOfDay();
        LocalDateTime enteredToExclusive = query.getBillDateTo().plusDays(1).atStartOfDay();
        List<String> statusCodes = query.getAllowedChargeItemStatusCodes();
        if (statusCodes == null || statusCodes.isEmpty()) {
            statusCodes = List.of(ChargeItemStatus.SETTLED.getCode());
        }
        List<ChargeItem> byEncounter =
            this.chargeItemQueryService.listChargeItemsByEncounterIdsInEnteredDateWindow(
                matchedEncounterIds,
                enteredFrom,
                enteredToExclusive,
                statusCodes,
                query.getEntererMatchIds(),
                query.getRequestingOrgId());
        if (byEncounter == null) {
            return List.of();
        }
        return byEncounter;
    }
}
