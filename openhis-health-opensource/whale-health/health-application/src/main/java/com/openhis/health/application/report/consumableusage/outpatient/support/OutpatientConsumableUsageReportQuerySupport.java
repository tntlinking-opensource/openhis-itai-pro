/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.consumableusage.outpatient.support;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import com.openhis.health.application.report.consumableusage.outpatient.dto.OutpatientConsumableUsageDetailRowDto;
import com.openhis.health.application.report.consumableusage.outpatient.dto.OutpatientConsumableUsageSummaryRowDto;
import com.openhis.health.application.report.consumableusage.support.ConsumableUsageReportAmountEnricher;
import com.openhis.health.application.report.consumableusage.support.ConsumableUsageReportScopeLoader;
import com.openhis.health.application.shared.option.TerminologyOptionLoader;
import com.openhis.health.application.shared.report.PatientSettlementContextRow;
import com.openhis.health.application.shared.report.ReportPatientSettlementContextAssembler;
import com.openhis.health.domain.administration.devicedefinition.entity.DeviceDefinition;
import com.openhis.health.domain.administration.devicedefinition.service.DeviceDefinitionQueryService;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.share.constants.ConsumableUsageReportConstants;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.DispenseStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.SupplyType;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.health.domain.workflow.devicedispense.queries.model.ConsumableUsageDispenseLineQueryModel;
import com.openhis.health.domain.workflow.devicedispense.queries.model.ConsumableUsageDispenseSummaryRow;
import com.openhis.health.domain.workflow.devicedispense.query.ConsumableUsageReportPageQuery;
import com.openhis.health.domain.workflow.devicedispense.service.DeviceDispenseQueryService;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.DeptIssueReportLineQueryModel;
import com.openhis.health.domain.workflow.supplyrequest.queries.model.DeptIssueReportSummaryRow;
import com.openhis.health.domain.workflow.supplyrequest.query.DeptIssueReportPageQuery;
import com.openhis.health.domain.workflow.supplyrequest.service.SupplyRequestQueryService;
import com.openhis.whale.core.application.util.StringUtils;

import lombok.RequiredArgsConstructor;

