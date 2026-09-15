/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.inpatient.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.openhis.health.application.report.consumableusage.inpatient.dto.InpatientConsumableUsageDetailRowDto;
import com.openhis.health.application.report.consumableusage.inpatient.dto.InpatientConsumableUsageSummaryRowDto;
import com.openhis.health.application.report.consumableusage.support.ConsumableUsageReportAmountEnricher;
import com.openhis.health.application.report.consumableusage.support.ConsumableUsageReportFilterLoader;
import com.openhis.health.application.report.consumableusage.support.ConsumableUsageReportQueryFilter;
import com.openhis.health.application.report.consumableusage.support.ConsumableUsageReportScopeLoader;
import com.openhis.health.application.shared.advice.OrderCatalogDisplayAssembler;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.report.PatientSettlementContextRow;
import com.openhis.health.application.shared.report.ReportPatientSettlementContextAssembler;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.share.constants.ConsumableUsageReportConstants;
import com.openhis.health.domain.share.constants.DeptIssueReportConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.workflow.devicedispense.queries.model.ConsumableUsageDispenseLineQueryModel;
import com.openhis.health.domain.workflow.devicedispense.queries.model.ConsumableUsageDispenseMergedGroupQueryModel;
import com.openhis.health.domain.workflow.devicedispense.queries.model.ConsumableUsageDispenseSummaryBundle;
import com.openhis.health.domain.workflow.devicedispense.queries.model.ConsumableUsageDispenseSummaryRow;
import com.openhis.health.domain.workflow.devicedispense.query.ConsumableUsageReportPageQuery;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.DeptIssueReportLineQueryModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.DeptIssueReportSummaryBundle;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.DeptIssueReportSummaryRow;
import com.openhis.health.domain.workflow.supplyrequest.query.DeptIssueReportPageQuery;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 住院耗材使用报表单源编排（患者 DeviceDispense 或科室 DEPT_ISSUE；AS-RPT-006）。
 *
 * <p>领域白名单（AS Gap / DM-WKF-007 §6.2.23～24、DM-WKF-006 §6.2.15）：
 * {@code pageConsumableUsageDispenseLines} / {@code listConsumableUsageDispenseLines} /
 * {@code pageConsumableUsageDispenseMergedByEncounterAndDevice} /
 * {@code listConsumableUsageDispenseMergedByEncounterAndDevice} /
 * {@code listConsumableUsageDispenseLinesForMergedKeys} /
 * {@code summarizeConsumableUsageDispenseWithLines}；
 * {@code pageDeptIssueReportLines} / {@code listDeptIssueReportLines} /
 * {@code summarizeDeptIssueReportWithLines}。住院域由本 Support 预写 IMP encounter scope，明细 enrich 住院号/病区/床号。</p>
 *
 * <p>禁止 ALL 两路并集；禁止 import 本 Feature Request/Response。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientConsumableUsageReportQuerySupport {

    /** 患者侧发放读。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;

    /** 科室领用读。 */
    private final SupplyRequestQueryService supplyRequestQueryService;

    /** 就诊/库位/组织 scope 预解析。 */
    private final ConsumableUsageReportScopeLoader consumableUsageReportScopeLoader;

    /** 品规/患者/记录类型等跨聚合筛选预解析。 */
    private final ConsumableUsageReportFilterLoader consumableUsageReportFilterLoader;

    /** 患者侧金额 enrich。 */
    private final ConsumableUsageReportAmountEnricher consumableUsageReportAmountEnricher;

    /** 耗材定义批量读。 */
    private final DeviceDefinitionQueryService deviceDefinitionQueryService;

    /** 患者姓名。 */
    private final PatientQueryService patientQueryService;

    /** 就诊号。 */
    private final EncounterQueryService encounterQueryService;

    /** 组织名。 */
    private final OrganizationQueryService organizationQueryService;

    /** 库位名。 */
    private final LocationQueryService locationQueryService;

    /** 执行人（发耗材人）姓名。 */
    private final PractitionerQueryService practitionerQueryService;

    /** 单位术语。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 费用性质 / medType 上下文。 */
    private final ReportPatientSettlementContextAssembler reportPatientSettlementContextAssembler;

    /**
     * 汇总：两路净量按维合并；金额仅患者侧经 ChargeItem，科室侧用行价累加近似净额。
     *
     * @param filter 报表筛选（含日期/来源/库位/科室/品规/患者/记录类型/执行人）
     * @return 汇总响应
     */
    public InpatientConsumableUsageSummaryResult summarize(ConsumableUsageReportQueryFilter filter) {
        String dimension = this.resolveSummaryDimension(filter.getSummaryDimension());
        String source = this.resolveSourceCode(filter.getSourceCode());
        // Pre：CATEGORY 先走 ITEM 领域分桶；科室 LOCATION remap 在 ScopeLoader
        String patientDomainDimension = this.resolvePatientDomainDimension(dimension);
        String deptDomainDimension = this.resolveDeptDomainDimension(dimension);
        List<InpatientConsumableUsageSummaryRowDto> rows = new ArrayList<>();
        List<ConsumableUsageDispenseLineQueryModel> patientLineRows = new ArrayList<>();
        List<DeptIssueReportLineQueryModel> deptLineRows = new ArrayList<>();
        // Pre：按来源收窄一路或两路；同次扫描保留事实行供汇总定价
        if (this.includePatient(source)) {
            ConsumableUsageReportPageQuery patientQuery = new ConsumableUsageReportPageQuery();
            this.preparePatientDispenseQueryWithFeeNature(
                patientQuery,
                EncounterClass.IMP.getCode(),
                filter,
                patientDomainDimension);
            ConsumableUsageDispenseSummaryBundle patientBundle =
                this.deviceDispenseQueryService.summarizeConsumableUsageDispenseWithLines(patientQuery);
            rows.addAll(this.mapPatientSummaries(patientBundle.getSummaryRows(), dimension));
            if (patientBundle.getLineRows() != null) {
                patientLineRows = patientBundle.getLineRows();
            }
        }
        if (this.includeDept(source)) {
            DeptIssueReportPageQuery deptQuery = new DeptIssueReportPageQuery();
            this.prepareDeptIssueQuery(
                deptQuery,
                EncounterClass.IMP.getCode(),
                filter,
                deptDomainDimension);
            DeptIssueReportSummaryBundle deptBundle =
                this.supplyRequestQueryService.summarizeDeptIssueReportWithLines(deptQuery);
            rows = this.mergeSummaryRows(rows, this.mapDeptSummaries(deptBundle.getSummaryRows(), dimension));
            if (deptBundle.getLineRows() != null) {
                deptLineRows = deptBundle.getLineRows();
            }
        }
        Map<Long, String> categoryCodeByDeviceDefId = null;
        // CATEGORY：ITEM 行再按类型分桶（含科室领用）
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_CATEGORY.equals(dimension)) {
            categoryCodeByDeviceDefId = this.resolveCategoryCodeByDeviceDefIds(this.collectDeviceDefIds(rows));
            rows = this.rebucketItemRowsToCategory(rows, categoryCodeByDeviceDefId);
        }
        // Asm：回填统一 dimensionName
        this.enrichSummaryDisplay(rows, dimension);
        // CATEGORY：事实行可能多出品规，补全映射避免金额落入空类型桶
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_CATEGORY.equals(dimension)) {
            LinkedHashSet<Long> allDeviceDefIds =
                this.collectDeviceDefIdsFromLineRows(patientLineRows, deptLineRows);
            if (categoryCodeByDeviceDefId != null) {
                allDeviceDefIds.addAll(categoryCodeByDeviceDefId.keySet());
            }
            categoryCodeByDeviceDefId = this.resolveCategoryCodeByDeviceDefIds(allDeviceDefIds);
        }
        // Pre：复用同窗事实行分块 enrich，禁止二次 page 扫描
        this.enrichSummaryPurchaseSaleAmounts(
            rows, patientLineRows, deptLineRows, dimension, categoryCodeByDeviceDefId);
        InpatientConsumableUsageSummaryResult response =
            new InpatientConsumableUsageSummaryResult();
        response.setRows(rows);
        response.setTotalNetQuantity(this.sumNetQuantity(rows));
        response.setSaleNetTotalAmount(this.consumableUsageReportAmountEnricher.sumSaleNetAmount(rows));
        response.setPurchaseNetTotalAmount(this.consumableUsageReportAmountEnricher.sumPurchaseNetAmount(rows));
        return response;
    }

    /**
     * 明细分页：单源领域真分页（患者 DeviceDispense 或科室领用）；禁止 ALL 并集预取。
     *
     * @param filter   报表筛选
     * @param pageNo   页码（1 基）
     * @param pageSize 页大小
     * @return 明细分页
     */
    public InpatientConsumableUsageDetailPageResult pageDetail(
        ConsumableUsageReportQueryFilter filter,
        Integer pageNo,
        Integer pageSize) {
        // Gate：来源规范化；ALL/未知码拒绝
        String source = this.resolveSourceCode(filter.getSourceCode());
        int resolvedPageNo = 1;
        if (pageNo != null && pageNo > 0) {
            resolvedPageNo = pageNo;
        }
        int resolvedPageSize = ConsumableUsageReportConstants.DEFAULT_PAGE_SIZE;
        if (pageSize != null && pageSize > 0) {
            resolvedPageSize = pageSize;
        }
        if (resolvedPageSize > ConsumableUsageReportConstants.MAX_PAGE_SIZE) {
            resolvedPageSize = ConsumableUsageReportConstants.MAX_PAGE_SIZE;
        }
        // Pre：患者单源 → 合并分组或原生分页，禁止 pageNo*pageSize 前缀物化
        if (ConsumableUsageReportConstants.SOURCE_CODE_PATIENT_DISPENSE.equals(source)) {
            // 合并模式仅患者源生效；科室领用见下方 LINE 路径
            if (this.isMergeByDefinition(filter)) {
                return this.pageMergedPatient(filter, resolvedPageNo, resolvedPageSize);
            }
            return this.pagePatientOnly(filter, resolvedPageNo, resolvedPageSize);
        }
        // Pre：领用单源 → 领域原生分页（BY_DEFINITION 不生效，按 LINE）
        if (ConsumableUsageReportConstants.SOURCE_CODE_DEPT_ISSUE.equals(source)) {
            return this.pageDeptOnly(filter, resolvedPageNo, resolvedPageSize);
        }
        // Gate：防御——规范化后仍非单源
        throw new BusinessException(
            ConsumableUsageReportConstants.EX_RPT_ICU_103,
            "禁止全部来源混查");
    }

    /**
     * 明细全量列表（导出 DETAIL Tab；受 EXPORT_MAX_ROWS 约束；单源）。
     *
     * @param filter 报表筛选
     * @return 明细行
     */
    public List<InpatientConsumableUsageDetailRowDto> listDetailForExport(ConsumableUsageReportQueryFilter filter) {
        String source = this.resolveSourceCode(filter.getSourceCode());
        if (ConsumableUsageReportConstants.SOURCE_CODE_PATIENT_DISPENSE.equals(source)) {
            // 导出合并：上限落在合并行数，禁止先物化全窗发放行再合并截断
            if (this.isMergeByDefinition(filter)) {
                return this.listMergedPatientDetails(filter, ConsumableUsageReportConstants.EXPORT_MAX_ROWS);
            }
            return this.listPatientDetails(filter, ConsumableUsageReportConstants.EXPORT_MAX_ROWS);
        }
        if (ConsumableUsageReportConstants.SOURCE_CODE_DEPT_ISSUE.equals(source)) {
            return this.listDeptDetails(filter, ConsumableUsageReportConstants.EXPORT_MAX_ROWS);
        }
        throw new BusinessException(
            ConsumableUsageReportConstants.EX_RPT_ICU_104,
            "禁止全部来源混查");
    }

    /**
     * 汇总进售价：复用数量汇总同次扫描的事实行，按 IN 分块装配定价后累加写回。
     *
     * <p>禁止对同一筛选窗再 {@code page*} 二次扫描；禁止囤积全窗定价明细后再一次聚合。</p>
     *
     * @param summaryRows                已合并汇总行
     * @param patientLineRows            患者侧同窗事实行；不查患者时为 null/空
     * @param deptLineRows               领用侧同窗事实行；不查领用时为 null/空
     * @param dimension                  汇总维度
     * @param categoryCodeByDeviceDefId  CATEGORY 维品规→类型；可空
     */
    private void enrichSummaryPurchaseSaleAmounts(
        List<InpatientConsumableUsageSummaryRowDto> summaryRows,
        List<ConsumableUsageDispenseLineQueryModel> patientLineRows,
        List<DeptIssueReportLineQueryModel> deptLineRows,
        String dimension,
        Map<Long, String> categoryCodeByDeviceDefId) {
        // 无汇总行：无需回刷进售价
        if (summaryRows == null || summaryRows.isEmpty()) {
            return;
        }
        ConsumableUsageReportAmountEnricher.SummaryAmountBuckets buckets =
            new ConsumableUsageReportAmountEnricher.SummaryAmountBuckets();
        // Pre：患者侧全窗一次装定价（禁止按 500 反复开 ChargeItem/批号会话）
        if (patientLineRows != null && !patientLineRows.isEmpty()) {
            List<InpatientConsumableUsageDetailRowDto> priced =
                this.mapPatientDetailLines(patientLineRows);
            this.enrichDetailAmountsOnly(priced);
            this.consumableUsageReportAmountEnricher.accumulateDetailRowsIntoSummaryBuckets(
                buckets, priced, dimension, categoryCodeByDeviceDefId);
        }
        // Pre：领用侧全窗一次装进价
        if (deptLineRows != null && !deptLineRows.isEmpty()) {
            List<InpatientConsumableUsageDetailRowDto> priced = this.mapDeptDetailLines(deptLineRows);
            this.enrichDetailAmountsOnly(priced);
            this.consumableUsageReportAmountEnricher.accumulateDetailRowsIntoSummaryBuckets(
                buckets, priced, dimension, categoryCodeByDeviceDefId);
        }
        // Asm：一次写回各 dimensionKey 进/售净额
        this.consumableUsageReportAmountEnricher.applySummaryAmountBuckets(summaryRows, buckets);
    }

    /**
     * 患者侧 scope 预解析、费用性质 encounter 收窄与品规/患者/记录类型筛选。
     *
     * @param query               患者侧查询
     * @param encounterClassCode  就诊类别 AMB / IMP
     * @param filter              报表筛选
     * @param summaryDimension    汇总维；明细可 null
     */
    private void preparePatientDispenseQueryWithFeeNature(
        ConsumableUsageReportPageQuery query,
        String encounterClassCode,
        ConsumableUsageReportQueryFilter filter,
        String summaryDimension) {
        this.consumableUsageReportScopeLoader.preparePatientDispenseQuery(
            query,
            encounterClassCode,
            filter.getDateFrom(),
            filter.getDateTo(),
            filter.getLocationId(),
            summaryDimension,
            filter.getOrganizationId());
        this.reportPatientSettlementContextAssembler.applyFeeNatureFilterToConsumableUsageReportPageQuery(
            query, filter.getFeeNatureContractNo());
        // Pre：记录类型/执行人/品规/患者关键字叠加
        this.consumableUsageReportFilterLoader.applyPatientExtraFilters(query, filter, encounterClassCode);
    }

    /**
     * 科室领用侧 scope 预解析与品规筛选。
     *
     * @param query              科室侧查询
     * @param encounterClassCode 就诊类别 AMB / IMP
     * @param filter             报表筛选
     * @param summaryDimension   汇总维；明细可 null
     */
    private void prepareDeptIssueQuery(
        DeptIssueReportPageQuery query,
        String encounterClassCode,
        ConsumableUsageReportQueryFilter filter,
        String summaryDimension) {
        this.consumableUsageReportScopeLoader.prepareDeptIssueQuery(
            query,
            encounterClassCode,
            filter.getDateFrom(),
            filter.getDateTo(),
            filter.getLocationId(),
            summaryDimension,
            filter.getOrganizationId());
        // Pre：类型/关键字 → resolvedItemIds
        this.consumableUsageReportFilterLoader.applyDeptExtraFilters(query, filter);
    }

    /**
     * 仅患者侧分页。
     *
     * @param filter   报表筛选
     * @param pageNo   1 基页码
     * @param pageSize 页大小
     * @return 患者侧明细分页（含展示 enrich）
     */
    private InpatientConsumableUsageDetailPageResult pagePatientOnly(
        ConsumableUsageReportQueryFilter filter,
        int pageNo,
        int pageSize) {
        ConsumableUsageReportPageQuery query = new ConsumableUsageReportPageQuery();
        this.preparePatientDispenseQueryWithFeeNature(
            query, EncounterClass.IMP.getCode(), filter, null);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        Page<ConsumableUsageDispenseLineQueryModel> page =
            this.deviceDispenseQueryService.pageConsumableUsageDispenseLines(query);
        List<InpatientConsumableUsageDetailRowDto> rows = this.mapPatientDetailLines(page.getContent());
        this.enrichDetailDisplay(rows);
        InpatientConsumableUsageDetailPageResult response =
            new InpatientConsumableUsageDetailPageResult();
        response.setRecords(rows);
        response.setTotal(page.getTotalElements());
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        return response;
    }

    /**
     * 仅科室侧分页。
     *
     * @param filter   报表筛选
     * @param pageNo   1 基页码
     * @param pageSize 页大小
     * @return 科室侧明细分页（含展示 enrich）
     */
    private InpatientConsumableUsageDetailPageResult pageDeptOnly(
        ConsumableUsageReportQueryFilter filter,
        int pageNo,
        int pageSize) {
        DeptIssueReportPageQuery query = new DeptIssueReportPageQuery();
        this.prepareDeptIssueQuery(query, EncounterClass.IMP.getCode(), filter, null);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        Page<DeptIssueReportLineQueryModel> page =
            this.supplyRequestQueryService.pageDeptIssueReportLines(query);
        List<InpatientConsumableUsageDetailRowDto> rows = this.mapDeptDetailLines(page.getContent());
        this.enrichDetailDisplay(rows);
        InpatientConsumableUsageDetailPageResult response =
            new InpatientConsumableUsageDetailPageResult();
        response.setRecords(rows);
        response.setTotal(page.getTotalElements());
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        return response;
    }

    /**
     * 患者侧有界明细列表。
     *
     * @param filter  报表筛选
     * @param maxRows 有界上限
     * @return 含展示 enrich 的患者明细
     */
    private List<InpatientConsumableUsageDetailRowDto> listPatientDetails(
        ConsumableUsageReportQueryFilter filter,
        int maxRows) {
        ConsumableUsageReportPageQuery query = new ConsumableUsageReportPageQuery();
        this.preparePatientDispenseQueryWithFeeNature(
            query, EncounterClass.IMP.getCode(), filter, null);
        query.setPageSize(maxRows);
        List<ConsumableUsageDispenseLineQueryModel> models =
            this.deviceDispenseQueryService.listConsumableUsageDispenseLines(query);
        List<InpatientConsumableUsageDetailRowDto> rows = this.mapPatientDetailLines(models);
        this.enrichDetailDisplay(rows);
        return rows;
    }

    /**
     * 科室侧有界明细列表。
     *
     * @param filter  报表筛选
     * @param maxRows 有界上限
     * @return 含展示 enrich 的科室明细
     */
    private List<InpatientConsumableUsageDetailRowDto> listDeptDetails(
        ConsumableUsageReportQueryFilter filter,
        int maxRows) {
        DeptIssueReportPageQuery query = new DeptIssueReportPageQuery();
        this.prepareDeptIssueQuery(query, EncounterClass.IMP.getCode(), filter, null);
        query.setPageSize(maxRows);
        List<DeptIssueReportLineQueryModel> models =
            this.supplyRequestQueryService.listDeptIssueReportLines(query);
        List<InpatientConsumableUsageDetailRowDto> rows = this.mapDeptDetailLines(models);
        this.enrichDetailDisplay(rows);
        return rows;
    }

    /**
     * 患者侧按就诊+品规[+单位]合并分页（领域 GROUP BY 真分页；本页回载明细刷金额）。
     *
     * @param filter   报表筛选
     * @param pageNo   1 基页码
     * @param pageSize 页大小（合并行）
     * @return 合并后的明细分页；{@code total} 为分组条数
     */
    private InpatientConsumableUsageDetailPageResult pageMergedPatient(
        ConsumableUsageReportQueryFilter filter,
        int pageNo,
        int pageSize) {
        ConsumableUsageReportPageQuery query = new ConsumableUsageReportPageQuery();
        this.preparePatientDispenseQueryWithFeeNature(
            query, EncounterClass.IMP.getCode(), filter, null);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        Page<ConsumableUsageDispenseMergedGroupQueryModel> page =
            this.deviceDispenseQueryService.pageConsumableUsageDispenseMergedByEncounterAndDevice(query);
        List<InpatientConsumableUsageDetailRowDto> mergedRows =
            this.mapMergedGroupDetailLines(page.getContent());
        // Pre：展示字段（姓名/品名/病区床等）；金额在页内明细回载后汇总
        this.enrichDetailDisplay(mergedRows);
        this.enrichMergedAmountsFromPageScopedLines(query, mergedRows);
        InpatientConsumableUsageDetailPageResult response =
            new InpatientConsumableUsageDetailPageResult();
        response.setRecords(mergedRows);
        response.setTotal(page.getTotalElements());
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        return response;
    }

    /**
     * 患者侧按就诊+品规[+单位]合并有界列表（导出；上限落在合并行）。
     *
     * @param filter  报表筛选
     * @param maxRows 合并行上限
     * @return 合并明细
     */
    private List<InpatientConsumableUsageDetailRowDto> listMergedPatientDetails(
        ConsumableUsageReportQueryFilter filter,
        int maxRows) {
        ConsumableUsageReportPageQuery query = new ConsumableUsageReportPageQuery();
        this.preparePatientDispenseQueryWithFeeNature(
            query, EncounterClass.IMP.getCode(), filter, null);
        query.setPageSize(maxRows);
        List<ConsumableUsageDispenseMergedGroupQueryModel> groups =
            this.deviceDispenseQueryService.listConsumableUsageDispenseMergedByEncounterAndDevice(query);
        List<InpatientConsumableUsageDetailRowDto> mergedRows = this.mapMergedGroupDetailLines(groups);
        this.enrichDetailDisplay(mergedRows);
        this.enrichMergedAmountsFromPageScopedLines(query, mergedRows);
        return mergedRows;
    }

    /**
     * 合并分组 → 明细 DTO：保留患者/就诊/品规/单位/净量；清空行级标识与批号/执行人/状态。
     *
     * @param groups 领域合并分组
     * @return 报表明细（未展示 enrich）
     */
    private List<InpatientConsumableUsageDetailRowDto> mapMergedGroupDetailLines(
        List<ConsumableUsageDispenseMergedGroupQueryModel> groups) {
        List<InpatientConsumableUsageDetailRowDto> rows = new ArrayList<>();
        if (groups == null) {
            return rows;
        }
        for (ConsumableUsageDispenseMergedGroupQueryModel group : groups) {
            if (group == null) {
                continue;
            }
            InpatientConsumableUsageDetailRowDto row = new InpatientConsumableUsageDetailRowDto();
            row.setSourceCode(ConsumableUsageReportConstants.SOURCE_CODE_PATIENT_DISPENSE);
            row.setSourceCode_dictText("患者使用");
            row.setEncounterId(group.getEncounterId());
            row.setDeviceDefId(group.getDeviceDefId());
            row.setPatientId(group.getPatientId());
            row.setLocationId(group.getLocationId());
            row.setUnitCode(group.getUnitCode());
            BigDecimal netQuantity = group.getNetQuantity();
            if (netQuantity == null) {
                netQuantity = BigDecimal.ZERO;
            }
            row.setQuantity(netQuantity);
            // 合并行：行级主键/时间/批号/执行人/状态一律清空
            row.setDispenseId(null);
            row.setBasedOnDispenseId(null);
            row.setDeviceReqId(null);
            row.setBusinessTime(null);
            row.setPerformerId(null);
            row.setPerformerName(null);
            row.setLotNumber(null);
            row.setChargeLotNumber(null);
            row.setLotMismatch(null);
            row.setStatusOrTypeEnum(null);
            row.setStatusOrTypeEnum_enumText(null);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 对本页合并行：按 encounter/deviceDef IN 回载明细 → 定价 → 按精确键汇总金额/单价/费用性质。
     *
     * @param query      已预解析的患者侧查询（同窗筛选）
     * @param mergedRows 已展示 enrich 的合并行
     */
    private void enrichMergedAmountsFromPageScopedLines(
        ConsumableUsageReportPageQuery query,
        List<InpatientConsumableUsageDetailRowDto> mergedRows) {
        if (mergedRows == null || mergedRows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> deviceDefIds = new LinkedHashSet<>();
        for (InpatientConsumableUsageDetailRowDto row : mergedRows) {
            if (row == null) {
                continue;
            }
            if (row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
            if (row.getDeviceDefId() != null) {
                deviceDefIds.add(row.getDeviceDefId());
            }
        }
        List<ConsumableUsageDispenseLineQueryModel> rawModels =
            this.deviceDispenseQueryService.listConsumableUsageDispenseLinesForMergedKeys(
                query, encounterIds, deviceDefIds);
        // Pre：先按本页精确键过滤，禁止对 IN 笛卡尔多余行做 ChargeItem 定价（性能）
        LinkedHashSet<String> pageMergeKeys = new LinkedHashSet<>();
        for (InpatientConsumableUsageDetailRowDto mergedRow : mergedRows) {
            if (mergedRow == null) {
                continue;
            }
            String pageKey = this.buildMergedPatientDeviceUnitKey(
                mergedRow.getEncounterId(), mergedRow.getDeviceDefId(), mergedRow.getUnitCode());
            if (pageKey != null) {
                pageMergeKeys.add(pageKey);
            }
        }
        List<ConsumableUsageDispenseLineQueryModel> pageScopedModels = new ArrayList<>();
        for (ConsumableUsageDispenseLineQueryModel rawModel : rawModels) {
            if (rawModel == null) {
                continue;
            }
            String rawKey = this.buildMergedPatientDeviceUnitKey(
                rawModel.getEncounterId(), rawModel.getDeviceDefId(), rawModel.getUnitCode());
            // 非本页分组合键：丢弃，避免多余定价
            if (rawKey == null || !pageMergeKeys.contains(rawKey)) {
                continue;
            }
            pageScopedModels.add(rawModel);
        }
        List<InpatientConsumableUsageDetailRowDto> pricedLines = this.mapPatientDetailLines(pageScopedModels);
        // Pre：仅刷金额与费用性质，禁止账批质检（合并行无批号列）
        this.enrichDetailAmountsOnly(pricedLines);
        this.enrichPatientSettlementContext(pricedLines);
        this.applyMergedAmountsFromPricedLines(mergedRows, pricedLines);
    }

    /**
     * 将页内已定价明细按 encounter+deviceDef+unit 键汇总到合并行。
     *
     * <p>金额/数量符号已在明细侧处理；单价仅当组内唯一非空值时保留；费用性质异质则清空。</p>
     *
     * @param mergedRows  合并行
     * @param pricedLines 页内已定价明细
     */
    private void applyMergedAmountsFromPricedLines(
        List<InpatientConsumableUsageDetailRowDto> mergedRows,
        List<InpatientConsumableUsageDetailRowDto> pricedLines) {
        Map<String, List<InpatientConsumableUsageDetailRowDto>> linesByKey = new LinkedHashMap<>();
        if (pricedLines != null) {
            for (InpatientConsumableUsageDetailRowDto line : pricedLines) {
                if (line == null) {
                    continue;
                }
                String key = this.buildMergedPatientDeviceUnitKey(
                    line.getEncounterId(), line.getDeviceDefId(), line.getUnitCode());
                if (key == null) {
                    continue;
                }
                List<InpatientConsumableUsageDetailRowDto> bucket = linesByKey.get(key);
                if (bucket == null) {
                    bucket = new ArrayList<>();
                    linesByKey.put(key, bucket);
                }
                bucket.add(line);
            }
        }
        for (InpatientConsumableUsageDetailRowDto merged : mergedRows) {
            if (merged == null) {
                continue;
            }
            String key = this.buildMergedPatientDeviceUnitKey(
                merged.getEncounterId(), merged.getDeviceDefId(), merged.getUnitCode());
            if (key == null) {
                continue;
            }
            List<InpatientConsumableUsageDetailRowDto> bucket = linesByKey.get(key);
            if (bucket == null || bucket.isEmpty()) {
                continue;
            }
            BigDecimal purchaseSum = BigDecimal.ZERO;
            BigDecimal saleSum = BigDecimal.ZERO;
            BigDecimal amountSum = BigDecimal.ZERO;
            BigDecimal quantitySum = BigDecimal.ZERO;
            boolean hasPurchase = false;
            boolean hasSale = false;
            boolean hasAmount = false;
            Set<String> purchaseUnitPriceKeys = new HashSet<>();
            Set<String> saleUnitPriceKeys = new HashSet<>();
            BigDecimal solePurchaseUnitPrice = null;
            BigDecimal soleSaleUnitPrice = null;
            Set<String> feeNatureKeys = new HashSet<>();
            String soleFeeNatureContractNo = null;
            String soleFeeNatureDisplay = null;
            for (InpatientConsumableUsageDetailRowDto line : bucket) {
                // 净量：明细侧已含退药取负，代数加总
                if (line.getQuantity() != null) {
                    quantitySum = quantitySum.add(line.getQuantity());
                }
                if (line.getPurchaseAmount() != null) {
                    purchaseSum = purchaseSum.add(line.getPurchaseAmount());
                    hasPurchase = true;
                }
                if (line.getSaleAmount() != null) {
                    saleSum = saleSum.add(line.getSaleAmount());
                    hasSale = true;
                }
                if (line.getAmount() != null) {
                    amountSum = amountSum.add(line.getAmount());
                    hasAmount = true;
                }
                if (line.getPurchaseUnitPrice() != null) {
                    // 退药单价为负；按绝对值判同价，避免冲减行清空单价
                    String priceKey =
                        line.getPurchaseUnitPrice().abs().stripTrailingZeros().toPlainString();
                    purchaseUnitPriceKeys.add(priceKey);
                    solePurchaseUnitPrice = line.getPurchaseUnitPrice().abs();
                }
                if (line.getSaleUnitPrice() != null) {
                    String priceKey =
                        line.getSaleUnitPrice().abs().stripTrailingZeros().toPlainString();
                    saleUnitPriceKeys.add(priceKey);
                    soleSaleUnitPrice = line.getSaleUnitPrice().abs();
                }
                String feeKey = this.buildFeeNatureHomogeneityKey(
                    line.getFeeNatureContractNo(), line.getFeeNatureDisplay());
                feeNatureKeys.add(feeKey);
                soleFeeNatureContractNo = line.getFeeNatureContractNo();
                soleFeeNatureDisplay = line.getFeeNatureDisplay();
            }
            // 净量以页内回载明细为准（领域分组查询不再做 SQL 符号 SUM）
            merged.setQuantity(MonetaryAmountSupport.scaleCalc(quantitySum));
            if (hasPurchase) {
                merged.setPurchaseAmount(MonetaryAmountSupport.scaleCalc(purchaseSum));
            }
            if (hasSale) {
                merged.setSaleAmount(MonetaryAmountSupport.scaleCalc(saleSum));
            }
            if (hasAmount) {
                merged.setAmount(MonetaryAmountSupport.scaleCalc(amountSum));
            }
            // 单价：组内唯一非空值才保留，否则置空
            if (purchaseUnitPriceKeys.size() == 1) {
                merged.setPurchaseUnitPrice(MonetaryAmountSupport.scaleCalc(solePurchaseUnitPrice));
            } else {
                merged.setPurchaseUnitPrice(null);
            }
            if (saleUnitPriceKeys.size() == 1) {
                merged.setSaleUnitPrice(MonetaryAmountSupport.scaleCalc(soleSaleUnitPrice));
            } else {
                merged.setSaleUnitPrice(null);
            }
            // 费用性质：组内同质保留，异质清空
            if (feeNatureKeys.size() == 1) {
                merged.setFeeNatureContractNo(soleFeeNatureContractNo);
                merged.setFeeNatureDisplay(soleFeeNatureDisplay);
            } else {
                merged.setFeeNatureContractNo(null);
                merged.setFeeNatureDisplay(null);
            }
            // 合并行再次确保行级批号/执行人清空（展示 enrich 可能写过空名）
            merged.setDispenseId(null);
            merged.setBasedOnDispenseId(null);
            merged.setDeviceReqId(null);
            merged.setBusinessTime(null);
            merged.setPerformerId(null);
            merged.setPerformerName(null);
            merged.setLotNumber(null);
            merged.setChargeLotNumber(null);
            merged.setLotMismatch(null);
            merged.setStatusOrTypeEnum(null);
            merged.setStatusOrTypeEnum_enumText(null);
        }
    }

    /**
     * 构建合并键字符串（encounter|deviceDef|unit）；任一主键空则返回 null。
     *
     * @param encounterId 就诊主键
     * @param deviceDefId 品规主键
     * @param unitCode    单位码；可空（空与有值分键）
     * @return 键；不可合并时 null
     */
    private String buildMergedPatientDeviceUnitKey(Long encounterId, Long deviceDefId, String unitCode) {
        if (encounterId == null || deviceDefId == null) {
            return null;
        }
        String unitPart = unitCode;
        if (unitPart == null) {
            unitPart = "\u0000";
        }
        return encounterId + "|" + deviceDefId + "|" + unitPart;
    }

    /**
     * 费用性质同质性比较键（合同号优先；皆空视为同一空桶）。
     *
     * @param contractNo 费用性质合同号
     * @param display    展示名
     * @return 比较键
     */
    private String buildFeeNatureHomogeneityKey(String contractNo, String display) {
        String contract = StringUtils.trimToNull(contractNo);
        if (contract != null) {
            return "C:" + contract;
        }
        String displayText = StringUtils.trimToNull(display);
        if (displayText != null) {
            return "D:" + displayText;
        }
        return "";
    }

    /**
     * 是否启用按定义合并（{@code BY_DEFINITION}）；空/未知视为 LINE。
     *
     * @param filter 报表筛选
     * @return true 表示患者源走合并分页
     */
    private boolean isMergeByDefinition(ConsumableUsageReportQueryFilter filter) {
        if (filter == null) {
            return false;
        }
        String mode = StringUtils.trimToNull(filter.getLineMergeMode());
        if (mode == null) {
            return false;
        }
        return ConsumableUsageReportConstants.LINE_MERGE_MODE_BY_DEFINITION.equals(mode);
    }

    /**
     * 患者侧明细映射；退药数量取负。
     *
     * @param models 领域发放行
     * @return 映射后的报表明细（未展示 enrich）
     */
    private List<InpatientConsumableUsageDetailRowDto> mapPatientDetailLines(
        List<ConsumableUsageDispenseLineQueryModel> models) {
        List<InpatientConsumableUsageDetailRowDto> rows = new ArrayList<>();
        if (models == null) {
            return rows;
        }
        for (ConsumableUsageDispenseLineQueryModel model : models) {
            if (model == null) {
                continue;
            }
            InpatientConsumableUsageDetailRowDto row = new InpatientConsumableUsageDetailRowDto();
            row.setSourceCode(ConsumableUsageReportConstants.SOURCE_CODE_PATIENT_DISPENSE);
            row.setSourceCode_dictText("患者使用");
            row.setDispenseId(model.getDispenseId());
            row.setBasedOnDispenseId(model.getBasedOnDispenseId());
            row.setDeviceReqId(model.getDeviceReqId());
            row.setDeviceDefId(model.getDeviceDefId());
            row.setEncounterId(model.getEncounterId());
            row.setPatientId(model.getPatientId());
            row.setLocationId(model.getLocationId());
            row.setStatusOrTypeEnum(model.getStatusEnum());
            row.setUnitCode(model.getUnitCode());
            row.setLotNumber(model.getLotNumber());
            row.setBusinessTime(model.getDispenseTime());
            // 发耗材人：患者发放取执行人
            row.setPerformerId(model.getPerformerId());
            BigDecimal quantity = model.getQuantity();
            if (quantity == null) {
                quantity = BigDecimal.ZERO;
            }
            // 已退药冲减：数量取负
            if (DispenseStatus.REFUNDED.getCode().equals(model.getStatusEnum())) {
                quantity = quantity.negate();
            }
            row.setQuantity(quantity);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 科室侧明细映射；退领数量/金额取负。
     *
     * @param models 领域领用行
     * @return 映射后的报表明细（未展示 enrich）
     */
    private List<InpatientConsumableUsageDetailRowDto> mapDeptDetailLines(
        List<DeptIssueReportLineQueryModel> models) {
        List<InpatientConsumableUsageDetailRowDto> rows = new ArrayList<>();
        if (models == null) {
            return rows;
        }
        for (DeptIssueReportLineQueryModel model : models) {
            if (model == null) {
                continue;
            }
            InpatientConsumableUsageDetailRowDto row = new InpatientConsumableUsageDetailRowDto();
            row.setSourceCode(ConsumableUsageReportConstants.SOURCE_CODE_DEPT_ISSUE);
            row.setSourceCode_dictText("科室领用");
            row.setSupplyRequestId(model.getSupplyRequestId());
            row.setBusNo(model.getBusNo());
            row.setDeviceDefId(model.getItemId());
            row.setLocationId(model.getPurposeLocationId());
            // 领用科室：优先落库 consumingOrganizationId（后续 enrich 回落药房上级科室）
            row.setOrganizationId(model.getConsumingOrganizationId());
            row.setStatusOrTypeEnum(model.getTypeEnum());
            row.setUnitCode(model.getUnitCode());
            row.setLotNumber(model.getLotNumber());
            row.setBusinessTime(model.getBusinessTime());
            // 发耗材人：科室领用取审批人
            row.setPerformerId(model.getApproverId());
            BigDecimal quantity = model.getItemQuantity();
            if (quantity == null) {
                quantity = BigDecimal.ZERO;
            }
            BigDecimal amount = model.getTotalPrice();
            if (amount == null) {
                amount = BigDecimal.ZERO;
            }
            // 退领冲减：数量与进价取负
            if (SupplyType.DEPT_ISSUE_RETURN.getCode().equals(model.getTypeEnum())) {
                quantity = quantity.negate();
                amount = amount.negate();
            }
            row.setQuantity(quantity);
            row.setPurchaseUnitPrice(MonetaryAmountSupport.scaleCalc(model.getPrice()));
            row.setPurchaseAmount(MonetaryAmountSupport.scaleCalc(amount));
            this.consumableUsageReportAmountEnricher.enrichDeptIssueAmounts(row);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 汇总金额专用：仅补 organizationId 与患者侧进售价，禁止姓名/品名/库位名/settlement 加载。
     *
     * @param rows 已 map 的明细行
     */
    private void enrichDetailAmountsOnly(List<InpatientConsumableUsageDetailRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> locationIdsMissingOrg = new LinkedHashSet<>();
        for (InpatientConsumableUsageDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            // 患者侧：经就诊补 organizationId
            if (row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
            // 科室侧：已有 organizationId 保留；缺失时仅用库位回落 id（不查名称）
            if (row.getOrganizationId() == null && row.getLocationId() != null) {
                locationIdsMissingOrg.add(row.getLocationId());
            }
        }
        Map<Long, Long> orgByEncounter =
            this.consumableUsageReportScopeLoader.mapOrganizationIdByEncounterIds(new ArrayList<>(encounterIds));
        Map<Long, Long> orgByLocation = Map.of();
        if (!locationIdsMissingOrg.isEmpty()) {
            orgByLocation = this.consumableUsageReportScopeLoader.mapOrganizationIdByLocationIds(
                new ArrayList<>(locationIdsMissingOrg));
        }
        List<InpatientConsumableUsageDetailRowDto> patientAmountLines = new ArrayList<>();
        for (InpatientConsumableUsageDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getEncounterId() != null) {
                row.setOrganizationId(orgByEncounter.get(row.getEncounterId()));
            } else if (row.getOrganizationId() == null && row.getLocationId() != null) {
                row.setOrganizationId(orgByLocation.get(row.getLocationId()));
            }
            if (ConsumableUsageReportConstants.SOURCE_CODE_PATIENT_DISPENSE.equals(row.getSourceCode())) {
                patientAmountLines.add(row);
            }
        }
        // Asm：患者侧金额经 ChargeItem；科室侧已在映射时写入；汇总路径不做账批质检
        this.consumableUsageReportAmountEnricher.enrichPatientDispenseAmounts(
            new ArrayList<>(patientAmountLines));
        // 退药金额随数量符号：已 enrich 的正金额在退药行取负
        for (InpatientConsumableUsageDetailRowDto row : patientAmountLines) {
            if (row.getQuantity() != null && row.getQuantity().signum() < 0) {
                this.negatePositiveAmountIfPresent(row.getAmount(), row::setAmount);
                this.negatePositiveAmountIfPresent(row.getSaleAmount(), row::setSaleAmount);
                this.negatePositiveAmountIfPresent(row.getPurchaseAmount(), row::setPurchaseAmount);
            }
        }
    }

    /**
     * 回填明细展示字段与患者侧金额。
     *
     * @param rows 已映射明细行
     */
    private void enrichDetailDisplay(List<InpatientConsumableUsageDetailRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> deviceDefIds = new LinkedHashSet<>();
        LinkedHashSet<Long> patientIds = new LinkedHashSet<>();
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> locationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> prefilledOrgIds = new LinkedHashSet<>();
        LinkedHashSet<Long> performerIds = new LinkedHashSet<>();
        for (InpatientConsumableUsageDetailRowDto row : rows) {
            if (row.getDeviceDefId() != null) {
                deviceDefIds.add(row.getDeviceDefId());
            }
            if (row.getPatientId() != null) {
                patientIds.add(row.getPatientId());
            }
            if (row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
            if (row.getLocationId() != null) {
                locationIds.add(row.getLocationId());
            }
            // 科室领用行可能已带落库领用科室 id
            if (row.getOrganizationId() != null) {
                prefilledOrgIds.add(row.getOrganizationId());
            }
            if (row.getPerformerId() != null) {
                performerIds.add(row.getPerformerId());
            }
        }
        Map<Long, DeviceDefinition> deviceById = this.loadDeviceMap(deviceDefIds);
        Map<Long, String> patientNameById = this.patientQueryService.findPatientNamesByIds(patientIds);
        Map<Long, String> encounterBusNoById = this.encounterQueryService.mapEncounterBusNoByIds(encounterIds);
        Map<Long, Long> orgByEncounter =
            this.consumableUsageReportScopeLoader.mapOrganizationIdByEncounterIds(new ArrayList<>(encounterIds));
        Map<Long, Long> orgByLocation =
            this.consumableUsageReportScopeLoader.mapOrganizationIdByLocationIds(new ArrayList<>(locationIds));
        LinkedHashSet<Long> organizationIds = new LinkedHashSet<>();
        organizationIds.addAll(orgByEncounter.values());
        organizationIds.addAll(orgByLocation.values());
        organizationIds.addAll(prefilledOrgIds);
        Map<Long, String> orgNameById = this.organizationQueryService.mapNamesByIds(organizationIds);
        Map<Long, String> locationNameById = this.locationQueryService.mapNamesByIds(locationIds);
        Map<Long, String> performerNameById = this.loadPerformerNameById(performerIds);
        Map<String, String> unitLabelMap =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        // Pre：住院上下文——展示病区/床位（活动优先，无则末次 COMPLETED）
        Map<Long, Long> wardLocationIdByEncounterId = new HashMap<>();
        Map<Long, Long> bedLocationIdByEncounterId = new HashMap<>();
        if (!encounterIds.isEmpty()) {
            wardLocationIdByEncounterId.putAll(
                this.encounterQueryService.mapDisplayWardLocationIdByEncounterIds(encounterIds));
            bedLocationIdByEncounterId.putAll(
                this.encounterQueryService.mapDisplayBedLocationIdByEncounterIds(encounterIds));
        }
        LinkedHashSet<Long> wardBedLocationIds = new LinkedHashSet<>();
        wardBedLocationIds.addAll(wardLocationIdByEncounterId.values());
        wardBedLocationIds.addAll(bedLocationIdByEncounterId.values());
        Map<Long, String> wardBedNameById = this.locationQueryService.mapNamesByIds(wardBedLocationIds);
        List<InpatientConsumableUsageDetailRowDto> patientAmountLines = new ArrayList<>();
        for (InpatientConsumableUsageDetailRowDto row : rows) {
            DeviceDefinition device = deviceById.get(row.getDeviceDefId());
            if (device != null) {
                row.setDeviceName(device.getName());
                // 规格取包装规格 totalVolume（与护士工作量/药品规格口径一致）；modelNumber 为产品型号
                if (device.getTotalVolume() != null) {
                    row.setSpecText(device.getTotalVolume().trim());
                }
            }
            if (row.getPatientId() != null) {
                row.setPatientName(patientNameById.get(row.getPatientId()));
            }
            if (row.getEncounterId() != null) {
                row.setEncounterBusNo(encounterBusNoById.get(row.getEncounterId()));
                Long orgId = orgByEncounter.get(row.getEncounterId());
                row.setOrganizationId(orgId);
                if (orgId != null) {
                    row.setOrganizationName(orgNameById.get(orgId));
                }
                Long wardLocationId = wardLocationIdByEncounterId.get(row.getEncounterId());
                if (wardLocationId != null) {
                    row.setWardName(wardBedNameById.get(wardLocationId));
                }
                Long bedLocationId = bedLocationIdByEncounterId.get(row.getEncounterId());
                if (bedLocationId != null) {
                    row.setBedName(wardBedNameById.get(bedLocationId));
                }
            } else if (row.getLocationId() != null || row.getOrganizationId() != null) {
                // 科室领用：已落库 consumingOrganizationId 优先；历史单回落药房上级科室
                if (row.getOrganizationId() == null && row.getLocationId() != null) {
                    row.setOrganizationId(orgByLocation.get(row.getLocationId()));
                }
                if (row.getOrganizationId() != null) {
                    row.setOrganizationName(orgNameById.get(row.getOrganizationId()));
                }
            }
            if (row.getLocationId() != null) {
                row.setLocationName(locationNameById.get(row.getLocationId()));
            }
            if (row.getPerformerId() != null) {
                row.setPerformerName(performerNameById.get(row.getPerformerId()));
            }
            if (row.getUnitCode() != null) {
                row.setUnitCode_dictText(unitLabelMap.get(row.getUnitCode()));
            }
            row.setStatusOrTypeEnum_enumText(this.resolveStatusOrTypeText(row.getStatusOrTypeEnum()));
            if (ConsumableUsageReportConstants.SOURCE_CODE_PATIENT_DISPENSE.equals(row.getSourceCode())) {
                patientAmountLines.add(row);
            }
        }
        // Asm：患者侧金额经 ChargeItem；科室侧已在映射时写入；明细分页开启账批质检
        this.consumableUsageReportAmountEnricher.enrichPatientDispenseAmounts(
            new ArrayList<>(patientAmountLines), true);
        // 退药金额随数量符号：已 enrich 的正金额在退药行取负
        for (InpatientConsumableUsageDetailRowDto row : patientAmountLines) {
            if (row.getQuantity() != null && row.getQuantity().signum() < 0) {
                this.negatePositiveAmountIfPresent(row.getAmount(), row::setAmount);
                this.negatePositiveAmountIfPresent(row.getSaleAmount(), row::setSaleAmount);
                this.negatePositiveAmountIfPresent(row.getPurchaseAmount(), row::setPurchaseAmount);
            }
        }
        this.enrichPatientSettlementContext(rows);
    }

    /**
     * 患者侧明细行回填费用性质与 medType。
     *
     * <p>费用性质：发放收费行挂账账户；医疗类别仍取就诊主诊；科室领用无 encounter/发放则留空。</p>
     *
     * @param rows 已装配明细行
     */
    private void enrichPatientSettlementContext(List<InpatientConsumableUsageDetailRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        List<InpatientConsumableUsageDetailRowDto> patientRows = new ArrayList<>();
        for (InpatientConsumableUsageDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
            if (row.getDispenseId() != null) {
                patientRows.add(row);
            }
        }
        Map<Long, PatientSettlementContextRow> contextByEncounterId =
            this.reportPatientSettlementContextAssembler.mapContextsByEncounterIds(encounterIds);
        Map<Long, Long> accountIdByDispenseId =
            this.consumableUsageReportAmountEnricher.mapAccountIdByDispenseId(patientRows);
        Map<Long, PatientSettlementContextRow> feeNatureByAccountId =
            this.reportPatientSettlementContextAssembler.mapFeeNatureContextsByAccountIds(
                accountIdByDispenseId.values());
        for (InpatientConsumableUsageDetailRowDto row : rows) {
            if (row == null) {
                continue;
            }
            if (row.getEncounterId() != null) {
                PatientSettlementContextRow context = contextByEncounterId.get(row.getEncounterId());
                if (context != null) {
                    row.setMedType(context.getMedType());
                    row.setMedTypeDisplay(context.getMedTypeDisplay());
                }
            }
            if (row.getDispenseId() == null) {
                continue;
            }
            Long accountId = accountIdByDispenseId.get(row.getDispenseId());
            if (accountId == null) {
                continue;
            }
            PatientSettlementContextRow feeNature = feeNatureByAccountId.get(accountId);
            if (feeNature == null) {
                continue;
            }
            row.setFeeNatureContractNo(feeNature.getFeeNatureContractNo());
            row.setFeeNatureDisplay(feeNature.getFeeNatureDisplay());
        }
    }

    /**
     * 正数金额取负（退药/退领符号对齐）。
     *
     * @param value  当前值
     * @param setter 写入器
     */
    private void negatePositiveAmountIfPresent(
        BigDecimal value,
        java.util.function.Consumer<BigDecimal> setter) {
        if (value != null && value.signum() > 0) {
            setter.accept(value.negate());
        }
    }

    /**
     * 患者侧汇总映射。
     */
    private List<InpatientConsumableUsageSummaryRowDto> mapPatientSummaries(
        List<ConsumableUsageDispenseSummaryRow> summaries,
        String dimension) {
        List<InpatientConsumableUsageSummaryRowDto> rows = new ArrayList<>();
        if (summaries == null) {
            return rows;
        }
        for (ConsumableUsageDispenseSummaryRow summary : summaries) {
            if (summary == null) {
                continue;
            }
            InpatientConsumableUsageSummaryRowDto row = new InpatientConsumableUsageSummaryRowDto();
            row.setDimensionKey(summary.getDimensionKey());
            row.setSummaryDimension(dimension);
            row.setPatientId(summary.getPatientId());
            row.setOrganizationId(summary.getOrganizationId());
            row.setLocationId(summary.getLocationId());
            row.setDeviceDefId(summary.getDeviceDefId());
            row.setNetQuantity(summary.getNetQuantity());
            // 进售价由金额 enrich 回填；禁止预置 0 伪装「已算价」
            rows.add(row);
        }
        return rows;
    }

    /**
     * 科室侧汇总映射；LOCATION 维将领域 {@code PHARMACY:} 键规范为 {@code LOCATION:}。
     *
     * @param summaries 科室领域汇总行
     * @param dimension 请求维（对外维，可为 LOCATION）
     * @return 应用层汇总行
     */
    private List<InpatientConsumableUsageSummaryRowDto> mapDeptSummaries(
        List<DeptIssueReportSummaryRow> summaries,
        String dimension) {
        List<InpatientConsumableUsageSummaryRowDto> rows = new ArrayList<>();
        if (summaries == null) {
            return rows;
        }
        for (DeptIssueReportSummaryRow summary : summaries) {
            if (summary == null) {
                continue;
            }
            InpatientConsumableUsageSummaryRowDto row = new InpatientConsumableUsageSummaryRowDto();
            row.setDimensionKey(this.normalizeDeptDimensionKey(summary.getDimensionKey(), dimension));
            row.setSummaryDimension(dimension);
            row.setOrganizationId(summary.getOrganizationId());
            row.setLocationId(summary.getPurposeLocationId());
            row.setDeviceDefId(summary.getItemId());
            row.setNetQuantity(summary.getNetQuantity());
            // 进售价由金额 enrich 回填；禁止预置 0 伪装「已算价」
            rows.add(row);
        }
        return rows;
    }

    /**
     * 按 dimensionKey 合并两路汇总净量。
     */
    private List<InpatientConsumableUsageSummaryRowDto> mergeSummaryRows(
        List<InpatientConsumableUsageSummaryRowDto> left,
        List<InpatientConsumableUsageSummaryRowDto> right) {
        Map<String, InpatientConsumableUsageSummaryRowDto> bucket = new LinkedHashMap<>();
        this.putSummaryBuckets(bucket, left);
        this.putSummaryBuckets(bucket, right);
        return new ArrayList<>(bucket.values());
    }

    /**
     * 写入汇总分桶。
     */
    private void putSummaryBuckets(
        Map<String, InpatientConsumableUsageSummaryRowDto> bucket,
        List<InpatientConsumableUsageSummaryRowDto> rows) {
        if (rows == null) {
            return;
        }
        for (InpatientConsumableUsageSummaryRowDto row : rows) {
            if (row == null || row.getDimensionKey() == null) {
                continue;
            }
            InpatientConsumableUsageSummaryRowDto existing = bucket.get(row.getDimensionKey());
            if (existing == null) {
                bucket.put(row.getDimensionKey(), row);
                continue;
            }
            BigDecimal leftQty = existing.getNetQuantity();
            if (leftQty == null) {
                leftQty = BigDecimal.ZERO;
            }
            BigDecimal rightQty = row.getNetQuantity();
            if (rightQty == null) {
                rightQty = BigDecimal.ZERO;
            }
            existing.setNetQuantity(leftQty.add(rightQty));
        }
    }

    /**
     * 回填汇总展示名：统一写入 {@code dimensionName}（耗材房/品规/类型/科室）。
     *
     * @param rows      汇总行
     * @param dimension 请求维
     */
    private void enrichSummaryDisplay(List<InpatientConsumableUsageSummaryRowDto> rows, String dimension) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> patientIds = new LinkedHashSet<>();
        LinkedHashSet<Long> organizationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> locationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> deviceDefIds = new LinkedHashSet<>();
        for (InpatientConsumableUsageSummaryRowDto row : rows) {
            if (row.getPatientId() != null) {
                patientIds.add(row.getPatientId());
            }
            if (row.getOrganizationId() != null) {
                organizationIds.add(row.getOrganizationId());
            }
            if (row.getLocationId() != null) {
                locationIds.add(row.getLocationId());
            }
            if (row.getDeviceDefId() != null) {
                deviceDefIds.add(row.getDeviceDefId());
            }
        }
        Map<Long, String> patientNameById = this.patientQueryService.findPatientNamesByIds(patientIds);
        Map<Long, String> orgNameById = this.organizationQueryService.mapNamesByIds(organizationIds);
        Map<Long, String> locationNameById = this.locationQueryService.mapNamesByIds(locationIds);
        Map<Long, DeviceDefinition> deviceById = this.loadDeviceMap(deviceDefIds);
        // 类型维：术语码→展示名（禁止把 categoryCode 原样当 dimensionName）
        Map<String, String> categoryLabelByCode = Map.of();
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_CATEGORY.equals(dimension)) {
            categoryLabelByCode = this.terminologyOptionLoader.buildLabelMap(
                TerminologyCodeSystemNameConstants.DEVICE_CATEGORY_CODE);
        }
        for (InpatientConsumableUsageSummaryRowDto row : rows) {
            if (row.getPatientId() != null) {
                row.setPatientName(patientNameById.get(row.getPatientId()));
            }
            if (row.getOrganizationId() != null) {
                row.setOrganizationName(orgNameById.get(row.getOrganizationId()));
            }
            if (row.getLocationId() != null) {
                row.setLocationName(locationNameById.get(row.getLocationId()));
            }
            DeviceDefinition device = deviceById.get(row.getDeviceDefId());
            if (device != null) {
                row.setDeviceName(device.getName());
            }
            // Asm：按维写统一维度列
            if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_ITEM.equals(dimension)) {
                row.setDimensionName(row.getDeviceName());
            } else if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_CATEGORY.equals(dimension)) {
                String categoryCode = row.getCategoryCode();
                String categoryLabel =
                    OrderCatalogDisplayAssembler.resolveCategoryDisplay(categoryLabelByCode, categoryCode);
                if (StringUtils.isNotEmpty(categoryLabel)) {
                    row.setDimensionName(categoryLabel);
                } else {
                    row.setDimensionName(categoryCode);
                }
            } else if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_DEPARTMENT.equals(dimension)) {
                row.setDimensionName(row.getOrganizationName());
            } else if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_LOCATION.equals(dimension)) {
                row.setDimensionName(row.getLocationName());
            } else {
                // 遗留 PATIENT 维：患者姓名
                row.setDimensionName(row.getPatientName());
            }
        }
    }

    /**
     * 批量加载耗材定义。
     */
    private Map<Long, DeviceDefinition> loadDeviceMap(LinkedHashSet<Long> deviceDefIds) {
        Map<Long, DeviceDefinition> deviceById = new HashMap<>();
        if (deviceDefIds == null || deviceDefIds.isEmpty()) {
            return deviceById;
        }
        List<DeviceDefinition> devices = this.deviceDefinitionQueryService.getAllDeviceDefinitions(deviceDefIds);
        for (DeviceDefinition device : devices) {
            if (device == null || device.getId() == null) {
                continue;
            }
            deviceById.put(device.getId(), device);
        }
        return deviceById;
    }

    /**
     * 批量加载执行人（发耗材人）姓名。
     *
     * @param performerIds 医务人员主键
     * @return id → 姓名
     */
    private Map<Long, String> loadPerformerNameById(LinkedHashSet<Long> performerIds) {
        Map<Long, String> performerNameById = new HashMap<>();
        if (performerIds == null || performerIds.isEmpty()) {
            return performerNameById;
        }
        List<Practitioner> practitioners = this.practitionerQueryService.findAllByIds(performerIds);
        for (Practitioner practitioner : practitioners) {
            if (practitioner == null || practitioner.getId() == null) {
                continue;
            }
            if (StringUtils.isEmpty(practitioner.getName())) {
                continue;
            }
            performerNameById.put(practitioner.getId(), practitioner.getName().trim());
        }
        return performerNameById;
    }

    /**
     * 汇总页脚：净数量加总（不做结算刻度）。
     *
     * @param rows 汇总行
     * @return 净数量合计；空表为零
     */
    private BigDecimal sumNetQuantity(List<InpatientConsumableUsageSummaryRowDto> rows) {
        BigDecimal total = BigDecimal.ZERO;
        // Asm：跳过空行与空数量，避免 NPE
        for (InpatientConsumableUsageSummaryRowDto row : rows) {
            if (row == null || row.getNetQuantity() == null) {
                continue;
            }
            total = total.add(row.getNetQuantity());
        }
        return total;
    }

    /**
     * 汇总页脚：售价净额经金额 enricher 结算刻度加总（BR-MON-001）。
     *
     * @param rows 汇总行
     * @return 结算刻度合计
     */
    private BigDecimal sumNetAmount(List<InpatientConsumableUsageSummaryRowDto> rows) {
        List<BigDecimal> amounts = new ArrayList<>();
        for (InpatientConsumableUsageSummaryRowDto row : rows) {
            if (row == null || row.getSaleNetAmount() == null) {
                continue;
            }
            amounts.add(row.getSaleNetAmount());
        }
        return this.consumableUsageReportAmountEnricher.sumThenSettle(amounts);
    }

    /**
     * 汇总维缺省回落耗材房（对齐药品默认药房）。
     *
     * @param summaryDimension 请求维；可空
     * @return LOCATION / ITEM / CATEGORY / DEPARTMENT 等
     */
    private String resolveSummaryDimension(String summaryDimension) {
        String trimmed = StringUtils.trimToNull(summaryDimension);
        // Gate：未传维时按耗材房净用量
        if (trimmed == null) {
            return ConsumableUsageReportConstants.SUMMARY_DIMENSION_LOCATION;
        }
        return trimmed;
    }

    /**
     * 患者侧领域维：CATEGORY 先按 ITEM 分桶。
     *
     * @param requestDimension 请求维
     * @return 领域 summaryDimension
     */
    private String resolvePatientDomainDimension(String requestDimension) {
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_CATEGORY.equals(requestDimension)) {
            return ConsumableUsageReportConstants.SUMMARY_DIMENSION_ITEM;
        }
        return requestDimension;
    }

    /**
     * 科室侧领域维：LOCATION→PHARMACY，CATEGORY→ITEM。
     *
     * @param requestDimension 请求维
     * @return 传入 ScopeLoader / 领域的维码
     */
    private String resolveDeptDomainDimension(String requestDimension) {
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_LOCATION.equals(requestDimension)) {
            return DeptIssueReportConstants.SUMMARY_DIMENSION_PHARMACY;
        }
        if (ConsumableUsageReportConstants.SUMMARY_DIMENSION_CATEGORY.equals(requestDimension)) {
            return DeptIssueReportConstants.SUMMARY_DIMENSION_ITEM;
        }
        return requestDimension;
    }

    /**
     * 科室领域键规范为对外 LOCATION: 前缀（金额 enrich 同口径）。
     *
     * @param domainKey 领域 dimensionKey
     * @param dimension 请求维
     * @return 规范化键
     */
    private String normalizeDeptDimensionKey(String domainKey, String dimension) {
        if (domainKey == null) {
            return null;
        }
        if (!ConsumableUsageReportConstants.SUMMARY_DIMENSION_LOCATION.equals(dimension)) {
            return domainKey;
        }
        if (domainKey.startsWith("PHARMACY:")) {
            return "LOCATION:" + domainKey.substring("PHARMACY:".length());
        }
        return domainKey;
    }

    /**
     * 收集汇总行品规主键。
     *
     * @param rows 汇总行
     * @return 去重品规 id
     */
    private LinkedHashSet<Long> collectDeviceDefIds(List<InpatientConsumableUsageSummaryRowDto> rows) {
        LinkedHashSet<Long> deviceDefIds = new LinkedHashSet<>();
        if (rows == null) {
            return deviceDefIds;
        }
        for (InpatientConsumableUsageSummaryRowDto row : rows) {
            if (row != null && row.getDeviceDefId() != null) {
                deviceDefIds.add(row.getDeviceDefId());
            }
        }
        return deviceDefIds;
    }

    /**
     * 收集事实行品规主键（CATEGORY 维补全类型映射）。
     *
     * @param patientLineRows 患者侧同窗事实行
     * @param deptLineRows    领用侧同窗事实行
     * @return 去重品规 id
     */
    private LinkedHashSet<Long> collectDeviceDefIdsFromLineRows(
        List<ConsumableUsageDispenseLineQueryModel> patientLineRows,
        List<DeptIssueReportLineQueryModel> deptLineRows) {
        LinkedHashSet<Long> deviceDefIds = new LinkedHashSet<>();
        if (patientLineRows != null) {
            for (ConsumableUsageDispenseLineQueryModel line : patientLineRows) {
                if (line != null && line.getDeviceDefId() != null) {
                    deviceDefIds.add(line.getDeviceDefId());
                }
            }
        }
        if (deptLineRows != null) {
            for (DeptIssueReportLineQueryModel line : deptLineRows) {
                if (line != null && line.getItemId() != null) {
                    deviceDefIds.add(line.getItemId());
                }
            }
        }
        return deviceDefIds;
    }

    /**
     * 批量解析品规 → 耗材类型 code。
     *
     * @param deviceDefIds 品规主键
     * @return id → categoryCode
     */
    private Map<Long, String> resolveCategoryCodeByDeviceDefIds(LinkedHashSet<Long> deviceDefIds) {
        Map<Long, String> categoryById = new HashMap<>();
        if (deviceDefIds == null || deviceDefIds.isEmpty()) {
            return categoryById;
        }
        Map<Long, DeviceDefinition> deviceById = this.loadDeviceMap(deviceDefIds);
        for (Map.Entry<Long, DeviceDefinition> entry : deviceById.entrySet()) {
            DeviceDefinition device = entry.getValue();
            if (device == null) {
                continue;
            }
            categoryById.put(entry.getKey(), device.getCategoryCode());
        }
        return categoryById;
    }

    /**
     * 将 ITEM 汇总行再按类型分桶为 CATEGORY 行。
     *
     * @param itemRows                   ITEM 维汇总行
     * @param categoryCodeByDeviceDefId  品规→类型
     * @return CATEGORY 维汇总行
     */
    private List<InpatientConsumableUsageSummaryRowDto> rebucketItemRowsToCategory(
        List<InpatientConsumableUsageSummaryRowDto> itemRows,
        Map<Long, String> categoryCodeByDeviceDefId) {
        Map<String, InpatientConsumableUsageSummaryRowDto> bucket = new LinkedHashMap<>();
        if (itemRows == null) {
            return new ArrayList<>();
        }
        Map<Long, String> categoryMap = categoryCodeByDeviceDefId;
        if (categoryMap == null) {
            categoryMap = Map.of();
        }
        for (InpatientConsumableUsageSummaryRowDto itemRow : itemRows) {
            if (itemRow == null) {
                continue;
            }
            String categoryCode = "";
            if (itemRow.getDeviceDefId() != null) {
                String mapped = categoryMap.get(itemRow.getDeviceDefId());
                if (mapped != null) {
                    categoryCode = mapped;
                }
            }
            String key = "CATEGORY:" + categoryCode;
            InpatientConsumableUsageSummaryRowDto existing = bucket.get(key);
            if (existing == null) {
                InpatientConsumableUsageSummaryRowDto categoryRow = new InpatientConsumableUsageSummaryRowDto();
                categoryRow.setDimensionKey(key);
                categoryRow.setSummaryDimension(ConsumableUsageReportConstants.SUMMARY_DIMENSION_CATEGORY);
                categoryRow.setCategoryCode(categoryCode);
                categoryRow.setNetQuantity(itemRow.getNetQuantity());
                // 进售价由金额 enrich 回填；禁止预置 0
                bucket.put(key, categoryRow);
                continue;
            }
            BigDecimal leftQty = existing.getNetQuantity();
            if (leftQty == null) {
                leftQty = BigDecimal.ZERO;
            }
            BigDecimal rightQty = itemRow.getNetQuantity();
            if (rightQty == null) {
                rightQty = BigDecimal.ZERO;
            }
            existing.setNetQuantity(leftQty.add(rightQty));
        }
        return new ArrayList<>(bucket.values());
    }

    /**
     * 来源筛缺省回落患者使用；ALL / 未知码拒绝（禁止并集混查）。
     *
     * @param sourceCode 请求来源；可空
     * @return PATIENT_DISPENSE / DEPT_ISSUE
     * @throws BusinessException 传入 ALL 或非法码时拒绝
     */
    private String resolveSourceCode(String sourceCode) {
        String trimmed = StringUtils.trimToNull(sourceCode);
        // Gate：未传默认患者使用
        if (trimmed == null) {
            return ConsumableUsageReportConstants.SOURCE_CODE_PATIENT_DISPENSE;
        }
        // Gate：遗留 ALL 明确拒绝，禁止两路并集全窗物化
        if (ConsumableUsageReportConstants.SOURCE_CODE_ALL.equals(trimmed)) {
            throw new BusinessException(
                ConsumableUsageReportConstants.EX_RPT_ICU_103,
                "禁止全部来源混查");
        }
        if (ConsumableUsageReportConstants.SOURCE_CODE_PATIENT_DISPENSE.equals(trimmed)
            || ConsumableUsageReportConstants.SOURCE_CODE_DEPT_ISSUE.equals(trimmed)) {
            return trimmed;
        }
        throw new BusinessException(
            ConsumableUsageReportConstants.EX_RPT_ICU_103,
            "禁止全部来源混查");
    }

    /**
     * 是否纳入患者发放一路。
     *
     * @param source 已规范化来源码
     * @return true 表示查询 DeviceDispense
     */
    private boolean includePatient(String source) {
        return ConsumableUsageReportConstants.SOURCE_CODE_PATIENT_DISPENSE.equals(source);
    }

    /**
     * 是否纳入科室领用一路。
     *
     * @param source 已规范化来源码
     * @return true 表示查询 Supply DEPT_ISSUE
     */
    private boolean includeDept(String source) {
        return ConsumableUsageReportConstants.SOURCE_CODE_DEPT_ISSUE.equals(source);
    }

    /**
     * 发放状态 / 领用类型码 → 报表展示文案。
     *
     * @param code statusEnum 或 typeEnum；可空
     * @return 中文展示；未知码原样回传
     */
    private String resolveStatusOrTypeText(String code) {
        // Gate：空码不展示占位文案
        if (StringUtils.isEmpty(code)) {
            return null;
        }
        if (DispenseStatus.COMPLETED.getCode().equals(code)) {
            return "已发放";
        }
        if (DispenseStatus.PART_COMPLETED.getCode().equals(code)) {
            return "部分发放";
        }
        if (DispenseStatus.REFUNDED.getCode().equals(code)) {
            return "已退药";
        }
        if (SupplyType.DEPT_ISSUE.getCode().equals(code)) {
            return "科室领用";
        }
        if (SupplyType.DEPT_ISSUE_RETURN.getCode().equals(code)) {
            return "科室退领";
        }
        // Asm：未识别码透出原值，便于联调对账
        return code;
    }
}
