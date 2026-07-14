/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.feedetail.support;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailChargePaymentLinkQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentScopeQueryModel;
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.constants.OutpatientFeeDetailConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.application.shared.financial.DailySettlementFinTypeAmountLoader;
import com.openhis.health.application.shared.product.ChargeItemProductLookupHelper;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 门诊费用明细读路径 scope 预解析（B.6.1 跨 payment / encounter / chargeitem 编排；AS-OP-FIN-005）。
 *
 * <p>本类只读、不写库；payment 筛选委托 Payment 聚合 QueryService，ChargeItem 批量读委托 ADM-011 QueryService，
 * 产品展示信息经 {@link ProductDefinitionDisplayTerminologyAssembler} 批量加载（禁止跨聚合 Repository）。</p>
 * <p>细分类筛选与展示统一取定价行 {@code type_code}（fin_type_code），与日结交账口径一致。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientFeeDetailLoader {

    private final PaymentReconciliationQueryService paymentReconciliationQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final EncounterQueryService encounterQueryService;
    private final PatientQueryService patientQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final DailySettlementFinTypeAmountLoader dailySettlementFinTypeAmountLoader;

    /**
     * 按 AS-OP-FDT 筛选条件预解析 {@code resolvedChargeItemScope}、{@code resolvedEncounterScope} 与 payment 上下文。
     *
     * <p>须在调用 {@link ChargeItemQueryService#pageOutpatientFeeDetailEncounterBlocks} 之前执行；
     * payment 无命中时写入空 scope，避免 ChargeItem 侧全表扫描。</p>
     *
     * @param query 须含 {@code billDateFrom}/{@code billDateTo}；本方法写入 scope、{@code paymentChargeItemLinks} 与 {@code paymentContextByChargeItemId}
     * @throws IllegalArgumentException {@code query} 为空
     */
    public void prepareOutpatientFeeDetailQueryScopes(OutpatientFeeDetailPageQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("outpatient fee detail query is required");
        }
        // Pre：先按 payment bill_date/收费人/收退 Tab 下推，得到 chargeItem 候选与 payment 读上下文
        this.paymentReconciliationQueryService.listOutpatientFeeDetailChargeItemIdsByPaymentFilter(query);
        // Pre：varchar(255) 快照截断时，补全同笔结算漏挂的已结/已退 chargeItem
        this.augmentTruncatedSnapshotChargeItems(query);
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentChargeItemLinks = query.getPaymentChargeItemLinks();
        if (paymentChargeItemLinks == null || paymentChargeItemLinks.isEmpty()) {
            // payment 无命中时短路，后续 ChargeItem 分页直接返回空页
            query.setResolvedChargeItemScope(List.of());
            query.setResolvedEncounterScope(List.of());
            query.setResolvedPaymentChargeItemLinks(List.of());
            return;
        }
        LinkedHashSet<Long> uniqueChargeItemIds = new LinkedHashSet<>();
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentChargeItemLinks) {
            if (link != null && link.getChargeItemId() != null) {
                uniqueChargeItemIds.add(link.getChargeItemId());
            }
        }
        if (uniqueChargeItemIds.isEmpty()) {
            query.setResolvedChargeItemScope(List.of());
            query.setResolvedEncounterScope(List.of());
            query.setResolvedPaymentChargeItemLinks(List.of());
            return;
        }
        // Pre：在 payment scope 内再收窄挂号科室/患者/项目/细分类，写入 resolved scope 供 ChargeItem 分页读
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(new ArrayList<>(uniqueChargeItemIds));
        List<ChargeItem> chargeItems = this.chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        Map<Long, ChargeItem> chargeItemById = new LinkedHashMap<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem != null && chargeItem.getId() != null) {
                chargeItemById.put(chargeItem.getId(), chargeItem);
            }
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        // 收集 payment scope 内涉及的就诊，供科室/患者二次收窄
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem != null && chargeItem.getEncounterId() != null) {
                encounterIds.add(chargeItem.getEncounterId());
            }
        }
        Set<Long> allowedEncounterIds = this.resolveAllowedEncounterIds(query, encounterIds);
        Map<String, ProductDefinitionDisplayDto> productMap =
            this.loadProductMapForChargeItems(chargeItems);
        Map<Long, String> finTypeByChargeItemId =
            this.dailySettlementFinTypeAmountLoader.mapFinTypeCodeByChargeItemId(chargeItems);
        LinkedHashSet<Long> filteredChargeItemIds = new LinkedHashSet<>();
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> filteredLinks = new ArrayList<>();
        // 逐条 payment 快照关联套用 BR-OP-FDT-002/007/008：状态、就诊、项目名 AND 编码、细分类
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentChargeItemLinks) {
            if (link == null || link.getChargeItemId() == null) {
                continue;
            }
            ChargeItem chargeItem = chargeItemById.get(link.getChargeItemId());
            if (chargeItem == null) {
                continue;
            }
            if (!this.isOutpatientFeeDetailChargeStatus(chargeItem.getStatusEnum())) {
                // 排除待结算/过程态，仅保留已结/已退
                continue;
            }
            if (chargeItem.getEncounterId() == null || !allowedEncounterIds.contains(chargeItem.getEncounterId())) {
                // 挂号科室或患者关键字未命中时剔除
                continue;
            }
            if (!this.matchesProductKeywords(chargeItem, productMap, query)) {
                continue;
            }
            if (!this.matchesSubCategoryCodes(chargeItem, finTypeByChargeItemId, query.getSubCategoryCodes())) {
                continue;
            }
            filteredChargeItemIds.add(chargeItem.getId());
            filteredLinks.add(link);
        }
        query.setResolvedChargeItemScope(new ArrayList<>(filteredChargeItemIds));
        query.setResolvedEncounterScope(new ArrayList<>(allowedEncounterIds));
        query.setResolvedPaymentChargeItemLinks(filteredLinks);
    }

    /**
     * 在 payment 候选就诊集合上叠加挂号科室与患者关键字收窄（BR-OP-FDT-004、005）。
     */
    private Set<Long> resolveAllowedEncounterIds(OutpatientFeeDetailPageQuery query, Set<Long> candidateEncounterIds) {
        if (candidateEncounterIds == null || candidateEncounterIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> allowed = new HashSet<>(candidateEncounterIds);
        if (query.getRegistrationDepartmentId() != null) {
            // 科室筛选 = 就诊挂号科室，非 ChargeItem 开单科室
            List<Long> deptEncounterIds =
                this.encounterQueryService.listOutpatientAmbEncounterIdsByOrganization(query.getRegistrationDepartmentId());
            allowed.retainAll(new HashSet<>(deptEncounterIds));
        }
        if (StringUtils.isEmpty(query.getPatientKeyword())) {
            return allowed;
        }
        String keyword = query.getPatientKeyword().trim();
        List<Long> patientIds = this.patientQueryService.listPatientIdsByEncounterListKeyword(keyword);
        Set<Long> patientIdSet = new HashSet<>(patientIds);
        List<Encounter> encounters = this.encounterQueryService.listEncounterEntitiesByIds(allowed);
        Set<Long> matchedEncounterIds = new HashSet<>();
        // 患者关键字：门诊号 busNo 或姓名命中其一即可
        for (Encounter encounter : encounters) {
            if (encounter == null || encounter.getId() == null) {
                continue;
            }
            if (encounter.getBusNo() != null
                && encounter.getBusNo().toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) {
                matchedEncounterIds.add(encounter.getId());
                continue;
            }
            if (encounter.getPatientId() != null && patientIdSet.contains(encounter.getPatientId())) {
                matchedEncounterIds.add(encounter.getId());
            }
        }
        return matchedEncounterIds;
    }

    /**
     * 为 scope 内 ChargeItem 批量加载产品定义 Map（复合键与 RM 一致）。
     */
    private Map<String, ProductDefinitionDisplayDto> loadProductMapForChargeItems(List<ChargeItem> chargeItems) {
        LinkedHashSet<String> dedupeKeys = new LinkedHashSet<>();
        List<ProductDefinitionBatchLookup> lookups = new ArrayList<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null) {
                continue;
            }
            ProductDefinitionBatchLookup lookup = ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeItem);
            if (lookup == null) {
                continue;
            }
            String compositeKey = ProductDefinitionKeyUtil.buildCompositeKey(
                lookup.getProductTableName(),
                lookup.getProductDefinitionId());
            if (compositeKey == null || !dedupeKeys.add(compositeKey)) {
                continue;
            }
            lookups.add(lookup);
        }
        if (lookups.isEmpty()) {
            return Map.of();
        }
        return this.productDefinitionDisplayTerminologyAssembler.loadAsMap(lookups);
    }

    /**
     * 项目名称与院内编码筛选（BR-OP-FDT-007：双填 AND，单填单条件）。
     */
    private boolean matchesProductKeywords(
        ChargeItem chargeItem,
        Map<String, ProductDefinitionDisplayDto> productMap,
        OutpatientFeeDetailPageQuery query) {
        boolean hasName = StringUtils.isNotEmpty(query.getProductNameKeyword());
        boolean hasCode = StringUtils.isNotEmpty(query.getProductCodeKeyword());
        if (!hasName && !hasCode) {
            return true;
        }
        ProductDefinitionBatchLookup lookup = ChargeItemProductLookupHelper.buildLookupFromChargeItem(chargeItem);
        if (lookup == null) {
            // 缺 product 锚点无法满足项目筛选
            return false;
        }
        String key = ProductDefinitionKeyUtil.buildCompositeKey(
            lookup.getProductTableName(),
            lookup.getProductDefinitionId());
        ProductDefinitionDisplayDto product = productMap.get(key);
        if (product == null) {
            return false;
        }
        boolean nameMatched = true;
        boolean codeMatched = true;
        if (hasName) {
            String nameKeyword = query.getProductNameKeyword().trim().toLowerCase(Locale.ROOT);
            nameMatched = product.getProductName() != null
                && product.getProductName().toLowerCase(Locale.ROOT).contains(nameKeyword);
        }
        if (hasCode) {
            String codeKeyword = query.getProductCodeKeyword().trim().toLowerCase(Locale.ROOT);
            codeMatched = product.getProductBusNo() != null
                && product.getProductBusNo().toLowerCase(Locale.ROOT).contains(codeKeyword);
        }
        if (hasName && hasCode) {
            return nameMatched && codeMatched;
        }
        if (hasName) {
            return nameMatched;
        }
        return codeMatched;
    }

    /**
     * 细分类多选筛选（BR-OP-FDT-008；未传则不过滤；口径为 fin_type_code）。
     *
     * @param chargeItem            收费明细
     * @param finTypeByChargeItemId chargeItemId → fin_type_code
     * @param subCategoryCodes      细分类多选（fin_type_code）
     * @return 命中或未传筛选时 {@code true}
     */
    private boolean matchesSubCategoryCodes(
        ChargeItem chargeItem,
        Map<Long, String> finTypeByChargeItemId,
        List<String> subCategoryCodes) {
        if (subCategoryCodes == null || subCategoryCodes.isEmpty()) {
            return true;
        }
        if (chargeItem == null || chargeItem.getId() == null) {
            return false;
        }
        if (finTypeByChargeItemId == null) {
            return false;
        }
        String finTypeCode = finTypeByChargeItemId.get(chargeItem.getId());
        if (StringUtils.isEmpty(finTypeCode)) {
            // 无法解析财务类型时，细分类筛选视为未命中
            return false;
        }
        for (String code : subCategoryCodes) {
            if (code != null && code.equals(finTypeCode)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 门诊费用明细纳入的 ChargeItem 状态白名单（BR-OP-FDT-002）。
     */
    private boolean isOutpatientFeeDetailChargeStatus(String statusEnum) {
        return ChargeItemStatus.SETTLED.getCode().equals(statusEnum)
            || ChargeItemStatus.REFUNDED.getCode().equals(statusEnum);
    }

    /**
     * 补全 {@code charge_item_ids} varchar(255) 截断导致漏挂的已结/已退行，并写入 payment 读上下文。
     *
     * @param query 须已执行 {@link PaymentReconciliationQueryService#listOutpatientFeeDetailChargeItemIdsByPaymentFilter}
     */
    private void augmentTruncatedSnapshotChargeItems(OutpatientFeeDetailPageQuery query) {
        List<OutpatientFeeDetailPaymentScopeQueryModel> paymentsInScope = query.getPaymentsInScope();
        if (paymentsInScope == null || paymentsInScope.isEmpty()) {
            return;
        }
        boolean hasTruncatedSnapshot = false;
        for (OutpatientFeeDetailPaymentScopeQueryModel paymentScope : paymentsInScope) {
            if (paymentScope == null) {
                continue;
            }
            if (this.isLikelyTruncatedChargeItemIdsSnapshot(paymentScope.getChargeItemIdsSnapshot())) {
                // 至少一笔 payment 快照触达历史列长上限，才启动补全，避免误挂历史已结行
                hasTruncatedSnapshot = true;
                break;
            }
        }
        if (!hasTruncatedSnapshot) {
            return;
        }
        Map<Long, OutpatientFeeDetailPaymentContextQueryModel> contextByChargeItemId =
            query.getPaymentContextByChargeItemId();
        if (contextByChargeItemId == null) {
            contextByChargeItemId = new LinkedHashMap<>();
            query.setPaymentContextByChargeItemId(contextByChargeItemId);
        }
        LinkedHashSet<Long> snapshotIds = new LinkedHashSet<>(contextByChargeItemId.keySet());
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        for (OutpatientFeeDetailPaymentScopeQueryModel paymentScope : paymentsInScope) {
            if (paymentScope != null && paymentScope.getEncounterId() != null) {
                encounterIds.add(paymentScope.getEncounterId());
            }
        }
        if (encounterIds.isEmpty()) {
            return;
        }
        List<String> allowedStatuses = this.resolveAugmentChargeStatuses(query.getTradeDirection());
        List<ChargeItem> augmentCandidates =
            this.chargeItemQueryService.listOutpatientFeeDetailAugmentChargeItems(
                encounterIds,
                snapshotIds,
                allowedStatuses,
                query.getEntererMatchIds(),
                query.getCashierId());
        if (augmentCandidates.isEmpty()) {
            return;
        }
        for (ChargeItem chargeItem : augmentCandidates) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            if (!this.isOutpatientFeeDetailChargeStatus(chargeItem.getStatusEnum())) {
                continue;
            }
            OutpatientFeeDetailPaymentScopeQueryModel paymentScope =
                this.resolvePaymentScopeForAugmentedChargeItem(chargeItem, paymentsInScope);
            if (paymentScope == null) {
                continue;
            }
            OutpatientFeeDetailPaymentContextQueryModel context = new OutpatientFeeDetailPaymentContextQueryModel();
            context.setChargeItemId(chargeItem.getId());
            context.setPaymentId(paymentScope.getPaymentId());
            context.setEncounterId(paymentScope.getEncounterId());
            context.setBillDate(paymentScope.getBillDate());
            context.setCashierId(paymentScope.getCashierId());
            context.setRefundPayment(paymentScope.getRefundPayment());
            context.setYbFundAndAcctTotal(paymentScope.getYbFundAndAcctTotal());
            contextByChargeItemId.put(chargeItem.getId(), context);
            OutpatientFeeDetailChargePaymentLinkQueryModel link = new OutpatientFeeDetailChargePaymentLinkQueryModel();
            link.setChargeItemId(chargeItem.getId());
            link.setPaymentContext(context);
            List<OutpatientFeeDetailChargePaymentLinkQueryModel> links = query.getPaymentChargeItemLinks();
            if (links == null) {
                links = new ArrayList<>();
                query.setPaymentChargeItemLinks(links);
            }
            links.add(link);
        }
    }

    /**
     * 收退 Tab → 快照截断补全用的 chargeItem 状态白名单（与 BR-OP-FDT-002/009 一致）。
     *
     * @param tradeDirection ALL / CHARGE / REFUND
     * @return 持久化状态码列表
     */
    private List<String> resolveAugmentChargeStatuses(String tradeDirection) {
        if (OutpatientFeeDetailConstants.TRADE_DIRECTION_CHARGE.equals(tradeDirection)) {
            return List.of(ChargeItemStatus.SETTLED.getCode());
        }
        if (OutpatientFeeDetailConstants.TRADE_DIRECTION_REFUND.equals(tradeDirection)) {
            return List.of(ChargeItemStatus.REFUNDED.getCode());
        }
        return Arrays.asList(ChargeItemStatus.SETTLED.getCode(), ChargeItemStatus.REFUNDED.getCode());
    }

    /**
     * 为补全候选行挂靠最可能的 payment scope（同就诊、同收款人、收退方向一致、结算时间最接近发生时刻）。
     *
     * @param chargeItem       补全候选收费明细
     * @param paymentsInScope  bill_date 筛选命中的 payment 列表
     * @return 挂靠 payment；无法唯一推断时 {@code null}
     */
    private OutpatientFeeDetailPaymentScopeQueryModel resolvePaymentScopeForAugmentedChargeItem(
        ChargeItem chargeItem,
        List<OutpatientFeeDetailPaymentScopeQueryModel> paymentsInScope) {
        boolean refundItem = ChargeItemStatus.REFUNDED.getCode().equals(chargeItem.getStatusEnum());
        OutpatientFeeDetailPaymentScopeQueryModel bestScope = null;
        long bestDistanceMinutes = Long.MAX_VALUE;
        for (OutpatientFeeDetailPaymentScopeQueryModel paymentScope : paymentsInScope) {
            if (paymentScope == null || paymentScope.getEncounterId() == null) {
                continue;
            }
            if (!paymentScope.getEncounterId().equals(chargeItem.getEncounterId())) {
                continue;
            }
            boolean scopeRefund = Boolean.TRUE.equals(paymentScope.getRefundPayment());
            if (refundItem != scopeRefund) {
                continue;
            }
            if (!this.matchesAugmentedChargeCashier(chargeItem, paymentScope)) {
                continue;
            }
            if (!this.isLikelyTruncatedChargeItemIdsSnapshot(paymentScope.getChargeItemIdsSnapshot())) {
                // 仅挂靠快照被截断的 payment，避免跨笔结算误并
                continue;
            }
            if (chargeItem.getOccurrenceTime() == null || paymentScope.getBillDate() == null) {
                if (bestScope == null) {
                    bestScope = paymentScope;
                }
                continue;
            }
            long distanceMinutes = Math.abs(
                java.time.Duration.between(chargeItem.getOccurrenceTime(), paymentScope.getBillDate()).toMinutes());
            if (distanceMinutes < bestDistanceMinutes) {
                bestDistanceMinutes = distanceMinutes;
                bestScope = paymentScope;
            }
        }
        return bestScope;
    }

    /**
     * 结算写侧将收款人写入 {@code performer_id}（BR-OPC-016），与 payment {@code enterer_id} 同源。
     *
     * @param chargeItem   补全候选收费明细
     * @param paymentScope payment 读 scope
     * @return 收款人一致时 {@code true}
     */
    private boolean matchesAugmentedChargeCashier(
        ChargeItem chargeItem,
        OutpatientFeeDetailPaymentScopeQueryModel paymentScope) {
        if (chargeItem == null || paymentScope == null) {
            return false;
        }
        Long performerId = chargeItem.getPerformerId();
        Long cashierId = paymentScope.getCashierId();
        if (performerId == null || cashierId == null) {
            return false;
        }
        return performerId.equals(cashierId);
    }

    /**
     * 判断 payment {@code charge_item_ids} 快照是否触达历史 varchar(255) 上限（可能被截断）。
     *
     * @param chargeItemIdsSnapshot 持久化快照原文
     * @return 长度 ≥ {@link OutpatientFeeDetailConstants#CHARGE_ITEM_IDS_VARCHAR_LEGACY_MAX_LENGTH} 时 {@code true}
     */
    private boolean isLikelyTruncatedChargeItemIdsSnapshot(String chargeItemIdsSnapshot) {
        if (StringUtils.isEmpty(chargeItemIdsSnapshot)) {
            return false;
        }
        return chargeItemIdsSnapshot.length()
            >= OutpatientFeeDetailConstants.CHARGE_ITEM_IDS_VARCHAR_LEGACY_MAX_LENGTH;
    }
}