/**
 * 门诊耗材使用报表两路并集编排（患者 DeviceDispense ∪ 科室 DEPT_ISSUE；AS-RPT-005）。
 *
 * <p>领域白名单（AS Gap / DM-WKF-007 §6.2.23～24、DM-WKF-006 §6.2.15）：
 * {@code pageConsumableUsageDispenseLines} / {@code listConsumableUsageDispenseLines} /
 * {@code summarizeConsumableUsageDispense}；
 * {@code pageDeptIssueReportLines} / {@code listDeptIssueReportLines} /
 * {@code summarizeDeptIssueReport}。门诊域由本 Support 预写 AMB encounter scope。</p>
 *
 * <p>禁止 import 本 Feature Request/Response。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientConsumableUsageReportQuerySupport {

    /** 患者侧发放读。 */
    private final DeviceDispenseQueryService deviceDispenseQueryService;

    /** 科室领用读。 */
    private final SupplyRequestQueryService supplyRequestQueryService;

    /** 就诊/库位/组织 scope 预解析。 */
    private final ConsumableUsageReportScopeLoader consumableUsageReportScopeLoader;

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

    /** 单位术语。 */
    private final TerminologyOptionLoader terminologyOptionLoader;

    /** 费用性质 / medType 上下文。 */
    private final ReportPatientSettlementContextAssembler reportPatientSettlementContextAssembler;

    /**
     * 汇总：两路净量按维合并；金额仅患者侧经 ChargeItem，科室侧用行价累加近似净额。
     *
     * @param dateFrom            日起
     * @param dateTo              日止
     * @param summaryDimension    汇总维
     * @param sourceCode          来源筛
     * @param locationId          可选库位
     * @param feeNatureContractNo 费用性质 bus_no
     * @return 汇总响应
     */
    public OutpatientConsumableUsageSummaryResult summarize(
        String dateFrom,
        String dateTo,
        String summaryDimension,
        String sourceCode,
        Long locationId,
        String feeNatureContractNo) {
        String dimension = this.resolveSummaryDimension(summaryDimension);
        String source = this.resolveSourceCode(sourceCode);
        List<OutpatientConsumableUsageSummaryRowDto> rows = new ArrayList<>();
        // Pre：按来源收窄一路或两路
        if (this.includePatient(source)) {
            ConsumableUsageReportPageQuery patientQuery = new ConsumableUsageReportPageQuery();
            this.preparePatientDispenseQueryWithFeeNature(
                patientQuery,
                EncounterClass.AMB.getCode(),
                dateFrom,
                dateTo,
                locationId,
                dimension,
                feeNatureContractNo);
            List<ConsumableUsageDispenseSummaryRow> patientSummaries =
                this.deviceDispenseQueryService.summarizeConsumableUsageDispense(patientQuery);
            rows.addAll(this.mapPatientSummaries(patientSummaries, dimension));
        }
        if (this.includeDept(source)) {
            DeptIssueReportPageQuery deptQuery = new DeptIssueReportPageQuery();
            this.consumableUsageReportScopeLoader.prepareDeptIssueQuery(
                deptQuery,
                EncounterClass.AMB.getCode(),
                dateFrom,
                dateTo,
                locationId,
                dimension);
            List<DeptIssueReportSummaryRow> deptSummaries =
                this.supplyRequestQueryService.summarizeDeptIssueReport(deptQuery);
            rows = this.mergeSummaryRows(rows, this.mapDeptSummaries(deptSummaries, dimension));
        }
        // Asm：回填展示名
        this.enrichSummaryDisplay(rows);
        // Pre：汇总金额仅拉定价所需明细，禁止姓名/品名等展示 enrich
        List<OutpatientConsumableUsageDetailRowDto> detailRows =
            this.listDetailForSummaryAmounts(dateFrom, dateTo, source, locationId, feeNatureContractNo);
        this.consumableUsageReportAmountEnricher.aggregateSummaryPurchaseSaleAmounts(
            rows, detailRows, dimension);
        OutpatientConsumableUsageSummaryResult response =
            new OutpatientConsumableUsageSummaryResult();
        response.setRows(rows);
        response.setTotalNetQuantity(this.sumNetQuantity(rows));
        response.setSaleNetTotalAmount(this.consumableUsageReportAmountEnricher.sumSaleNetAmount(rows));
        response.setPurchaseNetTotalAmount(this.consumableUsageReportAmountEnricher.sumPurchaseNetAmount(rows));
        response.setTotalNetAmount(response.getSaleNetTotalAmount());
        return response;
    }

    /**
     * 明细分页：两路并集按业务时间倒序后切页。
     *
     * @param dateFrom   日起
     * @param dateTo     日止
     * @param sourceCode 来源筛
     * @param locationId          可选库位
     * @param feeNatureContractNo 费用性质 bus_no
     * @param pageNo              页码（1 基）
     * @param pageSize            页大小
     * @return 明细分页
     */
    public OutpatientConsumableUsageDetailPageResult pageDetail(
        String dateFrom,
        String dateTo,
        String sourceCode,
        Long locationId,
        String feeNatureContractNo,
        Integer pageNo,
        Integer pageSize) {
        String source = this.resolveSourceCode(sourceCode);
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
        // Pre：单来源走领域原生分页；并集对齐发药报表前缀页 + mergeAndSlice
        if (ConsumableUsageReportConstants.SOURCE_CODE_PATIENT_DISPENSE.equals(source)) {
            return this.pagePatientOnly(
                dateFrom, dateTo, locationId, feeNatureContractNo, resolvedPageNo, resolvedPageSize);
        }
        if (ConsumableUsageReportConstants.SOURCE_CODE_DEPT_ISSUE.equals(source)) {
            return this.pageDeptOnly(dateFrom, dateTo, locationId, resolvedPageNo, resolvedPageSize);
        }
        // Pre：两路各取前 pageNo*pageSize 条（封顶 EXPORT_MAX_ROWS），再内存排序切当前页
        int fetchSize = resolvedPageNo * resolvedPageSize;
        if (fetchSize < resolvedPageSize) {
            fetchSize = resolvedPageSize;
        }
        if (fetchSize > ConsumableUsageReportConstants.EXPORT_MAX_ROWS) {
            fetchSize = ConsumableUsageReportConstants.EXPORT_MAX_ROWS;
        }
        OutpatientConsumableUsageDetailPageResult patientPage = this.pagePatientOnly(
            dateFrom, dateTo, locationId, feeNatureContractNo, 1, fetchSize);
        OutpatientConsumableUsageDetailPageResult deptPage =
            this.pageDeptOnly(dateFrom, dateTo, locationId, 1, fetchSize);
        List<OutpatientConsumableUsageDetailRowDto> pageRows = this.mergeAndSlice(
            patientPage.getRecords(),
            deptPage.getRecords(),
            resolvedPageNo,
            resolvedPageSize);
        OutpatientConsumableUsageDetailPageResult response =
            new OutpatientConsumableUsageDetailPageResult();
        response.setRecords(pageRows);
        // Asm：total 取两路领域 totalElements 之和（来源互斥）
        response.setTotal(patientPage.getTotal() + deptPage.getTotal());
        response.setPageNo(resolvedPageNo);
        response.setPageSize(resolvedPageSize);
        return response;
    }

    /**
     * 明细全量列表（导出 DETAIL Tab；受 EXPORT_MAX_ROWS 约束）。
     *
     * @param dateFrom   日起
     * @param dateTo     日止
     * @param sourceCode 来源筛
     * @param locationId          可选库位
     * @param feeNatureContractNo 费用性质 bus_no
     * @return 明细行
     */
    public List<OutpatientConsumableUsageDetailRowDto> listDetailForExport(
        String dateFrom,
        String dateTo,
        String sourceCode,
        Long locationId,
        String feeNatureContractNo) {
        String source = this.resolveSourceCode(sourceCode);
        if (ConsumableUsageReportConstants.SOURCE_CODE_PATIENT_DISPENSE.equals(source)) {
            return this.listPatientDetails(
                dateFrom, dateTo, locationId, feeNatureContractNo, ConsumableUsageReportConstants.EXPORT_MAX_ROWS);
        }
        if (ConsumableUsageReportConstants.SOURCE_CODE_DEPT_ISSUE.equals(source)) {
            return this.listDeptDetails(dateFrom, dateTo, locationId, ConsumableUsageReportConstants.EXPORT_MAX_ROWS);
        }
        return this.listMergedDetailLines(
            dateFrom, dateTo, locationId, feeNatureContractNo, ConsumableUsageReportConstants.EXPORT_MAX_ROWS);
    }

    /**
     * 汇总金额专用明细列表：与导出同来源分支，但仅补 organizationId + 进售价，禁止展示 enrich。
     *
     * @param dateFrom            日起
     * @param dateTo              日止
     * @param sourceCode          来源筛
     * @param locationId          可选库位
     * @param feeNatureContractNo 费用性质 bus_no
     * @return 仅含聚合/定价字段的明细行
     */
    public List<OutpatientConsumableUsageDetailRowDto> listDetailForSummaryAmounts(
        String dateFrom,
        String dateTo,
        String sourceCode,
        Long locationId,
        String feeNatureContractNo) {
        String source = this.resolveSourceCode(sourceCode);
        // Pre：单来源/并集与 listDetailForExport 同分支，改走金额-only 装载
        if (ConsumableUsageReportConstants.SOURCE_CODE_PATIENT_DISPENSE.equals(source)) {
            return this.listPatientDetailsForSummaryAmounts(
                dateFrom, dateTo, locationId, feeNatureContractNo, ConsumableUsageReportConstants.EXPORT_MAX_ROWS);
        }
        if (ConsumableUsageReportConstants.SOURCE_CODE_DEPT_ISSUE.equals(source)) {
            return this.listDeptDetailsForSummaryAmounts(
                dateFrom, dateTo, locationId, ConsumableUsageReportConstants.EXPORT_MAX_ROWS);
        }
        return this.listMergedDetailLinesForSummaryAmounts(
            dateFrom, dateTo, locationId, feeNatureContractNo, ConsumableUsageReportConstants.EXPORT_MAX_ROWS);
    }

    /**
     * 患者侧 scope 预解析并叠加费用性质 encounter 收窄。
     *
     * @param query               患者侧查询
     * @param encounterClassCode  就诊类别
     * @param dateFrom            日起
     * @param dateTo              日止
     * @param locationId          库位
     * @param summaryDimension    汇总维
     * @param feeNatureContractNo 费用性质 bus_no
     */
    private void preparePatientDispenseQueryWithFeeNature(
        ConsumableUsageReportPageQuery query,
        String encounterClassCode,
        String dateFrom,
        String dateTo,
        Long locationId,
        String summaryDimension,
        String feeNatureContractNo) {
        this.consumableUsageReportScopeLoader.preparePatientDispenseQuery(
            query, encounterClassCode, dateFrom, dateTo, locationId, summaryDimension);
        this.reportPatientSettlementContextAssembler.applyFeeNatureFilterToConsumableUsageReportPageQuery(
            query, feeNatureContractNo);
    }

    /**
     * 仅患者侧分页。
     *
     * @param dateFrom            日起
     * @param dateTo              日止
     * @param locationId          可选库位
     * @param feeNatureContractNo 费用性质 bus_no
     * @param pageNo              1 基页码
     * @param pageSize            页大小
     * @return 患者侧明细分页（含展示 enrich）
     */
    private OutpatientConsumableUsageDetailPageResult pagePatientOnly(
        String dateFrom,
        String dateTo,
        Long locationId,
        String feeNatureContractNo,
        int pageNo,
        int pageSize) {
        ConsumableUsageReportPageQuery query = new ConsumableUsageReportPageQuery();
        this.preparePatientDispenseQueryWithFeeNature(
            query, EncounterClass.AMB.getCode(), dateFrom, dateTo, locationId, null, feeNatureContractNo);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        Page<ConsumableUsageDispenseLineQueryModel> page =
            this.deviceDispenseQueryService.pageConsumableUsageDispenseLines(query);
        List<OutpatientConsumableUsageDetailRowDto> rows = this.mapPatientDetailLines(page.getContent());
        this.enrichDetailDisplay(rows);
        OutpatientConsumableUsageDetailPageResult response =
            new OutpatientConsumableUsageDetailPageResult();
        response.setRecords(rows);
        response.setTotal(page.getTotalElements());
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        return response;
    }

    /**
     * 仅科室侧分页。
     *
     * @param dateFrom   日起
     * @param dateTo     日止
     * @param locationId 可选库位
     * @param pageNo     1 基页码
     * @param pageSize   页大小
     * @return 科室侧明细分页（含展示 enrich）
     */
    private OutpatientConsumableUsageDetailPageResult pageDeptOnly(
        String dateFrom,
        String dateTo,
        Long locationId,
        int pageNo,
        int pageSize) {
        DeptIssueReportPageQuery query = new DeptIssueReportPageQuery();
        this.consumableUsageReportScopeLoader.prepareDeptIssueQuery(
            query, EncounterClass.AMB.getCode(), dateFrom, dateTo, locationId, null);
        query.setPageNo(pageNo);
        query.setPageSize(pageSize);
        Page<DeptIssueReportLineQueryModel> page =
            this.supplyRequestQueryService.pageDeptIssueReportLines(query);
        List<OutpatientConsumableUsageDetailRowDto> rows = this.mapDeptDetailLines(page.getContent());
        this.enrichDetailDisplay(rows);
        OutpatientConsumableUsageDetailPageResult response =
            new OutpatientConsumableUsageDetailPageResult();
        response.setRecords(rows);
        response.setTotal(page.getTotalElements());
        response.setPageNo(pageNo);
        response.setPageSize(pageSize);
        return response;
    }

    /**
     * 两路有界列表合并排序（导出 DETAIL / 展示 enrich）。
     *
     * @param dateFrom            日起
     * @param dateTo              日止
     * @param locationId          可选库位
     * @param feeNatureContractNo 费用性质 bus_no
     * @param maxRows             合并后上限
     * @return 合并排序后的明细行
     */
    private List<OutpatientConsumableUsageDetailRowDto> listMergedDetailLines(
        String dateFrom,
        String dateTo,
        Long locationId,
        String feeNatureContractNo,
        int maxRows) {
        List<OutpatientConsumableUsageDetailRowDto> patientRows =
            this.listPatientDetails(dateFrom, dateTo, locationId, feeNatureContractNo, maxRows);
        List<OutpatientConsumableUsageDetailRowDto> deptRows =
            this.listDeptDetails(dateFrom, dateTo, locationId, maxRows);
        List<OutpatientConsumableUsageDetailRowDto> merged = new ArrayList<>(patientRows.size() + deptRows.size());
        merged.addAll(patientRows);
        merged.addAll(deptRows);
        // Asm：业务时间倒序；空时间沉底
        merged.sort(Comparator.comparing(
            OutpatientConsumableUsageDetailRowDto::getBusinessTime,
            Comparator.nullsLast(Comparator.reverseOrder())));
        if (merged.size() > maxRows) {
            return new ArrayList<>(merged.subList(0, maxRows));
        }
        return merged;
    }

    /**
     * 两路明细按业务时间倒序合并后切当前页（SOURCE=全部）。
     *
     * @param patientRows 患者侧前缀行
     * @param deptRows    科室侧前缀行
     * @param pageNo      1 基页码
     * @param pageSize    页大小
     * @return 当前页切片；页越界为空列表
     */
    private List<OutpatientConsumableUsageDetailRowDto> mergeAndSlice(
        List<OutpatientConsumableUsageDetailRowDto> patientRows,
        List<OutpatientConsumableUsageDetailRowDto> deptRows,
        int pageNo,
        int pageSize) {
        List<OutpatientConsumableUsageDetailRowDto> merged = new ArrayList<>();
        if (patientRows != null) {
            merged.addAll(patientRows);
        }
        if (deptRows != null) {
            merged.addAll(deptRows);
        }
        // Asm：业务时间倒序；空时间沉底
        merged.sort(Comparator.comparing(
            OutpatientConsumableUsageDetailRowDto::getBusinessTime,
            Comparator.nullsLast(Comparator.reverseOrder())));
        if (pageSize <= 0) {
            return List.of();
        }
        int safePageNo = pageNo;
        if (safePageNo < 1) {
            safePageNo = 1;
        }
        int fromIndex = (safePageNo - 1) * pageSize;
        if (fromIndex >= merged.size()) {
            return List.of();
        }
        int toIndex = fromIndex + pageSize;
        if (toIndex > merged.size()) {
            toIndex = merged.size();
        }
        return new ArrayList<>(merged.subList(fromIndex, toIndex));
    }

    /**
     * 汇总金额：两路有界合并（仅金额 enrich）。
     *
     * @param dateFrom            日起
     * @param dateTo              日止
     * @param locationId          可选库位
     * @param feeNatureContractNo 费用性质 bus_no
     * @param maxRows             合并后上限
     * @return 合并排序后的金额明细
     */
    private List<OutpatientConsumableUsageDetailRowDto> listMergedDetailLinesForSummaryAmounts(
        String dateFrom,
        String dateTo,
        Long locationId,
        String feeNatureContractNo,
        int maxRows) {
        List<OutpatientConsumableUsageDetailRowDto> patientRows =
            this.listPatientDetailsForSummaryAmounts(
                dateFrom, dateTo, locationId, feeNatureContractNo, maxRows);
        List<OutpatientConsumableUsageDetailRowDto> deptRows =
            this.listDeptDetailsForSummaryAmounts(dateFrom, dateTo, locationId, maxRows);
        List<OutpatientConsumableUsageDetailRowDto> merged = new ArrayList<>(patientRows.size() + deptRows.size());
        merged.addAll(patientRows);
        merged.addAll(deptRows);
        // Asm：业务时间倒序；空时间沉底
        merged.sort(Comparator.comparing(
            OutpatientConsumableUsageDetailRowDto::getBusinessTime,
            Comparator.nullsLast(Comparator.reverseOrder())));
        if (merged.size() > maxRows) {
            return new ArrayList<>(merged.subList(0, maxRows));
        }
        return merged;
    }

    /**
     * 患者侧有界明细列表。
     *
     * @param dateFrom            日起
     * @param dateTo              日止
     * @param locationId          可选库位
     * @param feeNatureContractNo 费用性质 bus_no
     * @param maxRows             有界上限
     * @return 含展示 enrich 的患者明细
     */
    private List<OutpatientConsumableUsageDetailRowDto> listPatientDetails(
        String dateFrom,
        String dateTo,
        Long locationId,
        String feeNatureContractNo,
        int maxRows) {
        ConsumableUsageReportPageQuery query = new ConsumableUsageReportPageQuery();
        this.preparePatientDispenseQueryWithFeeNature(
            query, EncounterClass.AMB.getCode(), dateFrom, dateTo, locationId, null, feeNatureContractNo);
        query.setPageSize(maxRows);
        List<ConsumableUsageDispenseLineQueryModel> models =
            this.deviceDispenseQueryService.listConsumableUsageDispenseLines(query);
        List<OutpatientConsumableUsageDetailRowDto> rows = this.mapPatientDetailLines(models);
        this.enrichDetailDisplay(rows);
        return rows;
    }

    /**
     * 科室侧有界明细列表。
     *
     * @param dateFrom   日起
     * @param dateTo     日止
     * @param locationId 可选库位
     * @param maxRows    有界上限
     * @return 含展示 enrich 的科室明细
     */
    private List<OutpatientConsumableUsageDetailRowDto> listDeptDetails(
        String dateFrom,
        String dateTo,
        Long locationId,
        int maxRows) {
        DeptIssueReportPageQuery query = new DeptIssueReportPageQuery();
        this.consumableUsageReportScopeLoader.prepareDeptIssueQuery(
            query, EncounterClass.AMB.getCode(), dateFrom, dateTo, locationId, null);
        query.setPageSize(maxRows);
        List<DeptIssueReportLineQueryModel> models =
            this.supplyRequestQueryService.listDeptIssueReportLines(query);
        List<OutpatientConsumableUsageDetailRowDto> rows = this.mapDeptDetailLines(models);
        this.enrichDetailDisplay(rows);
        return rows;
    }

    /**
     * 汇总金额：患者侧有界明细（仅 organizationId + 进售价）。
     *
     * @param dateFrom            日起
     * @param dateTo              日止
     * @param locationId          可选库位
     * @param feeNatureContractNo 费用性质 bus_no
     * @param maxRows             有界上限
     * @return 金额聚合用患者明细
     */
    private List<OutpatientConsumableUsageDetailRowDto> listPatientDetailsForSummaryAmounts(
        String dateFrom,
        String dateTo,
        Long locationId,
        String feeNatureContractNo,
        int maxRows) {
        ConsumableUsageReportPageQuery query = new ConsumableUsageReportPageQuery();
        this.preparePatientDispenseQueryWithFeeNature(
            query, EncounterClass.AMB.getCode(), dateFrom, dateTo, locationId, null, feeNatureContractNo);
        query.setPageSize(maxRows);
        List<ConsumableUsageDispenseLineQueryModel> models =
            this.deviceDispenseQueryService.listConsumableUsageDispenseLines(query);
        List<OutpatientConsumableUsageDetailRowDto> rows = this.mapPatientDetailLines(models);
        this.enrichDetailAmountsOnly(rows);
        return rows;
    }

    /**
     * 汇总金额：科室侧有界明细（仅 organizationId + 进售价）。
     *
     * @param dateFrom   日起
     * @param dateTo     日止
     * @param locationId 可选库位
     * @param maxRows    有界上限
     * @return 金额聚合用科室明细
     */
    private List<OutpatientConsumableUsageDetailRowDto> listDeptDetailsForSummaryAmounts(
        String dateFrom,
        String dateTo,
        Long locationId,
        int maxRows) {
        DeptIssueReportPageQuery query = new DeptIssueReportPageQuery();
        this.consumableUsageReportScopeLoader.prepareDeptIssueQuery(
            query, EncounterClass.AMB.getCode(), dateFrom, dateTo, locationId, null);
        query.setPageSize(maxRows);
        List<DeptIssueReportLineQueryModel> models =
            this.supplyRequestQueryService.listDeptIssueReportLines(query);
        List<OutpatientConsumableUsageDetailRowDto> rows = this.mapDeptDetailLines(models);
        this.enrichDetailAmountsOnly(rows);
        return rows;
    }

    /**
     * 患者侧明细映射；退药数量取负。
     *
     * @param models 领域发放行
     * @return 映射后的报表明细（未展示 enrich）
     */
    private List<OutpatientConsumableUsageDetailRowDto> mapPatientDetailLines(
        List<ConsumableUsageDispenseLineQueryModel> models) {
        List<OutpatientConsumableUsageDetailRowDto> rows = new ArrayList<>();
        if (models == null) {
            return rows;
        }
        for (ConsumableUsageDispenseLineQueryModel model : models) {
            if (model == null) {
                continue;
            }
            OutpatientConsumableUsageDetailRowDto row = new OutpatientConsumableUsageDetailRowDto();
            row.setSourceCode(ConsumableUsageReportConstants.SOURCE_CODE_PATIENT_DISPENSE);
            row.setSourceCode_dictText("患者使用");
            row.setDispenseId(model.getDispenseId());
            row.setDeviceReqId(model.getDeviceReqId());
            row.setDeviceDefId(model.getDeviceDefId());
            row.setEncounterId(model.getEncounterId());
            row.setPatientId(model.getPatientId());
            row.setLocationId(model.getLocationId());
            row.setStatusOrTypeEnum(model.getStatusEnum());
            row.setUnitCode(model.getUnitCode());
            row.setLotNumber(model.getLotNumber());
            row.setBusinessTime(model.getDispenseTime());
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
    private List<OutpatientConsumableUsageDetailRowDto> mapDeptDetailLines(
        List<DeptIssueReportLineQueryModel> models) {
        List<OutpatientConsumableUsageDetailRowDto> rows = new ArrayList<>();
        if (models == null) {
            return rows;
        }
        for (DeptIssueReportLineQueryModel model : models) {
            if (model == null) {
                continue;
            }
            OutpatientConsumableUsageDetailRowDto row = new OutpatientConsumableUsageDetailRowDto();
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
    private void enrichDetailAmountsOnly(List<OutpatientConsumableUsageDetailRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> locationIdsMissingOrg = new LinkedHashSet<>();
        for (OutpatientConsumableUsageDetailRowDto row : rows) {
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
        List<OutpatientConsumableUsageDetailRowDto> patientAmountLines = new ArrayList<>();
        for (OutpatientConsumableUsageDetailRowDto row : rows) {
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
        // Asm：患者侧金额经 ChargeItem；科室侧已在映射时写入
        this.consumableUsageReportAmountEnricher.enrichPatientDispenseAmounts(
            new ArrayList<>(patientAmountLines));
        // 退药金额随数量符号：已 enrich 的正金额在退药行取负
        for (OutpatientConsumableUsageDetailRowDto row : patientAmountLines) {
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
    private void enrichDetailDisplay(List<OutpatientConsumableUsageDetailRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> deviceDefIds = new LinkedHashSet<>();
        LinkedHashSet<Long> patientIds = new LinkedHashSet<>();
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> locationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> prefilledOrgIds = new LinkedHashSet<>();
        for (OutpatientConsumableUsageDetailRowDto row : rows) {
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
        Map<String, String> unitLabelMap =
            this.terminologyOptionLoader.buildLabelMap(TerminologyCodeSystemNameConstants.UNIT_CODE);
        List<OutpatientConsumableUsageDetailRowDto> patientAmountLines = new ArrayList<>();
        for (OutpatientConsumableUsageDetailRowDto row : rows) {
            DeviceDefinition device = deviceById.get(row.getDeviceDefId());
            if (device != null) {
                row.setDeviceName(device.getName());
                row.setSpecText(device.getModelNumber());
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
            if (row.getUnitCode() != null) {
                row.setUnitCode_dictText(unitLabelMap.get(row.getUnitCode()));
            }
            row.setStatusOrTypeEnum_enumText(this.resolveStatusOrTypeText(row.getStatusOrTypeEnum()));
            if (ConsumableUsageReportConstants.SOURCE_CODE_PATIENT_DISPENSE.equals(row.getSourceCode())) {
                patientAmountLines.add(row);
            }
        }
        // Asm：患者侧金额经 ChargeItem；科室侧已在映射时写入
        this.consumableUsageReportAmountEnricher.enrichPatientDispenseAmounts(
            new ArrayList<>(patientAmountLines));
        // 退药金额随数量符号：已 enrich 的正金额在退药行取负
        for (OutpatientConsumableUsageDetailRowDto row : patientAmountLines) {
            if (row.getQuantity() != null && row.getQuantity().signum() < 0) {
                this.negatePositiveAmountIfPresent(row.getAmount(), row::setAmount);
                this.negatePositiveAmountIfPresent(row.getSaleAmount(), row::setSaleAmount);
                this.negatePositiveAmountIfPresent(row.getPurchaseAmount(), row::setPurchaseAmount);
            }
        }
        this.enrichPatientSettlementContext(rows);
    }

    /**
     * 患者侧明细行回填费用性质与 medType（科室领用无 encounter 留空）。
     *
     * @param rows 已装配明细行
     */
    private void enrichPatientSettlementContext(List<OutpatientConsumableUsageDetailRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        for (OutpatientConsumableUsageDetailRowDto row : rows) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        if (encounterIds.isEmpty()) {
            return;
        }
        Map<Long, PatientSettlementContextRow> contextByEncounterId =
            this.reportPatientSettlementContextAssembler.mapContextsByEncounterIds(encounterIds);
        for (OutpatientConsumableUsageDetailRowDto row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            PatientSettlementContextRow context = contextByEncounterId.get(row.getEncounterId());
            if (context == null) {
                continue;
            }
            row.setFeeNatureContractNo(context.getFeeNatureContractNo());
            row.setFeeNatureDisplay(context.getFeeNatureDisplay());
            row.setMedType(context.getMedType());
            row.setMedTypeDisplay(context.getMedTypeDisplay());
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
    private List<OutpatientConsumableUsageSummaryRowDto> mapPatientSummaries(
        List<ConsumableUsageDispenseSummaryRow> summaries,
        String dimension) {
        List<OutpatientConsumableUsageSummaryRowDto> rows = new ArrayList<>();
        if (summaries == null) {
            return rows;
        }
        for (ConsumableUsageDispenseSummaryRow summary : summaries) {
            if (summary == null) {
                continue;
            }
            OutpatientConsumableUsageSummaryRowDto row = new OutpatientConsumableUsageSummaryRowDto();
            row.setDimensionKey(summary.getDimensionKey());
            row.setSummaryDimension(dimension);
            row.setPatientId(summary.getPatientId());
            row.setOrganizationId(summary.getOrganizationId());
            row.setLocationId(summary.getLocationId());
            row.setDeviceDefId(summary.getDeviceDefId());
            row.setNetQuantity(summary.getNetQuantity());
            row.setLineCount(summary.getLineCount());
            row.setNetAmount(BigDecimal.ZERO);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 科室侧汇总映射。
     */
    private List<OutpatientConsumableUsageSummaryRowDto> mapDeptSummaries(
        List<DeptIssueReportSummaryRow> summaries,
        String dimension) {
        List<OutpatientConsumableUsageSummaryRowDto> rows = new ArrayList<>();
        if (summaries == null) {
            return rows;
        }
        for (DeptIssueReportSummaryRow summary : summaries) {
            if (summary == null) {
                continue;
            }
            OutpatientConsumableUsageSummaryRowDto row = new OutpatientConsumableUsageSummaryRowDto();
            row.setDimensionKey(summary.getDimensionKey());
            row.setSummaryDimension(dimension);
            row.setOrganizationId(summary.getOrganizationId());
            row.setLocationId(summary.getPurposeLocationId());
            row.setDeviceDefId(summary.getItemId());
            row.setNetQuantity(summary.getNetQuantity());
            row.setLineCount(summary.getLineCount());
            row.setNetAmount(BigDecimal.ZERO);
            rows.add(row);
        }
        return rows;
    }

    /**
     * 按 dimensionKey 合并两路汇总净量。
     */
    private List<OutpatientConsumableUsageSummaryRowDto> mergeSummaryRows(
        List<OutpatientConsumableUsageSummaryRowDto> left,
        List<OutpatientConsumableUsageSummaryRowDto> right) {
        Map<String, OutpatientConsumableUsageSummaryRowDto> bucket = new LinkedHashMap<>();
        this.putSummaryBuckets(bucket, left);
        this.putSummaryBuckets(bucket, right);
        return new ArrayList<>(bucket.values());
    }

    /**
     * 写入汇总分桶。
     */
    private void putSummaryBuckets(
        Map<String, OutpatientConsumableUsageSummaryRowDto> bucket,
        List<OutpatientConsumableUsageSummaryRowDto> rows) {
        if (rows == null) {
            return;
        }
        for (OutpatientConsumableUsageSummaryRowDto row : rows) {
            if (row == null || row.getDimensionKey() == null) {
                continue;
            }
            OutpatientConsumableUsageSummaryRowDto existing = bucket.get(row.getDimensionKey());
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
            long leftCount = 0L;
            if (existing.getLineCount() != null) {
                leftCount = existing.getLineCount();
            }
            long rightCount = 0L;
            if (row.getLineCount() != null) {
                rightCount = row.getLineCount();
            }
            existing.setLineCount(leftCount + rightCount);
        }
    }

    /**
     * 回填汇总展示名。
     */
    private void enrichSummaryDisplay(List<OutpatientConsumableUsageSummaryRowDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> patientIds = new LinkedHashSet<>();
        LinkedHashSet<Long> organizationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> locationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> deviceDefIds = new LinkedHashSet<>();
        for (OutpatientConsumableUsageSummaryRowDto row : rows) {
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
        for (OutpatientConsumableUsageSummaryRowDto row : rows) {
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
     * 汇总页脚：净数量加总（不做结算刻度）。
     *
     * @param rows 汇总行
     * @return 净数量合计；空表为零
     */
    private BigDecimal sumNetQuantity(List<OutpatientConsumableUsageSummaryRowDto> rows) {
        BigDecimal total = BigDecimal.ZERO;
        // Asm：跳过空行与空数量，避免 NPE
        for (OutpatientConsumableUsageSummaryRowDto row : rows) {
            if (row == null || row.getNetQuantity() == null) {
                continue;
            }
            total = total.add(row.getNetQuantity());
        }
        return total;
    }

    /**
     * 汇总页脚：净金额经金额 enricher 结算刻度加总（BR-MON-001）。
     *
     * @param rows 汇总行
     * @return 结算刻度合计
     */
    private BigDecimal sumNetAmount(List<OutpatientConsumableUsageSummaryRowDto> rows) {
        List<BigDecimal> amounts = new ArrayList<>();
        for (OutpatientConsumableUsageSummaryRowDto row : rows) {
            if (row == null || row.getNetAmount() == null) {
                continue;
            }
            amounts.add(row.getNetAmount());
        }
        return this.consumableUsageReportAmountEnricher.sumThenSettle(amounts);
    }

    /**
     * 汇总维缺省回落患者（BR-RPT-OCU-003 主维之一）。
     *
     * @param summaryDimension 请求维；可空
     * @return PATIENT / DEPARTMENT 等
     */
    private String resolveSummaryDimension(String summaryDimension) {
        String trimmed = StringUtils.trimToNull(summaryDimension);
        // Gate：未传维时按患者净用量，避免空维导致领域拒查
        if (trimmed == null) {
            return ConsumableUsageReportConstants.SUMMARY_DIMENSION_PATIENT;
        }
        return trimmed;
    }

    /**
     * 来源筛缺省回落全部（两路并集）。
     *
     * @param sourceCode 请求来源；可空
     * @return ALL / PATIENT_DISPENSE / DEPT_ISSUE
     */
    private String resolveSourceCode(String sourceCode) {
        String trimmed = StringUtils.trimToNull(sourceCode);
        // Gate：未传来源时查两路，与 AS「明细=并集」一致
        if (trimmed == null) {
            return ConsumableUsageReportConstants.SOURCE_CODE_ALL;
        }
        return trimmed;
    }

    /**
     * 是否纳入患者发放一路。
     *
     * @param source 已规范化来源码
     * @return true 表示查询 DeviceDispense
     */
    private boolean includePatient(String source) {
        return ConsumableUsageReportConstants.SOURCE_CODE_ALL.equals(source)
            || ConsumableUsageReportConstants.SOURCE_CODE_PATIENT_DISPENSE.equals(source);
    }

    /**
     * 是否纳入科室领用一路。
     *
     * @param source 已规范化来源码
     * @return true 表示查询 Supply DEPT_ISSUE
     */
    private boolean includeDept(String source) {
        return ConsumableUsageReportConstants.SOURCE_CODE_ALL.equals(source)
            || ConsumableUsageReportConstants.SOURCE_CODE_DEPT_ISSUE.equals(source);
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
