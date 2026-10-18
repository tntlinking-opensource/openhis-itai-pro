/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.feedetail.inpatient.support;

import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.chargeitem.query.ChargeItemQueryCommand;
import com.zxplat.domain.administration.chargeitem.query.FeeDetailChargeItemFilterProjection;
import com.zxplat.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.zxplat.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.zxplat.domain.administration.encounter.entity.Encounter;
import com.zxplat.domain.administration.encounter.service.EncounterQueryService;
import com.zxplat.domain.administration.patient.service.PatientQueryService;
import com.zxplat.domain.clinical.procedure.entity.Procedure;
import com.zxplat.domain.clinical.procedure.service.ProcedureQueryService;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailChargePaymentLinkQueryModel;
import com.zxplat.domain.financial.paymentreconciliation.queries.model.OutpatientFeeDetailPaymentContextQueryModel;
import com.zxplat.domain.share.constants.OutpatientFeeDetailConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.share.enums.EncounterClass;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.share.enums.GenerateSource;
import com.zxplat.domain.share.query.ProductDefinitionBatchLookup;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import com.zxplat.domain.share.utils.ProductDefinitionKeyUtil;
import com.zxplat.application.shared.financial.DailySettlementFinTypeAmountLoader;
import com.zxplat.application.shared.product.ChargeItemProductLookupHelper;
import com.zxplat.application.shared.product.ProductDefinitionDisplayTerminologyAssembler;
import com.zxplat.application.shared.report.ReportChargeBillSummarizeScopeSupport;
import com.zxplat.application.shared.report.ReportDateTimeWindowSupport;
import com.zxplat.application.shared.report.ReportPatientSettlementContextAssembler;
import com.zxplat.domain.share.product.dto.ProductDefinitionDisplayDto;
import com.zxplat.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 住院费用明细报表读路径 scope 预解析（B.6.1；AS-RPT-009）。
 *
 * <p>本类只读、不写库。<strong>明细+汇总+导出统一 ChargeItem-only</strong>：已结按
 * {@code entered_date}；待结列与日窗裁剪强制 {@code Procedure.recorded_time}（诊疗/药品执行；耗材发放展示；滚费 occurrence）。
 * 禁止扫 Payment；退费不入统计。
 * ChargeItem 批量读委托 ADM-011 QueryService；产品展示经
 * {@link ProductDefinitionDisplayTerminologyAssembler} 批量加载。</p>
 * <p>细分类取定价行 {@code type_code}（fin_type_code），与日结 fin_type 字典码一致。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientFeeDetailReportQuerySupport {

    private final ChargeItemQueryService chargeItemQueryService;
    private final EncounterQueryService encounterQueryService;
    private final PatientQueryService patientQueryService;
    private final ProcedureQueryService procedureQueryService;
    private final DeviceDispenseQueryService deviceDispenseQueryService;
    private final ProductDefinitionDisplayTerminologyAssembler productDefinitionDisplayTerminologyAssembler;
    private final DailySettlementFinTypeAmountLoader dailySettlementFinTypeAmountLoader;

    /** 费用性质（账单账户）+ medType（主诊断）报表上下文。 */
    private final ReportPatientSettlementContextAssembler reportPatientSettlementContextAssembler;

    /** 账单行合成读侧链路上下文（无真实 paymentId）。 */
    private final ReportChargeBillSummarizeScopeSupport reportChargeBillSummarizeScopeSupport;

    /**
     * 明细分页/导出：按账单收费时间窗预解析 scope（与汇总同口径，禁止扫 Payment）。
     *
     * <p>导出/兼容仍可走全日窗路径；明细分页请改用 {@link #prepareInpatientFeeDetailPageScopes}。</p>
     *
     * @param query 须含 {@code billDateFrom}/{@code billDateTo}
     * @throws IllegalArgumentException {@code query} 为空
     */
    public void prepareInpatientFeeDetailQueryScopes(OutpatientFeeDetailPageQuery query) {
        this.prepareInpatientFeeDetailScopesFromChargeBill(query);
    }

    /**
     * 明细分页专路径：禁止 {@link #loadInpatientFeeDetailWindowChargeItems} 满窗已结实体预载。
     *
     * <p>仅已结：与门诊同构（DB encounter 分页 + 本页装载）。含待结：待结按 recorded 轴收集（通常远小于满月已结），
     * 已结仅 distinct Long / 本页 IN；待结客户可见时间裁剪在本页实体上完成。</p>
     *
     * @param query 须含日期窗与分页键
     */
    public void prepareInpatientFeeDetailPageScopes(OutpatientFeeDetailPageQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("inpatient fee detail query is required");
        }
        if (query.getBillDateFrom() == null || query.getBillDateTo() == null) {
            throw new IllegalArgumentException("billDateFrom and billDateTo are required");
        }
        // Gate：退费 Tab 空结果
        if (OutpatientFeeDetailConstants.TRADE_DIRECTION_REFUND.equals(query.getTradeDirection())) {
            this.clearInpatientFeeDetailScopesToEmpty(query);
            query.setPageScopeOnly(Boolean.TRUE);
            query.setResolvedEncounterPageTotal(0L);
            query.setResolvedFeeDetailMatchedEncounterIds(List.of());
            query.setResolvedSaleAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
            return;
        }
        List<String> allowedStatusCodes = this.resolveAllowedChargeItemStatusCodes(query);
        query.setAllowedChargeItemStatusCodes(allowedStatusCodes);
        boolean includeSettled = false;
        boolean includePending = false;
        for (String statusCode : allowedStatusCodes) {
            if (ChargeItemStatus.SETTLED.getCode().equals(statusCode)) {
                includeSettled = true;
            }
            if (ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(statusCode)) {
                includePending = true;
            }
        }
        // 仅已结：与门诊同构 DB 分页
        if (includeSettled && !includePending) {
            this.prepareInpatientSettledOnlyPageScopes(query, allowedStatusCodes);
            return;
        }
        // 含待结 / 仅待结：已结不装满月实体；待结按 recorded 轴收集后仅本页保留实体
        this.prepareInpatientMixedStatusPageScopes(query, allowedStatusCodes, includeSettled, includePending);
    }

    /**
     * 汇总 Tab：与明细分页同路径（ChargeItem-only + 结算状态白名单）。
     *
     * @param query 须含日期窗
     */
    public void prepareInpatientFeeDetailSummarizeScopesFromChargeBill(OutpatientFeeDetailPageQuery query) {
        this.prepareInpatientFeeDetailScopesFromChargeBill(query);
    }

    /**
     * 住院仅已结明细分页：DB encounter 分页 + 本页装载（同构门诊）。
     *
     * @param query              查询
     * @param allowedStatusCodes 状态白名单（仅 SETTLED）
     */
    private void prepareInpatientSettledOnlyPageScopes(
        OutpatientFeeDetailPageQuery query,
        List<String> allowedStatusCodes) {
        LocalDateTime enteredFrom = query.getBillDateFrom();
        LocalDateTime enteredToExclusive =
            ReportDateTimeWindowSupport.toExclusiveEnd(query.getBillDateTo());
        List<String> serviceTables =
            this.chargeItemQueryService.resolveServiceTablesForFeeCategories(query.getFeeCategoryCodes());
        List<String> serviceTableFilter = null;
        if (serviceTables != null && !serviceTables.isEmpty()) {
            serviceTableFilter = serviceTables;
        }
        List<Long> windowEncounterIds =
            this.chargeItemQueryService.listDistinctEncounterIdsByEnteredDateWindow(
                enteredFrom, enteredToExclusive, allowedStatusCodes, query.getEntererMatchIds());
        Set<Long> impEncounterIds = this.filterEncounterIdsByClass(
            new LinkedHashSet<>(windowEncounterIds), EncounterClass.IMP);
        Set<Long> allowedEncounterIds = this.resolveAllowedEncounterIds(query, impEncounterIds);
        if (allowedEncounterIds.isEmpty()) {
            this.writeEmptyInpatientPageScopes(query);
            return;
        }
        boolean needChargeLevelHardFilter = this.needsInpatientChargeLevelHardFilter(query);
        List<Long> matchedEncounterIds;
        List<FeeDetailChargeItemFilterProjection> matchedProjections = List.of();
        BigDecimal projectionSaleSum = null;
        if (needChargeLevelHardFilter) {
            List<FeeDetailChargeItemFilterProjection> projections =
                this.chargeItemQueryService.listFeeDetailChargeItemFilterProjections(
                    enteredFrom,
                    enteredToExclusive,
                    allowedStatusCodes,
                    query.getEntererMatchIds(),
                    query.getRequestingOrgId(),
                    allowedEncounterIds,
                    serviceTableFilter);
            matchedProjections = this.filterInpatientProjectionsByHardFilters(projections, query);
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
            this.writeEmptyInpatientPageScopes(query);
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
        Page<Long> encounterPage =
            this.chargeItemQueryService.pageFeeDetailEncounterIdsByEnteredDateWindow(
                enteredFrom,
                enteredToExclusive,
                allowedStatusCodes,
                query.getEntererMatchIds(),
                query.getRequestingOrgId(),
                matchedEncounterIds,
                serviceTableFilter,
                pageNoZeroBased,
                pageSize);
        query.setPageNo(encounterPage.getNumber() + 1);
        query.setResolvedEncounterPageTotal(encounterPage.getTotalElements());
        query.setResolvedFeeDetailMatchedEncounterIds(matchedEncounterIds);
        query.setPageScopeOnly(Boolean.TRUE);
        query.setResolvedSaleAmountTotal(projectionSaleSum);
        List<Long> pageEncounterIds = encounterPage.getContent();
        if (pageEncounterIds == null || pageEncounterIds.isEmpty()) {
            this.clearInpatientFeeDetailScopesToEmpty(query);
            query.setPageScopeOnly(Boolean.TRUE);
            query.setResolvedEncounterPageTotal(encounterPage.getTotalElements());
            query.setResolvedFeeDetailMatchedEncounterIds(matchedEncounterIds);
            query.setResolvedSaleAmountTotal(projectionSaleSum);
            return;
        }
        List<ChargeItem> pageChargeItems;
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
                allowedStatusCodes,
                query.getEntererMatchIds(),
                query.getRequestingOrgId());
        }
        this.writeInpatientPageScopeFromChargeItems(query, pageChargeItems, pageEncounterIds);
    }

    /**
     * 含待结的明细分页：禁止满窗已结实体；待结按 recorded 轴收集后仅本页装载。
     *
     * @param query              查询
     * @param allowedStatusCodes 状态白名单
     * @param includeSettled     是否含已结
     * @param includePending     是否含待结
     */
    private void prepareInpatientMixedStatusPageScopes(
        OutpatientFeeDetailPageQuery query,
        List<String> allowedStatusCodes,
        boolean includeSettled,
        boolean includePending) {
        LocalDateTime windowFrom = query.getBillDateFrom();
        LocalDateTime windowToExclusive =
            ReportDateTimeWindowSupport.toExclusiveEnd(query.getBillDateTo());
        List<String> serviceTables =
            this.chargeItemQueryService.resolveServiceTablesForFeeCategories(query.getFeeCategoryCodes());
        List<String> serviceTableFilter = null;
        if (serviceTables != null && !serviceTables.isEmpty()) {
            serviceTableFilter = serviceTables;
        }
        LinkedHashSet<Long> candidateEncounterIds = new LinkedHashSet<>();
        Map<Long, LocalDateTime> sortKeyByEncounter = new HashMap<>();
        // Pre：已结仅 distinct Long + 排序键投影，禁止 listSettled 满窗实体
        if (includeSettled) {
            List<Long> settledEncounterIds =
                this.chargeItemQueryService.listDistinctEncounterIdsByEnteredDateWindow(
                    windowFrom,
                    windowToExclusive,
                    List.of(ChargeItemStatus.SETTLED.getCode()),
                    query.getEntererMatchIds());
            candidateEncounterIds.addAll(settledEncounterIds);
            List<FeeDetailChargeItemFilterProjection> settledProjections =
                this.chargeItemQueryService.listFeeDetailChargeItemFilterProjections(
                    windowFrom,
                    windowToExclusive,
                    List.of(ChargeItemStatus.SETTLED.getCode()),
                    query.getEntererMatchIds(),
                    query.getRequestingOrgId(),
                    settledEncounterIds,
                    serviceTableFilter);
            for (FeeDetailChargeItemFilterProjection projection : settledProjections) {
                if (projection == null || projection.getEncounterId() == null) {
                    continue;
                }
                // 排序键：同就诊取最新 entered_date（投影未带时间时用窗起点占位，后续本页以 billDate 为准）
                LocalDateTime current = sortKeyByEncounter.get(projection.getEncounterId());
                if (current == null) {
                    sortKeyByEncounter.put(projection.getEncounterId(), windowFrom);
                }
            }
        }
        LinkedHashMap<Long, ChargeItem> pendingById = new LinkedHashMap<>();
        Map<Long, LocalDateTime> pendingBillDateById = new HashMap<>();
        if (includePending) {
            this.appendPendingChargeItemsByRecordedAxis(
                windowFrom, windowToExclusive, pendingById, pendingBillDateById);
            for (ChargeItem pendingItem : pendingById.values()) {
                if (pendingItem == null || pendingItem.getEncounterId() == null) {
                    continue;
                }
                if (query.getRequestingOrgId() != null
                    && !query.getRequestingOrgId().equals(pendingItem.getRequestingOrgId())) {
                    continue;
                }
                if (serviceTableFilter != null
                    && (pendingItem.getServiceTable() == null
                    || !serviceTableFilter.contains(pendingItem.getServiceTable()))) {
                    continue;
                }
                candidateEncounterIds.add(pendingItem.getEncounterId());
                LocalDateTime pendingTime = pendingBillDateById.get(pendingItem.getId());
                if (pendingTime == null) {
                    pendingTime = pendingItem.getOccurrenceTime();
                }
                LocalDateTime current = sortKeyByEncounter.get(pendingItem.getEncounterId());
                if (current == null || (pendingTime != null && pendingTime.isAfter(current))) {
                    sortKeyByEncounter.put(pendingItem.getEncounterId(), pendingTime);
                }
            }
        }
        Set<Long> impEncounterIds = this.filterEncounterIdsByClass(candidateEncounterIds, EncounterClass.IMP);
        Set<Long> allowedEncounterIds = this.resolveAllowedEncounterIds(query, impEncounterIds);
        if (allowedEncounterIds.isEmpty()) {
            this.writeEmptyInpatientPageScopes(query);
            return;
        }
        // 行级硬筛：已结用投影；待结用已收集实体子集（非满月已结）
        boolean needChargeLevelHardFilter = this.needsInpatientChargeLevelHardFilter(query);
        LinkedHashSet<Long> matchedEncounterSet = new LinkedHashSet<>();
        LinkedHashSet<Long> matchedSettledChargeItemIds = new LinkedHashSet<>();
        LinkedHashSet<Long> matchedPendingChargeItemIds = new LinkedHashSet<>();
        BigDecimal matchedSaleSum = BigDecimal.ZERO;
        if (needChargeLevelHardFilter) {
            if (includeSettled) {
                List<FeeDetailChargeItemFilterProjection> settledProjections =
                    this.chargeItemQueryService.listFeeDetailChargeItemFilterProjections(
                        windowFrom,
                        windowToExclusive,
                        List.of(ChargeItemStatus.SETTLED.getCode()),
                        query.getEntererMatchIds(),
                        query.getRequestingOrgId(),
                        allowedEncounterIds,
                        serviceTableFilter);
                List<FeeDetailChargeItemFilterProjection> matchedSettled =
                    this.filterInpatientProjectionsByHardFilters(settledProjections, query);
                for (FeeDetailChargeItemFilterProjection projection : matchedSettled) {
                    if (projection == null || projection.getId() == null || projection.getEncounterId() == null) {
                        continue;
                    }
                    matchedEncounterSet.add(projection.getEncounterId());
                    matchedSettledChargeItemIds.add(projection.getId());
                    if (projection.getTotalPrice() != null) {
                        matchedSaleSum = matchedSaleSum.add(projection.getTotalPrice());
                    }
                }
            }
            if (includePending && !pendingById.isEmpty()) {
                List<ChargeItem> pendingCandidates = new ArrayList<>();
                for (ChargeItem pendingItem : pendingById.values()) {
                    if (pendingItem == null || pendingItem.getEncounterId() == null) {
                        continue;
                    }
                    if (!allowedEncounterIds.contains(pendingItem.getEncounterId())) {
                        continue;
                    }
                    pendingCandidates.add(pendingItem);
                }
                List<ChargeItem> matchedPending =
                    this.filterInpatientChargeItemsByHardFilters(pendingCandidates, query);
                for (ChargeItem pendingItem : matchedPending) {
                    if (pendingItem == null || pendingItem.getId() == null || pendingItem.getEncounterId() == null) {
                        continue;
                    }
                    matchedEncounterSet.add(pendingItem.getEncounterId());
                    matchedPendingChargeItemIds.add(pendingItem.getId());
                    if (pendingItem.getTotalPrice() != null) {
                        matchedSaleSum = matchedSaleSum.add(pendingItem.getTotalPrice());
                    }
                }
            }
        } else {
            matchedEncounterSet.addAll(allowedEncounterIds);
            if (includeSettled) {
                List<FeeDetailChargeItemFilterProjection> settledProjections =
                    this.chargeItemQueryService.listFeeDetailChargeItemFilterProjections(
                        windowFrom,
                        windowToExclusive,
                        List.of(ChargeItemStatus.SETTLED.getCode()),
                        query.getEntererMatchIds(),
                        query.getRequestingOrgId(),
                        allowedEncounterIds,
                        serviceTableFilter);
                for (FeeDetailChargeItemFilterProjection projection : settledProjections) {
                    if (projection == null || projection.getId() == null) {
                        continue;
                    }
                    matchedSettledChargeItemIds.add(projection.getId());
                    if (projection.getTotalPrice() != null) {
                        matchedSaleSum = matchedSaleSum.add(projection.getTotalPrice());
                    }
                }
            }
            if (includePending) {
                for (ChargeItem pendingItem : pendingById.values()) {
                    if (pendingItem == null || pendingItem.getId() == null || pendingItem.getEncounterId() == null) {
                        continue;
                    }
                    if (!allowedEncounterIds.contains(pendingItem.getEncounterId())) {
                        continue;
                    }
                    if (query.getRequestingOrgId() != null
                        && !query.getRequestingOrgId().equals(pendingItem.getRequestingOrgId())) {
                        continue;
                    }
                    if (serviceTableFilter != null
                        && (pendingItem.getServiceTable() == null
                        || !serviceTableFilter.contains(pendingItem.getServiceTable()))) {
                        continue;
                    }
                    matchedPendingChargeItemIds.add(pendingItem.getId());
                    if (pendingItem.getTotalPrice() != null) {
                        matchedSaleSum = matchedSaleSum.add(pendingItem.getTotalPrice());
                    }
                }
            }
        }
        if (matchedEncounterSet.isEmpty()) {
            this.writeEmptyInpatientPageScopes(query);
            return;
        }
        List<Long> matchedEncounterIds = new ArrayList<>(matchedEncounterSet);
        // Asm：混合轴按 sortKey 倒序内存分页（待结无 entered_date，无法单靠 SQL GROUP BY）
        matchedEncounterIds.sort((leftId, rightId) -> {
            LocalDateTime left = sortKeyByEncounter.get(leftId);
            LocalDateTime right = sortKeyByEncounter.get(rightId);
            if (left == null && right == null) {
                return 0;
            }
            if (left == null) {
                return 1;
            }
            if (right == null) {
                return -1;
            }
            return right.compareTo(left);
        });
        int pageNoZeroBased = 0;
        if (query.getPageNo() != null && query.getPageNo() >= 1) {
            pageNoZeroBased = query.springPageIndex();
        }
        int pageSize = OutpatientFeeDetailConstants.DEFAULT_PAGE_SIZE;
        if (query.getPageSize() != null && query.getPageSize() > 0) {
            pageSize = query.getBoundedPageSize();
        }
        long total = matchedEncounterIds.size();
        if (total > 0L && pageSize > 0) {
            int maxPageIndex = (int) ((total - 1L) / pageSize);
            if (pageNoZeroBased > maxPageIndex) {
                pageNoZeroBased = maxPageIndex;
            }
        }
        int fromIndex = pageNoZeroBased * pageSize;
        List<Long> pageEncounterIds;
        if (fromIndex >= matchedEncounterIds.size()) {
            pageEncounterIds = List.of();
        } else {
            int toIndex = Math.min(fromIndex + pageSize, matchedEncounterIds.size());
            pageEncounterIds = new ArrayList<>(matchedEncounterIds.subList(fromIndex, toIndex));
        }
        query.setPageNo(pageNoZeroBased + 1);
        query.setResolvedEncounterPageTotal(total);
        query.setResolvedFeeDetailMatchedEncounterIds(matchedEncounterIds);
        query.setPageScopeOnly(Boolean.TRUE);
        query.setResolvedSaleAmountTotal(MonetaryAmountSupport.scaleSettle(matchedSaleSum));
        // Asm：混合轴命中 chargeItemId 全集（已结+待结），供页脚进价
        LinkedHashSet<Long> matchedAllChargeItemIds = new LinkedHashSet<>();
        matchedAllChargeItemIds.addAll(matchedSettledChargeItemIds);
        matchedAllChargeItemIds.addAll(matchedPendingChargeItemIds);
        query.setResolvedFeeDetailMatchedChargeItemIds(new ArrayList<>(matchedAllChargeItemIds));
        if (pageEncounterIds.isEmpty()) {
            this.clearInpatientFeeDetailScopesToEmpty(query);
            query.setPageScopeOnly(Boolean.TRUE);
            query.setResolvedEncounterPageTotal(total);
            query.setResolvedFeeDetailMatchedEncounterIds(matchedEncounterIds);
            query.setResolvedFeeDetailMatchedChargeItemIds(new ArrayList<>(matchedAllChargeItemIds));
            query.setResolvedSaleAmountTotal(MonetaryAmountSupport.scaleSettle(matchedSaleSum));
            return;
        }
        LinkedHashSet<Long> pageEncounterSet = new LinkedHashSet<>(pageEncounterIds);
        LinkedHashMap<Long, ChargeItem> pageById = new LinkedHashMap<>();
        // Pre：本页已结按 encounter IN 装实体（或硬筛命中 id）
        if (includeSettled) {
            if (needChargeLevelHardFilter) {
                LinkedHashSet<Long> pageSettledIds = new LinkedHashSet<>();
                for (Long chargeItemId : matchedSettledChargeItemIds) {
                    // 需装载后知 encounter；先批量装再滤本页
                    pageSettledIds.add(chargeItemId);
                }
                if (!pageSettledIds.isEmpty()) {
                    ChargeItemQueryCommand loadCmd = new ChargeItemQueryCommand();
                    loadCmd.setIds(new ArrayList<>(pageSettledIds));
                    List<ChargeItem> settledRows = this.chargeItemQueryService.loadChargeItemsByIds(loadCmd);
                    for (ChargeItem settledRow : settledRows) {
                        if (settledRow == null || settledRow.getId() == null || settledRow.getEncounterId() == null) {
                            continue;
                        }
                        if (!pageEncounterSet.contains(settledRow.getEncounterId())) {
                            continue;
                        }
                        pageById.put(settledRow.getId(), settledRow);
                    }
                }
            } else {
                List<ChargeItem> settledPageRows =
                    this.chargeItemQueryService.listChargeItemsByEncounterIdsInEnteredDateWindow(
                        pageEncounterIds,
                        windowFrom,
                        windowToExclusive,
                        List.of(ChargeItemStatus.SETTLED.getCode()),
                        query.getEntererMatchIds(),
                        query.getRequestingOrgId());
                for (ChargeItem settledRow : settledPageRows) {
                    if (settledRow != null && settledRow.getId() != null) {
                        if (serviceTableFilter != null
                            && (settledRow.getServiceTable() == null
                            || !serviceTableFilter.contains(settledRow.getServiceTable()))) {
                            continue;
                        }
                        pageById.put(settledRow.getId(), settledRow);
                    }
                }
            }
        }
        // Pre：本页待结从 recorded 轴子集取，禁止再拉满窗已结
        if (includePending) {
            for (Long pendingId : matchedPendingChargeItemIds) {
                ChargeItem pendingItem = pendingById.get(pendingId);
                if (pendingItem == null || pendingItem.getEncounterId() == null) {
                    continue;
                }
                if (!pageEncounterSet.contains(pendingItem.getEncounterId())) {
                    continue;
                }
                pageById.put(pendingItem.getId(), pendingItem);
            }
        }
        List<ChargeItem> pageChargeItems = new ArrayList<>(pageById.values());
        Map<Long, OutpatientFeeDetailPaymentContextQueryModel> contextByChargeItemId =
            this.reportChargeBillSummarizeScopeSupport.buildSyntheticPaymentContextByChargeItemId(pageChargeItems);
        this.applyPendingCustomerVisibleBillDates(pageChargeItems, contextByChargeItemId);
        // 覆盖 appendPending 已解析的 recorded 轴（与全日窗路径一致）
        for (Map.Entry<Long, LocalDateTime> entry : pendingBillDateById.entrySet()) {
            if (!pageById.containsKey(entry.getKey())) {
                continue;
            }
            OutpatientFeeDetailPaymentContextQueryModel context = contextByChargeItemId.get(entry.getKey());
            if (context != null && entry.getValue() != null) {
                context.setBillDate(entry.getValue());
            }
        }
        List<ChargeItem> windowAlignedChargeItems = this.filterChargeItemsByFinalBillDateWindow(
            pageChargeItems, contextByChargeItemId, windowFrom, windowToExclusive);
        Map<Long, OutpatientFeeDetailPaymentContextQueryModel> windowAlignedContexts =
            this.filterContextsByChargeItems(contextByChargeItemId, windowAlignedChargeItems);
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> windowAlignedLinks =
            this.buildPaymentLinksFromContexts(windowAlignedChargeItems, windowAlignedContexts);
        Map<Long, ChargeItem> preloadedById = new LinkedHashMap<>();
        LinkedHashSet<Long> resolvedChargeItemIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : windowAlignedChargeItems) {
            if (chargeItem != null && chargeItem.getId() != null) {
                preloadedById.put(chargeItem.getId(), chargeItem);
                resolvedChargeItemIds.add(chargeItem.getId());
            }
        }
        query.setPaymentChargeItemLinks(windowAlignedLinks);
        query.setPaymentContextByChargeItemId(windowAlignedContexts);
        query.setPreloadedChargeItemsById(preloadedById);
        query.setPaymentsInScope(List.of());
        query.setResolvedChargeItemScope(new ArrayList<>(resolvedChargeItemIds));
        query.setResolvedEncounterScope(new ArrayList<>(pageEncounterIds));
        query.setResolvedPaymentChargeItemLinks(windowAlignedLinks);
        query.setCachedChargeRows(null);
    }

    /**
     * 将本页账单写入 pageScope 字段。
     *
     * @param query            查询
     * @param pageChargeItems  本页实体
     * @param pageEncounterIds 本页就诊
     */
    private void writeInpatientPageScopeFromChargeItems(
        OutpatientFeeDetailPageQuery query,
        List<ChargeItem> pageChargeItems,
        List<Long> pageEncounterIds) {
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> links =
            this.reportChargeBillSummarizeScopeSupport.buildSyntheticPaymentLinks(pageChargeItems);
        Map<Long, ChargeItem> preloadedById = new LinkedHashMap<>();
        LinkedHashSet<Long> chargeItemIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : pageChargeItems) {
            if (chargeItem != null && chargeItem.getId() != null) {
                preloadedById.put(chargeItem.getId(), chargeItem);
                chargeItemIds.add(chargeItem.getId());
            }
        }
        query.setPaymentChargeItemLinks(links);
        query.setPaymentContextByChargeItemId(
            this.reportChargeBillSummarizeScopeSupport.buildSyntheticPaymentContextByChargeItemId(pageChargeItems));
        query.setPreloadedChargeItemsById(preloadedById);
        query.setPaymentsInScope(List.of());
        query.setResolvedChargeItemScope(new ArrayList<>(chargeItemIds));
        query.setResolvedEncounterScope(new ArrayList<>(pageEncounterIds));
        query.setResolvedPaymentChargeItemLinks(links);
        query.setCachedChargeRows(null);
    }

    /**
     * 写入空明细分页 scope。
     *
     * @param query 查询
     */
    private void writeEmptyInpatientPageScopes(OutpatientFeeDetailPageQuery query) {
        this.clearInpatientFeeDetailScopesToEmpty(query);
        query.setPageScopeOnly(Boolean.TRUE);
        query.setResolvedEncounterPageTotal(0L);
        query.setResolvedFeeDetailMatchedEncounterIds(List.of());
        query.setResolvedFeeDetailMatchedChargeItemIds(List.of());
        query.setResolvedSaleAmountTotal(MonetaryAmountSupport.scaleSettle(BigDecimal.ZERO));
    }

    /**
     * 清空住院费用明细 scope 字段。
     *
     * @param query 查询
     */
    private void clearInpatientFeeDetailScopesToEmpty(OutpatientFeeDetailPageQuery query) {
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
     * 是否需要行级硬筛选。
     *
     * @param query 查询
     * @return 需要时 true
     */
    private boolean needsInpatientChargeLevelHardFilter(OutpatientFeeDetailPageQuery query) {
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
     * 投影行级硬筛（项目/细分类/费用性质）。
     *
     * @param projections 投影
     * @param query       查询
     * @return 命中投影
     */
    private List<FeeDetailChargeItemFilterProjection> filterInpatientProjectionsByHardFilters(
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
            // Pre：硬筛 stub 仅填 fin_type/关键字/费用性质所需字段；不设 unit_price（投影未选）
            ChargeItem stub = new ChargeItem();
            stub.setId(projection.getId());
            stub.setEncounterId(projection.getEncounterId());
            stub.setProductTable(projection.getProductTable());
            stub.setProductId(projection.getProductId());
            stub.setAccountId(projection.getAccountId());
            stub.setDefinitionId(projection.getDefinitionId());
            // Pre：挂号费无 product_* 时靠 generate_source/service_table 回落 healthcare_service 锚点
            stub.setGenerateSourceEnum(projection.getGenerateSourceEnum());
            stub.setServiceTable(projection.getServiceTable());
            stub.setTotalPrice(projection.getTotalPrice());
            stubs.add(stub);
            projectionById.put(projection.getId(), projection);
        }
        List<ChargeItem> matchedStubs = this.filterInpatientChargeItemsByHardFilters(stubs, query);
        List<FeeDetailChargeItemFilterProjection> matched = new ArrayList<>();
        for (ChargeItem stub : matchedStubs) {
            if (stub == null || stub.getId() == null) {
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
     * 实体行级硬筛（项目/细分类/费用性质）。
     *
     * @param chargeItems 候选
     * @param query       查询
     * @return 命中实体
     */
    private List<ChargeItem> filterInpatientChargeItemsByHardFilters(
        List<ChargeItem> chargeItems,
        OutpatientFeeDetailPageQuery query) {
        if (chargeItems == null || chargeItems.isEmpty()) {
            return List.of();
        }
        Set<Long> feeNatureMatchedChargeItemIds =
            this.reportPatientSettlementContextAssembler.filterChargeItemIdsByAccountContractNo(
                chargeItems, query.getFeeNatureContractNo());
        if (feeNatureMatchedChargeItemIds != null && feeNatureMatchedChargeItemIds.isEmpty()) {
            return List.of();
        }
        boolean filterByProductKeyword = StringUtils.isNotEmpty(query.getProductNameKeyword())
            || StringUtils.isNotEmpty(query.getProductCodeKeyword());
        boolean filterBySubCategory = query.getSubCategoryCodes() != null
            && !query.getSubCategoryCodes().isEmpty();
        Map<String, ProductDefinitionDisplayDto> productMap = Map.of();
        if (filterByProductKeyword) {
            productMap = this.loadProductMapForChargeItems(chargeItems);
        }
        Map<Long, String> finTypeByChargeItemId = Map.of();
        if (filterBySubCategory) {
            finTypeByChargeItemId =
                this.dailySettlementFinTypeAmountLoader.mapFinTypeCodeByChargeItemId(chargeItems);
        }
        List<ChargeItem> matched = new ArrayList<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
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
            matched.add(chargeItem);
        }
        return matched;
    }

    /**
     * 住院费用明细统一 scope：{@code entered_date} 窗 + 结算状态白名单 + 住院就诊；退费 Tab 空结果。
     *
     * <p>供<strong>汇总/导出</strong>；明细分页禁止调用本方法装满月已结实体。</p>
     *
     * @param query 须含日期窗；可含收费人白名单与 {@code allowedChargeItemStatusCodes}
     */
    private void prepareInpatientFeeDetailScopesFromChargeBill(OutpatientFeeDetailPageQuery query) {
        if (query == null) {
            throw new IllegalArgumentException("inpatient fee detail query is required");
        }
        if (query.getBillDateFrom() == null || query.getBillDateTo() == null) {
            throw new IllegalArgumentException("billDateFrom and billDateTo are required");
        }
        // Gate：退费 Tab 在净口径下无行
        if (OutpatientFeeDetailConstants.TRADE_DIRECTION_REFUND.equals(query.getTradeDirection())) {
            this.clearInpatientFeeDetailScopesToEmpty(query);
            return;
        }
        List<String> allowedStatusCodes = this.resolveAllowedChargeItemStatusCodes(query);
        query.setAllowedChargeItemStatusCodes(allowedStatusCodes);
        LocalDateTime windowFrom = query.getBillDateFrom();
        LocalDateTime windowToExclusive =
            ReportDateTimeWindowSupport.toExclusiveEnd(query.getBillDateTo());
        // Pre：已结 entered_date；待结 recorded_time / 发放展示 / 滚费 occurrence
        WindowChargeLoadResult windowLoad = this.loadInpatientFeeDetailWindowChargeItems(
            windowFrom, windowToExclusive, query.getEntererMatchIds(), allowedStatusCodes);
        List<ChargeItem> windowChargeItems = windowLoad.chargeItems;
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        for (ChargeItem chargeItem : windowChargeItems) {
            if (chargeItem != null && chargeItem.getEncounterId() != null) {
                encounterIds.add(chargeItem.getEncounterId());
            }
        }
        Set<Long> impEncounterIds = this.filterEncounterIdsByClass(encounterIds, EncounterClass.IMP);
        List<ChargeItem> impChargeItems = new ArrayList<>();
        for (ChargeItem chargeItem : windowChargeItems) {
            if (chargeItem == null || chargeItem.getId() == null || chargeItem.getEncounterId() == null) {
                continue;
            }
            if (!impEncounterIds.contains(chargeItem.getEncounterId())) {
                continue;
            }
            impChargeItems.add(chargeItem);
        }
        Map<Long, OutpatientFeeDetailPaymentContextQueryModel> contextByChargeItemId =
            this.reportChargeBillSummarizeScopeSupport.buildSyntheticPaymentContextByChargeItemId(impChargeItems);
        // Asm：待结列业务时间强制 Procedure.recorded / 耗材发放 / 滚费 occurrence；已结保持 entered_date
        this.applyPendingCustomerVisibleBillDates(impChargeItems, contextByChargeItemId);
        // Asm：最终列业务时间须落在查询日窗（待结按 recorded 裁；已结按 entered_date 裁）
        List<ChargeItem> windowAlignedChargeItems = this.filterChargeItemsByFinalBillDateWindow(
            impChargeItems, contextByChargeItemId, windowFrom, windowToExclusive);
        Map<Long, OutpatientFeeDetailPaymentContextQueryModel> windowAlignedContexts =
            this.filterContextsByChargeItems(contextByChargeItemId, windowAlignedChargeItems);
        // Asm：链路必须复用已覆盖的 context，禁止再 buildSynthetic（会把待结 billDate 冲回 occurrence）
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> windowAlignedLinks =
            this.buildPaymentLinksFromContexts(windowAlignedChargeItems, windowAlignedContexts);
        Map<Long, ChargeItem> preloadedById = new LinkedHashMap<>();
        for (ChargeItem chargeItem : windowAlignedChargeItems) {
            if (chargeItem != null && chargeItem.getId() != null) {
                preloadedById.put(chargeItem.getId(), chargeItem);
            }
        }
        query.setPaymentChargeItemLinks(windowAlignedLinks);
        query.setPaymentContextByChargeItemId(windowAlignedContexts);
        query.setPreloadedChargeItemsById(preloadedById);
        query.setPaymentsInScope(List.of());
        query.setPageScopeOnly(Boolean.FALSE);
        this.narrowInpatientFeeDetailScopesAfterCandidates(query);
    }

    /**
     * 用已定稿的 payment 上下文组装 charge↔payment 链路（保留待结 recorded 轴）。
     *
     * @param chargeItems             裁剪后账单
     * @param contextByChargeItemId   已覆盖 billDate 的上下文
     * @return 链路列表
     */
    private List<OutpatientFeeDetailChargePaymentLinkQueryModel> buildPaymentLinksFromContexts(
        List<ChargeItem> chargeItems,
        Map<Long, OutpatientFeeDetailPaymentContextQueryModel> contextByChargeItemId) {
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> links = new ArrayList<>();
        if (chargeItems == null || chargeItems.isEmpty() || contextByChargeItemId == null) {
            return links;
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            OutpatientFeeDetailPaymentContextQueryModel context = contextByChargeItemId.get(chargeItem.getId());
            if (context == null) {
                continue;
            }
            OutpatientFeeDetailChargePaymentLinkQueryModel link =
                new OutpatientFeeDetailChargePaymentLinkQueryModel();
            link.setChargeItemId(chargeItem.getId());
            link.setPaymentContext(context);
            links.add(link);
        }
        return links;
    }

    /**
     * 待结账单「业务时间」列强制客户可见轴（已结不改，仍用 {@code entered_date}）。
     *
     * <p>有 Procedure → 当前 {@code recorded_time}；护士划价耗材 → 发放展示时刻；滚费 → {@code occurrence_time}。
     * 待结不得回落 occurrence 给用户（除滚费）。</p>
     *
     * @param chargeItems             候选账单（含已结+待结）
     * @param contextByChargeItemId   合成上下文（待结 billDate 被覆盖）
     */
    private void applyPendingCustomerVisibleBillDates(
        List<ChargeItem> chargeItems,
        Map<Long, OutpatientFeeDetailPaymentContextQueryModel> contextByChargeItemId) {
        if (chargeItems == null || chargeItems.isEmpty() || contextByChargeItemId == null) {
            return;
        }
        String pendingCode = ChargeItemStatus.PENDING_SETTLEMENT.getCode();
        LinkedHashSet<Long> procedureIds = new LinkedHashSet<>();
        LinkedHashSet<Long> nursePricingServiceReqIds = new LinkedHashSet<>();
        LinkedHashSet<Long> nursePricingDeviceReqIds = new LinkedHashSet<>();
        LinkedHashSet<Long> encounterIdsForNursePricing = new LinkedHashSet<>();
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            // 已结：列时间保持 entered_date，不覆盖
            if (!pendingCode.equals(chargeItem.getStatusEnum())) {
                continue;
            }
            if (chargeItem.getProcedureId() != null) {
                procedureIds.add(chargeItem.getProcedureId());
            }
            if (!GenerateSource.NURSE_PRICING.getCode().equals(chargeItem.getGenerateSourceEnum())
                && !GenerateSource.MEDICAL_TECH_PRICING.getCode().equals(chargeItem.getGenerateSourceEnum())) {
                continue;
            }
            if (chargeItem.getServiceId() == null) {
                continue;
            }
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(chargeItem.getServiceTable())) {
                nursePricingServiceReqIds.add(chargeItem.getServiceId());
                if (chargeItem.getEncounterId() != null) {
                    encounterIdsForNursePricing.add(chargeItem.getEncounterId());
                }
            } else if (TableNameConstants.WKF_DEVICE_REQUEST.equals(chargeItem.getServiceTable())) {
                nursePricingDeviceReqIds.add(chargeItem.getServiceId());
            }
        }
        Map<Long, LocalDateTime> recordedByProcedureId = new HashMap<>();
        if (!procedureIds.isEmpty()) {
            List<Procedure> procedures = this.procedureQueryService.listByIds(procedureIds);
            for (Procedure procedure : procedures) {
                if (procedure == null || procedure.getId() == null || procedure.getRecordedTime() == null) {
                    continue;
                }
                recordedByProcedureId.put(procedure.getId(), procedure.getRecordedTime());
            }
        }
        Map<Long, LocalDateTime> recordedByServiceReqId =
            this.procedureQueryService.mapNetCompletedRecordedTimeByServiceRequestIds(
                encounterIdsForNursePricing, nursePricingServiceReqIds);
        Map<Long, LocalDateTime> deviceDisplayByReqId =
            this.deviceDispenseQueryService.mapPrimaryDisplayTimeByDeviceReqIds(nursePricingDeviceReqIds);
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            if (!pendingCode.equals(chargeItem.getStatusEnum())) {
                continue;
            }
            OutpatientFeeDetailPaymentContextQueryModel context = contextByChargeItemId.get(chargeItem.getId());
            if (context == null) {
                continue;
            }
            LocalDateTime customerTime = this.resolvePendingCustomerVisibleTime(
                chargeItem, recordedByProcedureId, recordedByServiceReqId, deviceDisplayByReqId);
            // 待结无法解析客户轴：清空 billDate，后续日窗裁剪剔除（禁止 occurrence 糊弄用户）
            if (customerTime == null) {
                context.setBillDate(null);
                continue;
            }
            context.setBillDate(customerTime);
        }
    }

    /**
     * 解析待结单行客户可见业务时刻。
     *
     * @param chargeItem              待结账单
     * @param recordedByProcedureId   procedureId → recorded_time
     * @param recordedByServiceReqId  护士划价诊疗 Request → recorded
     * @param deviceDisplayByReqId    护士划价耗材 Request → 发放展示
     * @return 客户可见时刻；无法解析时 null
     */
    private LocalDateTime resolvePendingCustomerVisibleTime(
        ChargeItem chargeItem,
        Map<Long, LocalDateTime> recordedByProcedureId,
        Map<Long, LocalDateTime> recordedByServiceReqId,
        Map<Long, LocalDateTime> deviceDisplayByReqId) {
        // 滚费：无 Procedure 客户轴
        if (GenerateSource.AUTO_ROLL_FEES.getCode().equals(chargeItem.getGenerateSourceEnum())) {
            return chargeItem.getOccurrenceTime();
        }
        // 护士/医技划价耗材：发放/拣配展示时刻
        if ((GenerateSource.NURSE_PRICING.getCode().equals(chargeItem.getGenerateSourceEnum())
                || GenerateSource.MEDICAL_TECH_PRICING.getCode().equals(chargeItem.getGenerateSourceEnum()))
            && TableNameConstants.WKF_DEVICE_REQUEST.equals(chargeItem.getServiceTable())
            && deviceDisplayByReqId != null
            && chargeItem.getServiceId() != null) {
            return deviceDisplayByReqId.get(chargeItem.getServiceId());
        }
        // 优先本行 Procedure.recorded（禁止被同 Request 兄弟行 recorded 串日）
        if (chargeItem.getProcedureId() != null && recordedByProcedureId != null) {
            LocalDateTime byProcedure = recordedByProcedureId.get(chargeItem.getProcedureId());
            if (byProcedure != null) {
                return byProcedure;
            }
        }
        // 护士划价诊疗缺 procedure_id：按 Request 取净剩 recorded
        if (GenerateSource.NURSE_PRICING.getCode().equals(chargeItem.getGenerateSourceEnum())
            && TableNameConstants.WKF_SERVICE_REQUEST.equals(chargeItem.getServiceTable())
            && recordedByServiceReqId != null
            && chargeItem.getServiceId() != null) {
            return recordedByServiceReqId.get(chargeItem.getServiceId());
        }
        return null;
    }

    /**
     * 按最终「业务时间」列（context.billDate）裁剪账单，保证筛日 = 列日。
     *
     * @param chargeItems             候选账单
     * @param contextByChargeItemId   合成上下文（含最终 billDate）
     * @param windowFrom              日起（含）
     * @param windowToExclusive       日止（不含）
     * @return 列业务时间落在窗内的账单
     */
    private List<ChargeItem> filterChargeItemsByFinalBillDateWindow(
        List<ChargeItem> chargeItems,
        Map<Long, OutpatientFeeDetailPaymentContextQueryModel> contextByChargeItemId,
        LocalDateTime windowFrom,
        LocalDateTime windowToExclusive) {
        List<ChargeItem> kept = new ArrayList<>();
        if (chargeItems == null || chargeItems.isEmpty()) {
            return kept;
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            OutpatientFeeDetailPaymentContextQueryModel context = null;
            if (contextByChargeItemId != null) {
                context = contextByChargeItemId.get(chargeItem.getId());
            }
            if (context == null || context.getBillDate() == null) {
                continue;
            }
            LocalDateTime billDate = context.getBillDate();
            // 窗外：列时间与筛日不一致，剔除
            if (billDate.isBefore(windowFrom) || !billDate.isBefore(windowToExclusive)) {
                continue;
            }
            kept.add(chargeItem);
        }
        return kept;
    }

    /**
     * 仅保留仍在结果集中的账单上下文。
     *
     * @param contextByChargeItemId 原上下文
     * @param chargeItems           裁剪后账单
     * @return 子集上下文
     */
    private Map<Long, OutpatientFeeDetailPaymentContextQueryModel> filterContextsByChargeItems(
        Map<Long, OutpatientFeeDetailPaymentContextQueryModel> contextByChargeItemId,
        List<ChargeItem> chargeItems) {
        Map<Long, OutpatientFeeDetailPaymentContextQueryModel> kept = new LinkedHashMap<>();
        if (contextByChargeItemId == null || contextByChargeItemId.isEmpty()
            || chargeItems == null || chargeItems.isEmpty()) {
            return kept;
        }
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            OutpatientFeeDetailPaymentContextQueryModel context = contextByChargeItemId.get(chargeItem.getId());
            if (context != null) {
                kept.put(chargeItem.getId(), context);
            }
        }
        return kept;
    }

    /**
     * 按结算状态白名单拉住院费用明细时间窗账单。
     *
     * <p>已结：{@code entered_date}（列与窗同为结算戳）。待结：诊疗/划价 {@code Procedure.recorded_time}；
     * 耗材划价发放展示时刻；滚费 {@code occurrence_time}。装配后按最终列时间再裁一次日窗。</p>
     *
     * @param windowFrom         日起（含）
     * @param windowToExclusive  日止（不含）
     * @param entererMatchIds    收费人白名单；仅作用于已结查询
     * @param allowedStatusCodes 状态白名单
     * @return 账单行 + 待结业务时间覆盖
     */
    private WindowChargeLoadResult loadInpatientFeeDetailWindowChargeItems(
        LocalDateTime windowFrom,
        LocalDateTime windowToExclusive,
        List<Long> entererMatchIds,
        List<String> allowedStatusCodes) {
        boolean includeSettled = false;
        boolean includePending = false;
        for (String statusCode : allowedStatusCodes) {
            if (ChargeItemStatus.SETTLED.getCode().equals(statusCode)) {
                includeSettled = true;
            }
            if (ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(statusCode)) {
                includePending = true;
            }
        }
        LinkedHashMap<Long, ChargeItem> byId = new LinkedHashMap<>();
        Map<Long, LocalDateTime> billDateOverrideByChargeItemId = new HashMap<>();
        // 已结：结算戳 entered_date；收费人白名单生效
        if (includeSettled) {
            List<ChargeItem> settledRows =
                this.chargeItemQueryService.listChargeItemsByEnteredDateWindow(
                    windowFrom,
                    windowToExclusive,
                    entererMatchIds,
                    List.of(ChargeItemStatus.SETTLED.getCode()));
            for (ChargeItem chargeItem : settledRows) {
                if (chargeItem != null && chargeItem.getId() != null) {
                    byId.put(chargeItem.getId(), chargeItem);
                }
            }
        }
        if (includePending) {
            this.appendPendingChargeItemsByRecordedAxis(
                windowFrom, windowToExclusive, byId, billDateOverrideByChargeItemId);
        }
        return new WindowChargeLoadResult(new ArrayList<>(byId.values()), billDateOverrideByChargeItemId);
    }

    /**
     * 待结业务日轴：recorded_time（诊疗/药品执行与划价）+ 耗材发放展示 + 滚费 occurrence。
     *
     * @param windowFrom                       日起（含）
     * @param windowToExclusive                日止（不含）
     * @param byId                             去重账单累积
     * @param billDateOverrideByChargeItemId   业务时间覆盖累积
     */
    private void appendPendingChargeItemsByRecordedAxis(
        LocalDateTime windowFrom,
        LocalDateTime windowToExclusive,
        LinkedHashMap<Long, ChargeItem> byId,
        Map<Long, LocalDateTime> billDateOverrideByChargeItemId) {
        // 1a) 诊疗执行/划价：wkf_service_request → Procedure.recorded
        this.appendPendingByProcedureRecordedWindow(
            TableNameConstants.WKF_SERVICE_REQUEST,
            windowFrom,
            windowToExclusive,
            byId,
            billDateOverrideByChargeItemId);
        // 1b) 药品执行：med_medication_request → Procedure.recorded（禁止只扫诊疗漏待结药品）
        this.appendPendingByProcedureRecordedWindow(
            TableNameConstants.MED_MEDICATION_REQUEST,
            windowFrom,
            windowToExclusive,
            byId,
            billDateOverrideByChargeItemId);
        // 1c) 药品待结 occurrence 兜底：发药入账后 Procedure 被退药 CANCEL 对冲时，净 recorded 轴会漏仍 PENDING 的账单
        this.appendPendingMedicationByOccurrenceTimeWindow(
            windowFrom, windowToExclusive, byId, billDateOverrideByChargeItemId);
        // 2) 耗材划价：发放展示时刻落窗（dispense_time 优先，否则 prepare_time）
        this.appendPendingDevicePricingByDisplayTimeWindow(
            windowFrom, windowToExclusive, byId, billDateOverrideByChargeItemId);
        // 3) 滚费：无 recorded 轴，按 occurrence_time
        String pendingCode = ChargeItemStatus.PENDING_SETTLEMENT.getCode();
        List<ChargeItem> occurrencePending =
            this.chargeItemQueryService.listChargeItemsByOccurrenceTimeWindow(
                windowFrom,
                windowToExclusive,
                null,
                List.of(pendingCode));
        for (ChargeItem chargeItem : occurrencePending) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            if (!GenerateSource.AUTO_ROLL_FEES.getCode().equals(chargeItem.getGenerateSourceEnum())) {
                continue;
            }
            byId.put(chargeItem.getId(), chargeItem);
            if (chargeItem.getOccurrenceTime() != null) {
                billDateOverrideByChargeItemId.put(chargeItem.getId(), chargeItem.getOccurrenceTime());
            }
        }
    }

    /**
     * 按指定 Request 表的净剩 Procedure.recorded 窗反查待结账单并写入业务时间覆盖。
     *
     * @param requestTable                     来源请求表（诊疗/药品）
     * @param windowFrom                       日起（含）
     * @param windowToExclusive                日止（不含）
     * @param byId                             去重账单累积
     * @param billDateOverrideByChargeItemId   业务时间覆盖累积
     */
    private void appendPendingByProcedureRecordedWindow(
        String requestTable,
        LocalDateTime windowFrom,
        LocalDateTime windowToExclusive,
        LinkedHashMap<Long, ChargeItem> byId,
        Map<Long, LocalDateTime> billDateOverrideByChargeItemId) {
        String pendingCode = ChargeItemStatus.PENDING_SETTLEMENT.getCode();
        List<Procedure> netProcedures =
            this.procedureQueryService.listNetCompletedProceduresByRequestTableAndRecordedTimeWindow(
                requestTable, windowFrom, windowToExclusive);
        LinkedHashSet<Long> procedureIds = new LinkedHashSet<>();
        LinkedHashSet<Long> requestIds = new LinkedHashSet<>();
        Map<Long, LocalDateTime> recordedByProcedureId = new HashMap<>();
        Map<Long, LocalDateTime> recordedByRequestId = new HashMap<>();
        for (Procedure procedure : netProcedures) {
            if (procedure == null || procedure.getId() == null || procedure.getRecordedTime() == null) {
                continue;
            }
            procedureIds.add(procedure.getId());
            recordedByProcedureId.put(procedure.getId(), procedure.getRecordedTime());
            if (procedure.getRequestId() != null) {
                requestIds.add(procedure.getRequestId());
                recordedByRequestId.put(procedure.getRequestId(), procedure.getRecordedTime());
            }
        }
        if (!procedureIds.isEmpty()) {
            List<ChargeItem> byProcedure =
                this.chargeItemQueryService.listChargeItemsByProcedureIds(procedureIds, true);
            for (ChargeItem chargeItem : byProcedure) {
                if (chargeItem == null || chargeItem.getId() == null) {
                    continue;
                }
                if (!pendingCode.equals(chargeItem.getStatusEnum())) {
                    continue;
                }
                byId.put(chargeItem.getId(), chargeItem);
                LocalDateTime recorded = recordedByProcedureId.get(chargeItem.getProcedureId());
                if (recorded != null) {
                    billDateOverrideByChargeItemId.put(chargeItem.getId(), recorded);
                }
            }
        }
        if (requestIds.isEmpty()) {
            return;
        }
        // 缺 procedure_id 的待结行：按 service_table + service_id 反查
        List<ChargeItem> byRequest =
            this.chargeItemQueryService.listPendingSettlementChargeItemsByServiceTableAndServiceIds(
                requestTable, requestIds);
        for (ChargeItem chargeItem : byRequest) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            byId.put(chargeItem.getId(), chargeItem);
            LocalDateTime recorded = recordedByRequestId.get(chargeItem.getServiceId());
            if (recorded != null) {
                billDateOverrideByChargeItemId.put(chargeItem.getId(), recorded);
            }
        }
    }

    /**
     * 药品待结：按 {@code occurrence_time} 窗收账单（与滚费 occurrence 轴同字段，仅收药品锚点）。
     *
     * <p>场景：护士/药房已入待结账，随后退药把 Procedure CANCEL 对冲，净 recorded 轴不再命中，
     * 但 {@code ChargeItem} 仍为 {@code PENDING_SETTLEMENT}——费用明细关键字检索须能命中。</p>
     *
     * @param windowFrom                       日起（含）
     * @param windowToExclusive                日止（不含）
     * @param byId                             去重账单累积
     * @param billDateOverrideByChargeItemId   业务时间覆盖累积
     */
    private void appendPendingMedicationByOccurrenceTimeWindow(
        LocalDateTime windowFrom,
        LocalDateTime windowToExclusive,
        LinkedHashMap<Long, ChargeItem> byId,
        Map<Long, LocalDateTime> billDateOverrideByChargeItemId) {
        String pendingCode = ChargeItemStatus.PENDING_SETTLEMENT.getCode();
        List<ChargeItem> occurrencePending =
            this.chargeItemQueryService.listChargeItemsByOccurrenceTimeWindow(
                windowFrom,
                windowToExclusive,
                null,
                List.of(pendingCode));
        for (ChargeItem chargeItem : occurrencePending) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            // 仅药品：product 定义表或 request/dispense 服务锚点
            boolean medicationProduct =
                TableNameConstants.MED_MEDICATION_DEFINITION.equals(chargeItem.getProductTable());
            String serviceTable = chargeItem.getServiceTable();
            boolean medicationService =
                TableNameConstants.MED_MEDICATION_REQUEST.equals(serviceTable)
                    || TableNameConstants.MED_MEDICATION_DISPENSE.equals(serviceTable);
            if (!medicationProduct && !medicationService) {
                continue;
            }
            byId.put(chargeItem.getId(), chargeItem);
            if (chargeItem.getOccurrenceTime() != null) {
                billDateOverrideByChargeItemId.put(chargeItem.getId(), chargeItem.getOccurrenceTime());
            }
        }
    }

    /**
     * 耗材护士划价待结：按主发放展示时刻窗反查账单。
     *
     * @param windowFrom                       日起（含）
     * @param windowToExclusive                日止（不含）
     * @param byId                             去重账单累积
     * @param billDateOverrideByChargeItemId   业务时间覆盖累积
     */
    private void appendPendingDevicePricingByDisplayTimeWindow(
        LocalDateTime windowFrom,
        LocalDateTime windowToExclusive,
        LinkedHashMap<Long, ChargeItem> byId,
        Map<Long, LocalDateTime> billDateOverrideByChargeItemId) {
        Map<Long, LocalDateTime> displayByReqId =
            this.deviceDispenseQueryService.mapPrimaryDisplayTimeByDisplayTimeWindow(
                windowFrom, windowToExclusive);
        if (displayByReqId.isEmpty()) {
            return;
        }
        List<ChargeItem> devicePending =
            this.chargeItemQueryService.listPendingSettlementChargeItemsByServiceTableAndServiceIds(
                TableNameConstants.WKF_DEVICE_REQUEST, displayByReqId.keySet());
        for (ChargeItem chargeItem : devicePending) {
            if (chargeItem == null || chargeItem.getId() == null) {
                continue;
            }
            byId.put(chargeItem.getId(), chargeItem);
            LocalDateTime displayTime = displayByReqId.get(chargeItem.getServiceId());
            if (displayTime != null) {
                billDateOverrideByChargeItemId.put(chargeItem.getId(), displayTime);
            }
        }
    }

    /**
     * 时间窗账单加载结果。
     */
    private static final class WindowChargeLoadResult {
        private final List<ChargeItem> chargeItems;
        private final Map<Long, LocalDateTime> billDateOverrideByChargeItemId;

        private WindowChargeLoadResult(
            List<ChargeItem> chargeItems,
            Map<Long, LocalDateTime> billDateOverrideByChargeItemId) {
            this.chargeItems = chargeItems;
            this.billDateOverrideByChargeItemId = billDateOverrideByChargeItemId;
        }
    }

    /**
     * 解析住院费用明细结算状态白名单；空则默认已结+待结算。
     *
     * @param query 可含 {@code allowedChargeItemStatusCodes}（由 Mapper 按筛选项写入）
     * @return 非空状态码列表
     */
    private List<String> resolveAllowedChargeItemStatusCodes(OutpatientFeeDetailPageQuery query) {
        List<String> configured = query.getAllowedChargeItemStatusCodes();
        if (configured != null && !configured.isEmpty()) {
            LinkedHashSet<String> normalized = new LinkedHashSet<>();
            for (String statusCode : configured) {
                if (StringUtils.isNotEmpty(statusCode)) {
                    normalized.add(statusCode.trim());
                }
            }
            if (!normalized.isEmpty()) {
                return new ArrayList<>(normalized);
            }
        }
        // 缺省：已结 + 待结算均可查
        List<String> defaults = new ArrayList<>();
        defaults.add(ChargeItemStatus.SETTLED.getCode());
        defaults.add(ChargeItemStatus.PENDING_SETTLEMENT.getCode());
        return defaults;
    }

    /**
     * 在已写入候选链路后收窄科室/患者/项目等。
     *
     * @param query 已含 paymentChargeItemLinks
     */
    private void narrowInpatientFeeDetailScopesAfterCandidates(OutpatientFeeDetailPageQuery query) {
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
        // Pre：在候选 scope 内再收窄挂号科室/患者/项目/细分类
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
        for (ChargeItem chargeItem : chargeItems) {
            if (chargeItem != null && chargeItem.getEncounterId() != null) {
                encounterIds.add(chargeItem.getEncounterId());
            }
        }
        Set<Long> allowedEncounterIds = this.resolveAllowedEncounterIds(query, encounterIds);
        Set<Long> feeNatureMatchedChargeItemIds =
            this.reportPatientSettlementContextAssembler.filterChargeItemIdsByAccountContractNo(
                chargeItems, query.getFeeNatureContractNo());
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
        Map<String, ProductDefinitionDisplayDto> productMap = Map.of();
        if (filterByProductKeyword) {
            productMap = this.loadProductMapForChargeItems(chargeItems);
        }
        Map<Long, String> finTypeByChargeItemId = Map.of();
        if (filterBySubCategory) {
            finTypeByChargeItemId =
                this.dailySettlementFinTypeAmountLoader.mapFinTypeCodeByChargeItemId(chargeItems);
        }
        LinkedHashSet<Long> filteredChargeItemIds = new LinkedHashSet<>();
        LinkedHashSet<Long> filteredEncounterIds = new LinkedHashSet<>();
        List<OutpatientFeeDetailChargePaymentLinkQueryModel> filteredLinks = new ArrayList<>();
        for (OutpatientFeeDetailChargePaymentLinkQueryModel link : paymentChargeItemLinks) {
            if (link == null || link.getChargeItemId() == null) {
                continue;
            }
            ChargeItem chargeItem = chargeItemById.get(link.getChargeItemId());
            if (chargeItem == null) {
                continue;
            }
            if (!this.isInpatientFeeDetailChargeStatus(chargeItem.getStatusEnum(), query)) {
                continue;
            }
            if (chargeItem.getEncounterId() == null || !allowedEncounterIdSet.contains(chargeItem.getEncounterId())) {
                continue;
            }
            // 开立科室：按账单 requesting_org_id 收窄
            if (query.getRequestingOrgId() != null
                && !query.getRequestingOrgId().equals(chargeItem.getRequestingOrgId())) {
                continue;
            }
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
        Map<Long, ChargeItem> filteredChargeItemById = new LinkedHashMap<>();
        for (Long chargeItemId : filteredChargeItemIds) {
            ChargeItem chargeItem = chargeItemById.get(chargeItemId);
            if (chargeItem != null) {
                filteredChargeItemById.put(chargeItemId, chargeItem);
            }
        }
        query.setPreloadedChargeItemsById(filteredChargeItemById);
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
     * 在 payment 候选就诊集合上叠加住院科室与患者关键字收窄。
     *
     * <p>科室筛选仅在 payment 已命中的候选就诊上过滤 organization_id，禁止拉取该科室历史全部 IMP 就诊。</p>
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
        // Pre：只加载候选就诊实体，禁止按科室全表扫 IMP
        List<Encounter> encounters = this.encounterQueryService.listEncounterEntitiesByIds(allowed);
        Long departmentId = query.getRegistrationDepartmentId();
        Set<Long> departmentMatched = new HashSet<>();
        for (Encounter encounter : encounters) {
            if (encounter == null || encounter.getId() == null) {
                continue;
            }
            if (filterByDepartment) {
                // 住院科室 = Encounter.organization_id；并收窄 IMP
                if (!departmentId.equals(encounter.getOrganizationId())) {
                    continue;
                }
                if (!EncounterClass.IMP.getCode().equals(encounter.getClassEnum())) {
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
        // 患者关键字：住院号 busNo 或姓名命中其一即可（在科室收窄后的集合上）
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
     * 住院费用明细纳入的 ChargeItem 状态：须落在请求白名单内。
     *
     * @param statusEnum 账单状态码
     * @param query      含 {@code allowedChargeItemStatusCodes}
     * @return 命中白名单时 {@code true}
     */
    private boolean isInpatientFeeDetailChargeStatus(String statusEnum, OutpatientFeeDetailPageQuery query) {
        if (StringUtils.isEmpty(statusEnum) || query == null) {
            return false;
        }
        List<String> allowed = query.getAllowedChargeItemStatusCodes();
        if (allowed == null || allowed.isEmpty()) {
            return ChargeItemStatus.SETTLED.getCode().equals(statusEnum)
                || ChargeItemStatus.PENDING_SETTLEMENT.getCode().equals(statusEnum);
        }
        for (String code : allowed) {
            if (statusEnum.equals(code)) {
                return true;
            }
        }
        return false;
    }
}
