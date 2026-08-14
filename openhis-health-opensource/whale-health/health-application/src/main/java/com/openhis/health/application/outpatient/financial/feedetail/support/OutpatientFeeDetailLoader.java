/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.feedetail.support;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.openhis.health.domain.administration.chargeitem.query.FeeDetailChargeItemFilterProjection;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailChargePaymentLinkQueryModel;
import com.openhis.health.domain.share.constants.OutpatientFeeDetailConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.query.ProductDefinitionBatchLookup;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.share.utils.ProductDefinitionKeyUtil;
import com.openhis.health.application.shared.financial.DailySettlementFinTypeAmountLoader;
import com.openhis.health.application.shared.product.ChargeItemProductLookupHelper;
import com.openhis.health.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.openhis.health.application.shared.report.ReportChargeBillSummarizeScopeSupport;
import com.openhis.health.application.shared.report.ReportPatientSettlementContextAssembler;
import com.openhis.health.domain.share.product.dto.ProductDefinitionDisplayDto;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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
 * <p>本类只读、不写库。<strong>费用明细（明细+汇总+导出）统一 ChargeItem-only</strong>：按
 * {@code entered_date} 窗拉已结账单，禁止扫 Payment。退费态不入统计（净额=仍有效收费）。
 * ChargeItem 批量读委托 ADM-011 QueryService；产品展示经
 * {@link ProductDefinitionDisplayTerminologyAssembler} 批量加载（禁止跨聚合 Repository）。</p>
 * <p>细分类筛选与展示取定价行 {@code type_code}（fin_type_code），与日结 fin_type 字典码一致。</p>
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

    /** 账单行合成读侧链路上下文（无真实 paymentId）。 */
    private final ReportChargeBillSummarizeScopeSupport reportChargeBillSummarizeScopeSupport;

    /**
     * 明细分页/导出：按账单收费时间窗预解析 scope（与汇总同口径，禁止扫 Payment）。
     *
     * <p>须在调用 {@link ChargeItemQueryService#pageOutpatientFeeDetailEncounterBlocks} 之前执行。
     * <strong>导出/兼容</strong>仍可走本方法（全日窗实体）；明细分页请改用
     * {@link #prepareOutpatientFeeDetailPageScopes}。</p>
     *
     * @param query 须含 {@code billDateFrom}/{@code billDateTo}
     * @throws IllegalArgumentException {@code query} 为空
     */
    public void prepareOutpatientFeeDetailQueryScopes(OutpatientFeeDetailPageQuery query) {
        this.prepareOutpatientFeeDetailScopesFromChargeBill(query);
    }

    /**
     * 明细分页专路径：DB encounter 分页 + 仅本页装实体，禁止一个月全日窗 ChargeItem 预载。
     *
     * <p>汇总 Tab / 导出仍走 {@link #prepareOutpatientFeeDetailScopesFromChargeBill}。
     * 写入 {@code pageScopeOnly=true}、{@code resolvedEncounterPageTotal} 与命中就诊全集供页脚 SUM。</p>
     *
     * @param query 须含日期窗与分页键
     * @throws IllegalArgumentException {@code query} 为空或缺日窗
     */
    public void prepareOutpatientFeeDetailPageScopes(OutpatientFeeDetailPageQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("outpatient fee detail query is required");
        }
        if (query.getBillDateFrom() == null || query.getBillDateTo() == null) {
            throw new IllegalArgumentException("billDateFrom and billDateTo are required");
        }
        // Gate：退费 Tab 在净口径下无行（退费不入统计）
        if (OutpatientFeeDetailConstants.TRADE_DIRECTION_REFUND.equals(query.getTradeDirection())) {
            this.clearOutpatientFeeDetailScopesToEmpty(query);
            query.setPageScopeOnly(Boolean.TRUE);
            query.setResolvedEncounterPageTotal(0L);
            query.setResolvedFeeDetailMatchedEncounterIds(List.of());
            query.setResolvedSaleAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
            return;
        }
        LocalDateTime enteredFrom = query.getBillDateFrom().atStartOfDay();
        LocalDateTime enteredToExclusive = query.getBillDateTo().plusDays(1).atStartOfDay();
        List<String> statusCodes = List.of(ChargeItemStatus.SETTLED.getCode());
        List<String> serviceTables =
            this.chargeItemQueryService.resolveServiceTablesForFeeCategories(query.getFeeCategoryCodes());
        CollectionOrNull serviceTableFilter = this.toServiceTableFilter(serviceTables);
        // Pre：日窗 distinct encounterId（仅 Long），再筛 AMB + 患者/挂号科室
        List<Long> windowEncounterIds =
            this.chargeItemQueryService.listDistinctEncounterIdsByEnteredDateWindow(
                enteredFrom, enteredToExclusive, statusCodes, query.getEntererMatchIds());
        Set<Long> ambEncounterIds = this.filterEncounterIdsByClass(
            new LinkedHashSet<>(windowEncounterIds), EncounterClass.AMB);
        Set<Long> allowedEncounterIds = this.resolveAllowedEncounterIds(query, ambEncounterIds);
        if (allowedEncounterIds.isEmpty()) {
            this.clearOutpatientFeeDetailScopesToEmpty(query);
            query.setPageScopeOnly(Boolean.TRUE);
            query.setResolvedEncounterPageTotal(0L);
            query.setResolvedFeeDetailMatchedEncounterIds(List.of());
            query.setResolvedSaleAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
            return;
        }
        boolean needChargeLevelHardFilter = this.needsChargeLevelHardFilter(query);
        List<Long> matchedEncounterIds;
        List<FeeDetailChargeItemFilterProjection> matchedProjections = List.of();
        BigDecimal projectionSaleSum = null;
        // Pre：项目/细分类/费用性质 → 轻量投影过滤命中 encounter（禁止全日窗实体进 preloaded）
        if (needChargeLevelHardFilter) {
            List<FeeDetailChargeItemFilterProjection> projections =
                this.chargeItemQueryService.listFeeDetailChargeItemFilterProjections(
                    enteredFrom,
                    enteredToExclusive,
                    statusCodes,
                    query.getEntererMatchIds(),
                    query.getRequestingOrgId(),
                    allowedEncounterIds,
                    serviceTableFilter.value);
            matchedProjections = this.filterProjectionsByChargeLevelHardFilters(projections, query);
            LinkedHashSet<Long> matchedEncounterSet = new LinkedHashSet<>();
            LinkedHashSet<Long> matchedChargeItemIdSet = new LinkedHashSet<>();
            BigDecimal saleSum = BigDecimal.ZERO;
            for (FeeDetailChargeItemFilterProjection projection : matchedProjections) {
                if (projection == null || projection.getEncounterId() == null) {
                    continue;
                }
                matchedEncounterSet.add(projection.getEncounterId());
                if (projection.getId() != null) {
                    matchedChargeItemIdSet.add(projection.getId());
                }
                if (projection.getTotalPrice() != null) {
                    saleSum = saleSum.add(projection.getTotalPrice());
                }
            }
            matchedEncounterIds = new ArrayList<>(matchedEncounterSet);
            projectionSaleSum = MonetaryAmountSupport.scaleSettle(saleSum);
            // Asm：硬筛命中 chargeItemId 全集，供页脚进价按 id IN 装载
            query.setResolvedFeeDetailMatchedChargeItemIds(new ArrayList<>(matchedChargeItemIdSet));
        } else {
            matchedEncounterIds = new ArrayList<>(allowedEncounterIds);
            query.setResolvedFeeDetailMatchedChargeItemIds(null);
        }
        if (matchedEncounterIds.isEmpty()) {
            this.clearOutpatientFeeDetailScopesToEmpty(query);
            query.setPageScopeOnly(Boolean.TRUE);
            query.setResolvedEncounterPageTotal(0L);
            query.setResolvedFeeDetailMatchedEncounterIds(List.of());
            query.setResolvedSaleAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
            return;
        }
        int pageNoZeroBased = 0;
        if (query.getPageNo() != null && query.getPageNo() >= 1) {
            pageNoZeroBased = query.springPageIndex();
        }
        int pageSize = OutpatientFeeDetailConstants.DEFAULT_PAGE_SIZE;
        if (query.getPageSize() != null && query.getPageSize() > 0) {
            pageSize = query.getBoundedPageSize();
        }
        // Pre：DB GROUP BY encounter 分页（ORDER BY MAX(entered_date) DESC）
        Page<Long> encounterPage =
            this.chargeItemQueryService.pageFeeDetailEncounterIdsByEnteredDateWindow(
                enteredFrom,
                enteredToExclusive,
                statusCodes,
                query.getEntererMatchIds(),
                query.getRequestingOrgId(),
                matchedEncounterIds,
                serviceTableFilter.value,
                pageNoZeroBased,
                pageSize);
        List<Long> pageEncounterIds = encounterPage.getContent();
        // Asm：回写钳制后的实际页码（1-based），与域层 Page 一致
        query.setPageNo(encounterPage.getNumber() + 1);
        query.setResolvedEncounterPageTotal(encounterPage.getTotalElements());
        query.setResolvedFeeDetailMatchedEncounterIds(matchedEncounterIds);
        query.setPageScopeOnly(Boolean.TRUE);
        if (projectionSaleSum != null) {
            query.setResolvedSaleAmountTotal(projectionSaleSum);
        } else {
            query.setResolvedSaleAmountTotal(null);
        }
        if (pageEncounterIds == null || pageEncounterIds.isEmpty()) {
            this.clearOutpatientFeeDetailScopesToEmpty(query);
            query.setPageScopeOnly(Boolean.TRUE);
            query.setResolvedEncounterPageTotal(encounterPage.getTotalElements());
            query.setResolvedFeeDetailMatchedEncounterIds(matchedEncounterIds);
            if (projectionSaleSum != null) {
                query.setResolvedSaleAmountTotal(projectionSaleSum);
            }
            return;
        }
        List<ChargeItem> pageChargeItems;
        // Pre：有行级硬筛时只装本页命中投影对应实体；否则按本页 encounter IN 装日窗实体
        if (needChargeLevelHardFilter) {
            LinkedHashSet<Long> pageEncounterSet = new LinkedHashSet<>(pageEncounterIds);
            LinkedHashSet<Long> pageChargeItemIds = new LinkedHashSet<>();
            for (FeeDetailChargeItemFilterProjection projection : matchedProjections) {
                if (projection == null || projection.getId() == null || projection.getEncounterId() == null) {
                    continue;
                }
                if (!pageEncounterSet.contains(projection.getEncounterId())) {
                    continue;
                }
                pageChargeItemIds.add(projection.getId());
            }
            ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
            loadCmd.setIds(new ArrayList<>(pageChargeItemIds));
            pageChargeItems = this.chargeItemQueryService.loadChargeItemsByIds(loadCmd);
        } else {
            pageChargeItems = this.chargeItemQueryService.listChargeItemsByEncounterIdsInEnteredDateWindow(
                pageEncounterIds,
                enteredFrom,
                enteredToExclusive,
                statusCodes,
                query.getEntererMatchIds(),
                query.getRequestingOrgId());
            // 费用大类已下推 service_table；此处无需再滤
        }
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> links =
            this.reportChargeBillSummarizeScopeSupport.buildSyntheticPaymentLinks(pageChargeItems);
        Map<Long, ChargeItem> preloadedById = new LinkedHashMap<>();
        LinkedHashSet<Long> pageChargeItemIdSet = new LinkedHashSet<>();
        for (ChargeItem chargeItem : pageChargeItems) {
            if (chargeItem != null && chargeItem.getId() != null) {
                preloadedById.put(chargeItem.getId(), chargeItem);
                pageChargeItemIdSet.add(chargeItem.getId());
            }
        }
        query.setPaymentChargeItemLinks(links);
        query.setPaymentContextByChargeItemId(
            this.reportChargeBillSummarizeScopeSupport.buildSyntheticPaymentContextByChargeItemId(pageChargeItems));
        query.setPreloadedChargeItemsById(preloadedById);
        query.setPaymentsInScope(List.of());
        query.setResolvedChargeItemScope(new ArrayList<>(pageChargeItemIdSet));
        query.setResolvedEncounterScope(new ArrayList<>(pageEncounterIds));
        query.setResolvedPaymentChargeItemLinks(links);
        query.setCachedChargeRows(null);
    }

    /**
     * 汇总 Tab：与明细分页同路径（ChargeItem-only + 仅 SETTLED）。
     *
     * @param query 须含日期窗
     */
    public void prepareOutpatientFeeDetailSummarizeScopesFromChargeBill(OutpatientFeeDetailPageQuery query) {
        this.prepareOutpatientFeeDetailScopesFromChargeBill(query);
    }

    /**
     * 费用明细统一 scope：{@code entered_date} 窗 + 仅 SETTLED + 门诊就诊；退费 Tab 直接空结果。
     *
     * <p>供<strong>汇总/导出</strong>全日窗路径；明细分页禁止调用本方法装满月实体。</p>
     *
     * @param query 须含日期窗；可含收费人白名单
     */
    private void prepareOutpatientFeeDetailScopesFromChargeBill(OutpatientFeeDetailPageQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("outpatient fee detail query is required");
        }
        if (query.getBillDateFrom() == null || query.getBillDateTo() == null) {
            throw new IllegalArgumentException("billDateFrom and billDateTo are required");
        }
        // Gate：退费 Tab 在净口径下无行（退费不入统计）
        if (OutpatientFeeDetailConstants.TRADE_DIRECTION_REFUND.equals(query.getTradeDirection())) {
            this.clearOutpatientFeeDetailScopesToEmpty(query);
            return;
        }
        LocalDateTime enteredFrom = query.getBillDateFrom().atStartOfDay();
        LocalDateTime enteredToExclusive = query.getBillDateTo().plusDays(1).atStartOfDay();
        // Pre：仅已结账单；收费人按 enterer_id（结算写入口径）
        List<ChargeItem> windowChargeItems =
            this.chargeItemQueryService.listSettledChargeItemsByEnteredDateWindow(
                enteredFrom, enteredToExclusive, query.getEntererMatchIds());
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : windowChargeItems) {
            if (chargeItem != null && chargeItem.getEncounterId() != null) {
                encounterIds.add(chargeItem.getEncounterId());
            }
        }
        Set<Long> ambEncounterIds = this.filterEncounterIdsByClass(encounterIds, EncounterClass.AMB);
        List<ChargeItem> ambChargeItems = new ArrayList<>();
        for (ChargeItem chargeItem : windowChargeItems) {
            if (chargeItem == null || chargeItem.getId() == null || chargeItem.getEncounterId() == null) {
                continue;
            }
            if (!ambEncounterIds.contains(chargeItem.getEncounterId())) {
                continue;
            }
            ambChargeItems.add(chargeItem);
        }
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> links =
            this.reportChargeBillSummarizeScopeSupport.buildSyntheticPaymentLinks(ambChargeItems);
        Map<Long, ChargeItem> preloadedById = new LinkedHashMap<>();
        for (ChargeItem chargeItem : ambChargeItems) {
            if (chargeItem != null && chargeItem.getId() != null) {
                preloadedById.put(chargeItem.getId(), chargeItem);
            }
        }
        query.setPaymentChargeItemLinks(links);
        query.setPaymentContextByChargeItemId(
            this.reportChargeBillSummarizeScopeSupport.buildSyntheticPaymentContextByChargeItemId(ambChargeItems));
        query.setPreloadedChargeItemsById(preloadedById);
        query.setPaymentsInScope(List.of());
        query.setPageScopeOnly(Boolean.FALSE);
        this.narrowOutpatientFeeDetailScopesAfterCandidates(query);
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
    }

    /**
     * 是否需要行级硬筛选（项目关键字 / 细分类 / 费用性质）——须轻量投影，禁止全日窗实体。
     *
     * @param query 查询
     * @return 需要时 {@code true}
     */
    private boolean needsChargeLevelHardFilter(OutpatientFeeDetailPageQuery query) {
        if (StringUtils.isNotEmpty(query.getProductNameKeyword())
            || StringUtils.isNotEmpty(query.getProductCodeKeyword())) {
            return true;
        }
        if (query.getSubCategoryCodes() != null && !query.getSubCategoryCodes().isEmpty()) {
            return true;
        }
        if (StringUtils.isNotEmpty(query.getFeeNatureContractNo())) {
            return true;
        }
        return false;
    }

    /**
     * service_table 过滤包装：空列表表示不过滤（传 null 给域层）。
     *
     * @param serviceTables 大类映射表名
     * @return 包装；value 可为 null
     */
    private CollectionOrNull toServiceTableFilter(List<String> serviceTables) {
        CollectionOrNull wrapper = new CollectionOrNull();
        if (serviceTables == null || serviceTables.isEmpty()) {
            wrapper.value = null;
        } else {
            wrapper.value = serviceTables;
        }
        return wrapper;
    }

    /**
     * 对轻量投影套用项目关键字 / 细分类 / 费用性质（用 stub ChargeItem 复用既有匹配逻辑）。
     *
     * @param projections 日窗投影
     * @param query       筛选条件
     * @return 命中投影
     */
    private List<FeeDetailChargeItemFilterProjection> filterProjectionsByChargeLevelHardFilters(
        List<FeeDetailChargeItemFilterProjection> projections,
        OutpatientFeeDetailPageQuery query) {
        if (projections == null || projections.isEmpty()) {
            return List.of();
        }
        List<ChargeItem> stubs = new ArrayList<>(projections.size());
        Map<Long, FeeDetailChargeItemFilterProjection> projectionById = new LinkedHashMap<>();
        for (FeeDetailChargeItemFilterProjection projection : projections) {
            if (projection == null || projection.getId() == null) {
                continue;
            }
            ChargeItem stub = new ChargeItem();
            stub.setId(projection.getId());
            stub.setEncounterId(projection.getEncounterId());
            stub.setProductTable(projection.getProductTable());
            stub.setProductId(projection.getProductId());
            stub.setAccountId(projection.getAccountId());
            stub.setDefinitionId(projection.getDefinitionId());
            stub.setTotalPrice(projection.getTotalPrice());
            stubs.add(stub);
            projectionById.put(projection.getId(), projection);
        }
        if (stubs.isEmpty()) {
            return List.of();
        }
        Set<Long> feeNatureMatchedChargeItemIds =
            this.reportPatientSettlementContextAssembler.filterChargeItemIdsByAccountContractNo(
                stubs, query.getFeeNatureContractNo());
        // 费用性质有筛选但无命中时短路
        if (feeNatureMatchedChargeItemIds != null && feeNatureMatchedChargeItemIds.isEmpty()) {
            return List.of();
        }
        boolean filterByProductKeyword = StringUtils.isNotEmpty(query.getProductNameKeyword())
            || StringUtils.isNotEmpty(query.getProductCodeKeyword());
        boolean filterBySubCategory = query.getSubCategoryCodes() != null
            && !query.getSubCategoryCodes().isEmpty();
        Map<String, ProductDefinitionDisplayDto> productMap = Map.of();
        if (filterByProductKeyword) {
            productMap = this.loadProductMapForChargeItems(stubs);
        }
        Map<Long, String> finTypeByChargeItemId = Map.of();
        if (filterBySubCategory) {
            finTypeByChargeItemId =
                this.dailySettlementFinTypeAmountLoader.mapFinTypeCodeByChargeItemId(stubs);
        }
        List<FeeDetailChargeItemFilterProjection> matched = new ArrayList<>();
        for (ChargeItem stub : stubs) {
            if (stub == null || stub.getId() == null) {
                continue;
            }
            if (feeNatureMatchedChargeItemIds != null
                && !feeNatureMatchedChargeItemIds.contains(stub.getId())) {
                continue;
            }
            if (filterByProductKeyword && !this.matchesProductKeywords(stub, productMap, query)) {
                continue;
            }
            if (filterBySubCategory
                && !this.matchesSubCategoryCodes(stub, finTypeByChargeItemId, query.getSubCategoryCodes())) {
                continue;
            }
            FeeDetailChargeItemFilterProjection projection = projectionById.get(stub.getId());
            if (projection != null) {
                matched.add(projection);
            }
        }
        return matched;
    }

    /**
     * 可空 service_table 集合包装（避免三元）。
     */
    private static final class CollectionOrNull {
        /** 非空时下推 service_table IN；null 表示不过滤。 */
        private List<String> value;
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
                // 净口径仅保留已结；退费退出统计
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
     * 门诊费用明细纳入的 ChargeItem 状态：仅已结（退费不入统计）。
     *
     * @param statusEnum 账单状态码
     * @return 已结时 {@code true}
     */
    private boolean isOutpatientFeeDetailChargeStatus(String statusEnum) {
        return ChargeItemStatus.SETTLED.getCode().equals(statusEnum);
    }
}
