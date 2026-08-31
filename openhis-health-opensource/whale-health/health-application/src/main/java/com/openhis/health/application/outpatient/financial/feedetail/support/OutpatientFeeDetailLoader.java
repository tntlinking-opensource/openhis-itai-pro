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
import com.openhis.health.domain.financial.paymentreconciliation.service.PaymentReconciliationQueryService;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.application.shared.financial.DailySettlementFinTypeAmountLoader;
import com.openhis.health.application.shared.product.ChargeItemProductLookupHelper;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.report.ReportPatientSettlementContextAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 门诊费用明细读路径 scope 预解析（B.6.1；AS-OP-FIN-005 / AS-RPT-008）。
 *
 * <p>本类只读、不写库。<strong>时间轴 / 收退 Tab 以 Payment {@code bill_date} 为准</strong>
 * （与日结同轴；BR-OP-FDT-002/003/009）：收费 SUCCESS 正、退费 REFUNDED 负。
 * 写路径退费会把原行改成 {@code REFUNDED}/{@code REFUNDING} 并覆盖 {@code entered_date}，故禁止仅靠 ChargeItem
 * 日窗统计；挂载快照仍指向原 chargeItemId，收退各保留一行链路。</p>
 * <p>ChargeItem 状态白名单为 {@code SETTLED}∪{@code REFUNDING}∪{@code REFUNDED}
 * （行额符号看 payment 上下文，不看状态）。{@code REFUNDING} 表示退费进行中、原收费 SUCCESS 快照仍挂该行，
 * 须计入已收净额直至退费 Payment 落库冲减，避免与日结 {@code fin_payment_rec_static} 少计。</p>
 * <p>产品展示经 {@link ProductDefinitionDisplayTerminologyAssembler} 批量加载；细分类取定价行
 * {@code type_code}（与日结 fin_type 一致）。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientFeeDetailLoader {

    private final ChargeItemQueryService chargeItemQueryService;
    private final EncounterQueryService encounterQueryService;
    private final PatientQueryService patientQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final DailySettlementFinTypeAmountLoader dailySettlementFinTypeAmountLoader;

    /** 费用性质筛选：按收费行挂账账户 contract_no（非就诊优先账户）。 */
    private final ReportPatientSettlementContextAssembler reportPatientSettlementContextAssembler;

    /** Payment 日窗 / 收退 Tab → chargeItem 快照链路（DM-FIN-002 §6.2.17）。 */
    private final PaymentReconciliationQueryService paymentReconciliationQueryService;

    /**
     * 明细导出：按 Payment {@code bill_date} 预解析全日窗 scope（与汇总同口径）。
     *
     * <p>须在调用 {@link ChargeItemQueryService#pageOutpatientFeeDetailEncounterBlocks} /
     * 全量 list 之前执行。</p>
     *
     * @param query 须含 {@code billDateFrom}/{@code billDateTo}
     * @throws IllegalArgumentException {@code query} 为空或缺日窗
     */
    public void prepareOutpatientFeeDetailQueryScopes(OutpatientFeeDetailPageQuery query) {
        this.prepareOutpatientFeeDetailScopesFromPayment(query);
    }

    /**
     * 明细分页：Payment 日窗收退链路 + 就诊块内存分页（{@code pageScopeOnly=false}）。
     *
     * <p>与汇总/导出同 Payment 口径；禁止再走 ChargeItem {@code entered_date} 日窗（退费会抹收费日事实）。</p>
     *
     * @param query 须含日期窗与分页键
     * @throws IllegalArgumentException {@code query} 为空或缺日窗
     */
    public void prepareOutpatientFeeDetailPageScopes(OutpatientFeeDetailPageQuery query) {
        this.prepareOutpatientFeeDetailScopesFromPayment(query);
    }

    /**
     * 汇总 Tab：Payment {@code bill_date} + 收退并集净额（退费冲减）。
     *
     * @param query 须含日期窗
     */
    public void prepareOutpatientFeeDetailSummarizeScopesFromChargeBill(OutpatientFeeDetailPageQuery query) {
        this.prepareOutpatientFeeDetailScopesFromPayment(query);
    }

    /**
     * 费用明细统一 scope：Payment 日窗 + 收退 Tab + 门诊 AMB + 硬筛收窄。
     *
     * <p>CHARGE→SUCCESS；REFUND→REFUNDED；ALL→两者。退费 Tab 出退费 payment 负向行，不再恒空。</p>
     *
     * @param query 须含日期窗；可含收费人白名单 / 收退 Tab
     */
    private void prepareOutpatientFeeDetailScopesFromPayment(OutpatientFeeDetailPageQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("outpatient fee detail query is required");
        }
        if (query.getBillDateFrom() == null || query.getBillDateTo() == null) {
            throw new IllegalArgumentException("billDateFrom and billDateTo are required");
        }
        // Pre：已结 + 退费中 + 已退；退费中仍挂原 SUCCESS 快照，须计入直至退费 Payment 冲减
        query.setAllowedChargeItemStatusCodes(List.of(
            ChargeItemStatus.SETTLED.getCode(),
            ChargeItemStatus.REFUNDING.getCode(),
            ChargeItemStatus.REFUNDED.getCode()));
        // Pre：按 bill_date / 收费人 / 收退 Tab 解析 payment→chargeItem 快照链路（与日结同轴）
        this.paymentReconciliationQueryService.listOutpatientFeeDetailChargeItemIdsByPaymentFilter(query);
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> paymentLinks = query.getPaymentChargeItemLinks();
        if (paymentLinks == null || paymentLinks.isEmpty()) {
            this.clearOutpatientFeeDetailScopesToEmpty(query);
            return;
        }
        // Pre：先收窄门诊 AMB，再套挂号科室/患者等硬筛
        LinkedHashSet<Long> candidateEncounterIds = new LinkedHashSet<>();
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link == null || link.getPaymentContext() == null) {
                continue;
            }
            if (link.getPaymentContext().getEncounterId() != null) {
                candidateEncounterIds.add(link.getPaymentContext().getEncounterId());
            }
        }
        Set<Long> ambEncounterIds = this.filterEncounterIdsByClass(candidateEncounterIds, EncounterClass.AMB);
        if (ambEncounterIds.isEmpty()) {
            this.clearOutpatientFeeDetailScopesToEmpty(query);
            return;
        }
        // Asm：非 AMB 就诊从候选链路剔除（门诊费用明细不入住院结算单）
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> ambLinks = new ArrayList<>();
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentLinks) {
            if (link == null || link.getPaymentContext() == null) {
                continue;
            }
            Long encounterId = link.getPaymentContext().getEncounterId();
            if (encounterId == null || !ambEncounterIds.contains(encounterId)) {
                continue;
            }
            ambLinks.add(link);
        }
        if (ambLinks.isEmpty()) {
            this.clearOutpatientFeeDetailScopesToEmpty(query);
            return;
        }
        query.setPaymentChargeItemLinks(ambLinks);
        // Pre：明细分页走 links 内存就诊块分页；页脚售价走链路带符号加总
        query.setPageScopeOnly(Boolean.FALSE);
        query.setResolvedSaleAmountTotal(null);
        this.narrowOutpatientFeeDetailScopesAfterCandidates(query);
        // Asm：进价页脚按命中 chargeItemId IN 装载（与售价 scope 同集合）
        List<Long> resolvedChargeItemScope = query.getResolvedChargeItemScope();
        if (resolvedChargeItemScope != null && !resolvedChargeItemScope.isEmpty()) {
            query.setResolvedFeeDetailMatchedChargeItemIds(new ArrayList<>(resolvedChargeItemScope));
            List<Long> resolvedEncounterScope = query.getResolvedEncounterScope();
            if (resolvedEncounterScope == null) {
                query.setResolvedFeeDetailMatchedEncounterIds(List.of());
            } else {
                query.setResolvedFeeDetailMatchedEncounterIds(new ArrayList<>(resolvedEncounterScope));
            }
        }
    }

    /**
     * 将 scope 相关字段清空为无命中（退费 Tab / 白名单空）。
     *
     * @param query 目标查询
     */
    private void clearOutpatientFeeDetailScopesToEmpty(OutpatientFeeDetailPageQuery query) {
        query.setPaymentChargeItemLinks(List.of());
        query.setPaymentContextByChargeItemId(Map.of());
        query.setPreloadedChargeItemsById(Map.of());
        query.setPaymentsInScope(List.of());
        query.setResolvedChargeItemScope(List.of());
        query.setResolvedEncounterScope(List.of());
        query.setResolvedPaymentChargeItemLinks(List.of());
        query.setCachedChargeRows(null);
        query.setResolvedFeeDetailMatchedChargeItemIds(List.of());
        query.setResolvedFeeDetailMatchedEncounterIds(List.of());
        query.setResolvedEncounterPageTotal(0L);
        query.setResolvedSaleAmountTotal(null);
        query.setPageScopeOnly(Boolean.FALSE);
    }

    /**
     * 在已写入 {@code paymentChargeItemLinks} 的候选上收窄科室/患者/项目等并写 resolved scope。
     *
     * @param query 已含候选链路
     */
    private void narrowOutpatientFeeDetailScopesAfterCandidates(OutpatientFeeDetailPageQuery query) {
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
        // Pre：在候选 scope 内再收窄挂号科室/患者/项目/细分类，写入 resolved scope 供 ChargeItem 分页读
        ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
        loadCmd.setIds(new ArrayList<>(uniqueChargeItemIds));
        Map<Long, ChargeItem> preloaded = query.getPreloadedChargeItemsById();
        List<ChargeItem> chargeItems;
        Map<Long, ChargeItem> chargeItemById = new LinkedHashMap<>();
        boolean reusePreloaded = false;
        if (preloaded != null && !preloaded.isEmpty()) {
            reusePreloaded = true;
            for (Long chargeItemId : uniqueChargeItemIds) {
                if (chargeItemId == null || !preloaded.containsKey(chargeItemId)) {
                    reusePreloaded = false;
                    break;
                }
            }
        }
        if (reusePreloaded) {
            for (Long chargeItemId : uniqueChargeItemIds) {
                chargeItemById.put(chargeItemId, preloaded.get(chargeItemId));
            }
            chargeItems = new ArrayList<>(chargeItemById.values());
        } else {
            chargeItems = this.chargeItemQueryService.loadChargeItemsByIds(loadCmd);
            for (ChargeItem chargeItem : chargeItems) {
                if (chargeItem != null && chargeItem.getId() != null) {
                    chargeItemById.put(chargeItem.getId(), chargeItem);
                }
            }
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        // 收集候选涉及的就诊，供科室/患者二次收窄
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem != null && chargeItem.getEncounterId() != null) {
                encounterIds.add(chargeItem.getEncounterId());
            }
        }
        Set<Long> allowedEncounterIds = this.resolveAllowedEncounterIds(query, encounterIds);
        // Pre：费用性质按收费行 account_id→contract_no 收窄（转自费后跟新账户；非就诊优先医保户）
        Set<Long> feeNatureMatchedChargeItemIds =
            this.reportPatientSettlementContextAssembler.filterChargeItemIdsByAccountContractNo(
                chargeItems, query.getFeeNatureContractNo());
        // 费用性质有筛选但无命中时短路
        if (feeNatureMatchedChargeItemIds != null && feeNatureMatchedChargeItemIds.isEmpty()) {
            query.setResolvedChargeItemScope(List.of());
            query.setResolvedEncounterScope(List.of());
            query.setResolvedPaymentChargeItemLinks(List.of());
            return;
        }
        Set<Long> allowedEncounterIdSet = new HashSet<>(allowedEncounterIds);
        boolean filterByProductKeyword = StringUtils.isNotEmpty(query.getProductNameKeyword())
            || StringUtils.isNotEmpty(query.getProductCodeKeyword());
        boolean filterBySubCategory = query.getSubCategoryCodes() != null
            && !query.getSubCategoryCodes().isEmpty();
        // Pre：无项目关键字时跳过全量产品 Map，避免无谓词仍扫目录
        Map<String, ProductDefinitionDisplayDto> productMap = Map.of();
        if (filterByProductKeyword) {
            productMap = this.loadProductMapForChargeItems(chargeItems);
        }
        // Pre：无细分类多选时跳过全量 fin_type 解析
        Map<Long, String> finTypeByChargeItemId = Map.of();
        if (filterBySubCategory) {
            finTypeByChargeItemId =
                this.dailySettlementFinTypeAmountLoader.mapFinTypeCodeByChargeItemId(chargeItems);
        }
        LinkedHashSet<Long> filteredChargeItemIds = new LinkedHashSet<>();
        LinkedHashSet<Long> filteredEncounterIds = new LinkedHashSet<>();
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> filteredLinks = new ArrayList<>();
        // 逐条关联套用 BR-OP-FDT-002/007/008：状态、就诊、项目名 AND 编码、细分类、费用性质
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentChargeItemLinks) {
            if (link == null || link.getChargeItemId() == null) {
                continue;
            }
            ChargeItem chargeItem = chargeItemById.get(link.getChargeItemId());
            if (chargeItem == null) {
                continue;
            }
            if (!this.isOutpatientFeeDetailChargeStatus(chargeItem.getStatusEnum())) {
                // 写路径退费后行可能已是 REFUNDING/REFUNDED；符号看 payment 链路，白名单内都须保留
                continue;
            }
            if (chargeItem.getEncounterId() == null || !allowedEncounterIdSet.contains(chargeItem.getEncounterId())) {
                // 患者关键字等未命中时剔除
                continue;
            }
            // 开立科室：按账单 requesting_org_id 收窄（与就诊挂号科室分离）
            if (query.getRequestingOrgId() != null
                && !query.getRequestingOrgId().equals(chargeItem.getRequestingOrgId())) {
                continue;
            }
            // 费用性质：仅保留挂账账户 contract_no 命中的收费行
            if (feeNatureMatchedChargeItemIds != null
                && !feeNatureMatchedChargeItemIds.contains(chargeItem.getId())) {
                continue;
            }
            if (filterByProductKeyword && !this.matchesProductKeywords(chargeItem, productMap, query)) {
                continue;
            }
            if (filterBySubCategory
                && !this.matchesSubCategoryCodes(chargeItem, finTypeByChargeItemId, query.getSubCategoryCodes())) {
                continue;
            }
            filteredChargeItemIds.add(chargeItem.getId());
            filteredEncounterIds.add(chargeItem.getEncounterId());
            filteredLinks.add(link);
        }
        query.setResolvedChargeItemScope(new ArrayList<>(filteredChargeItemIds));
        query.setResolvedEncounterScope(new ArrayList<>(filteredEncounterIds));
        query.setResolvedPaymentChargeItemLinks(filteredLinks);
        // Pre：仅保留过滤后的 ChargeItem，缩内存并供域层/汇总复用（禁止二次全量 IN）
        Map<Long, ChargeItem> filteredChargeItemById = new LinkedHashMap<>();
        for (Long chargeItemId : filteredChargeItemIds) {
            ChargeItem chargeItem = chargeItemById.get(chargeItemId);
            if (chargeItem != null) {
                filteredChargeItemById.put(chargeItemId, chargeItem);
            }
        }
        query.setPreloadedChargeItemsById(filteredChargeItemById);
        // Pre：清空旧缓存，强制下次 list 基于最新 scope 物化
        query.setCachedChargeRows(null);
    }

    /**
     * 按就诊类别收窄就诊主键集合。
     *
     * @param encounterIds   候选就诊
     * @param encounterClass 目标类别
     * @return 命中类别的就诊 id
     */
    private Set<Long> filterEncounterIdsByClass(
        Set<Long> encounterIds,
        EncounterClass encounterClass) {
        if (encounterIds == null || encounterIds.isEmpty() || encounterClass == null) {
            return Set.of();
        }
        List<Encounter> encounters = this.encounterQueryService.listEncounterEntitiesByIds(encounterIds);
        LinkedHashSet<Long> matched = new LinkedHashSet<>();
        String classCode = encounterClass.getCode();
        for (Encounter encounter : encounters) {
            if (encounter == null || encounter.getId() == null) {
                continue;
            }
            if (classCode.equals(encounter.getClassEnum())) {
                matched.add(encounter.getId());
            }
        }
        return matched;
    }

    /**
     * 在候选就诊集合上叠加挂号科室（遗留）与患者关键字收窄。
     *
     * <p>开立科室改走账单 {@code requesting_org_id}（见 {@link OutpatientFeeDetailPageQuery#getRequestingOrgId()}），
     * 本方法不再用 Encounter.organization_id 充当开立科室。</p>
     */
    private Set<Long> resolveAllowedEncounterIds(OutpatientFeeDetailPageQuery query, Set<Long> candidateEncounterIds) {
        if (candidateEncounterIds == null || candidateEncounterIds.isEmpty()) {
            return Set.of();
        }
        Set<Long> allowed = new HashSet<>(candidateEncounterIds);
        boolean filterByDepartment = query.getRegistrationDepartmentId() != null;
        boolean filterByPatient = StringUtils.isNotEmpty(query.getPatientKeyword());
        // 无科室/患者二次条件时直接返回 payment 候选
        if (!filterByDepartment && !filterByPatient) {
            return allowed;
        }
        // Pre：只加载候选就诊实体，禁止按科室全表扫 AMB
        List<Encounter> encounters = this.encounterQueryService.listEncounterEntitiesByIds(allowed);
        Long departmentId = query.getRegistrationDepartmentId();
        Set<Long> departmentMatched = new HashSet<>();
        for (Encounter encounter : encounters) {
            if (encounter == null || encounter.getId() == null) {
                continue;
            }
            if (filterByDepartment) {
                // 挂号科室 = Encounter.organization_id；并收窄 AMB
                if (!departmentId.equals(encounter.getOrganizationId())) {
                    continue;
                }
                if (!EncounterClass.AMB.getCode().equals(encounter.getClassEnum())) {
                    continue;
                }
            }
            departmentMatched.add(encounter.getId());
        }
        if (!filterByPatient) {
            return departmentMatched;
        }
        String keyword = query.getPatientKeyword().trim();
        List<Long> patientIds = this.patientQueryService.listPatientIdsByEncounterListKeyword(keyword);
        Set<Long> patientIdSet = new HashSet<>(patientIds);
        Set<Long> matchedEncounterIds = new HashSet<>();
        // 患者关键字：门诊号 busNo 或姓名命中其一即可（在科室收窄后的集合上）
        for (Encounter encounter : encounters) {
            if (encounter == null || encounter.getId() == null) {
                continue;
            }
            if (!departmentMatched.contains(encounter.getId())) {
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
            // 项目名：通用名或商品名任一包含即命中（与药品目录关键字 OR 口径对齐）
            String nameKeyword = query.getProductNameKeyword().trim().toLowerCase(Locale.ROOT);
            boolean genericMatched = product.getProductName() != null
                && product.getProductName().toLowerCase(Locale.ROOT).contains(nameKeyword);
            boolean merchandiseMatched = product.getMerchandiseName() != null
                && product.getMerchandiseName().toLowerCase(Locale.ROOT).contains(nameKeyword);
            nameMatched = genericMatched || merchandiseMatched;
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
     * 门诊费用明细纳入的 ChargeItem 状态：已结、退费中或已退费（BR-OP-FDT-002）。
     *
     * <p>退费写路径会把原行改成 {@code REFUNDING}/{@code REFUNDED}；收费日仍靠原 SUCCESS payment
     * 快照挂载同 id，故三态均须可读。{@code REFUNDING} 尚未产生退费 Payment 时仍计正向已收，
     * 与日结 static 同口径。行金额正负由 payment 收退上下文决定（BR-OP-FDT-012）。</p>
     *
     * @param statusEnum 账单状态码
     * @return 白名单内状态为 {@code true}
     */
    private boolean isOutpatientFeeDetailChargeStatus(String statusEnum) {
        if (ChargeItemStatus.SETTLED.getCode().equals(statusEnum)) {
            return true;
        }
        // 退费中：原收费已入账，未完成退费前须计入净额
        if (ChargeItemStatus.REFUNDING.getCode().equals(statusEnum)) {
            return true;
        }
        return ChargeItemStatus.REFUNDED.getCode().equals(statusEnum);
    }
}
