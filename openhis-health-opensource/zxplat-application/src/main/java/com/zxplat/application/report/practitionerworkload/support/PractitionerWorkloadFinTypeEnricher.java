/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.practitionerworkload.support;

import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.chargeitem.query.PractitionerWorkloadPageQuery;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailChargePaymentLinkQueryModel;
import com.zxplat.domain.share.constants.PractitionerWorkloadConstants;
import com.zxplat.application.shared.financial.DailySettlementFinTypeAmountLoader;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * 科室/医生工作量 fin_type 历史定价行解析（BR-RPT-WKL-007；只读 enrich，B.6.1 querysupport）。
 *
 * <p>与日结口径一致：委托 {@link DailySettlementFinTypeAmountLoader#mapFinTypeCodeByChargeItemId}；
 * 仅锚 {@code definition_id} 历史定价行；挂号费强制诊察费 {@code 1012}；禁止回落当前目录 ACTIVE 价。</p>
 */
@Component
@RequiredArgsConstructor
public class PractitionerWorkloadFinTypeEnricher {

    private final ChargeItemQueryService chargeItemQueryService;

    private final DailySettlementFinTypeAmountLoader dailySettlementFinTypeAmountLoader;

    /**
     * 批量解析定价锚点 → fin_type_code（与日结、费用明细细分类口径一致）。
     *
     * @param query               须含 {@link PractitionerWorkloadPageQuery#getResolvedPaymentChargeItemLinks()}
     * @param definitionIds       汇总行涉及的 definition 主键集合
     * @param scopedChargeItemIds 当前筛选 scope 内 chargeItem 主键
     * @return definitionId → fin_type_code
     * @throws BusinessException 任一定价锚点无法解析（EX-RPT-WKL-103）
     */
    public Map<Long, String> resolveFinTypeCodeByDefinitionId(
        PractitionerWorkloadPageQuery query,
        Collection<Long> definitionIds,
        Collection<Long> scopedChargeItemIds) {
        Map<Long, String> finTypeByDefinitionId = new LinkedHashMap<>();
        if (definitionIds == null || definitionIds.isEmpty()) {
            return finTypeByDefinitionId;
        }
        LinkedHashSet<Long> targetDefinitionIds = new LinkedHashSet<>();
        for (Long definitionId : definitionIds) {
            if (definitionId != null) {
                targetDefinitionIds.add(definitionId);
            }
        }
        if (targetDefinitionIds.isEmpty()) {
            return finTypeByDefinitionId;
        }
        List<ChargeItem> chargeItems =
            this.loadChargeItemsInPaymentScope(query, targetDefinitionIds, scopedChargeItemIds);
        // Pre：按 chargeItem 解析 fin_type（含挂号强制 1012，BR-RPT-WKL-007）
        Map<Long, String> finTypeByChargeItemId =
            this.dailySettlementFinTypeAmountLoader.mapFinTypeCodeByChargeItemId(chargeItems);
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null || chargeItem.getDefinitionId() == null) {
                continue;
            }
            String finTypeCode = finTypeByChargeItemId.get(chargeItem.getId());
            if (finTypeCode != null) {
                finTypeByDefinitionId.put(chargeItem.getDefinitionId(), finTypeCode);
            }
        }
        for (Long definitionId : targetDefinitionIds) {
            if (!finTypeByDefinitionId.containsKey(definitionId)) {
                // 汇总行存在 definition 但 scope 内无法解析 fin_type → 整单失败
                throw new BusinessException(
                    PractitionerWorkloadConstants.EX_RPT_WKL_103,
                    "统计数据不完整，请联系管理员核对收费项目配置");
            }
        }
        return finTypeByDefinitionId;
    }

    /**
     * 在 payment scope 内加载命中目标 definition 的 chargeItem 事实行。
     *
     * @param query                预解析后的读查询
     * @param targetDefinitionIds  待解析 fin_type 的 definition 集合
     * @param scopedChargeItemIds  当前筛选 scope 内 chargeItem 主键
     * @return chargeItem 列表；无命中时返回空列表
     */
    private List<ChargeItem> loadChargeItemsInPaymentScope(
        PractitionerWorkloadPageQuery query,
        LinkedHashSet<Long> targetDefinitionIds,
        Collection<Long> scopedChargeItemIds) {
        List<ChargeItem> result = new ArrayList<>();
        if (query == null || targetDefinitionIds.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> allowedChargeItemIds = new LinkedHashSet<>();
        if (scopedChargeItemIds != null) {
            for (Long chargeItemId : scopedChargeItemIds) {
                if (chargeItemId != null) {
                    allowedChargeItemIds.add(chargeItemId);
                }
            }
        }
        if (allowedChargeItemIds.isEmpty()) {
            return result;
        }
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentLinks = query.getResolvedPaymentChargeItemLinks();
        if (paymentLinks == null || paymentLinks.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> chargeItemIds = new LinkedHashSet<>();
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link == null || link.getChargeItemId() == null) {
                continue;
            }
            if (!allowedChargeItemIds.contains(link.getChargeItemId())) {
                continue;
            }
            chargeItemIds.add(link.getChargeItemId());
        }
        if (chargeItemIds.isEmpty()) {
            return result;
        }
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(new ArrayList<>(chargeItemIds));
        List<ChargeItem> loaded = this.chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        for (ChargeItem chargeItem : loaded) {
            if (chargeItem == null || chargeItem.getDefinitionId() == null) {
                continue;
            }
            if (targetDefinitionIds.contains(chargeItem.getDefinitionId())) {
                result.add(chargeItem);
            }
        }
        return result;
    }
}
