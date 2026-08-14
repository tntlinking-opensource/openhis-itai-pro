/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.feedetail.inpatient.mapper;

import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailAmountSummaryDto;
import com.openhis.health.application.outpatient.financial.feedetail.dto.OutpatientFeeDetailEncounterBlockDto;
import com.openhis.health.application.report.feedetail.dto.FeeDetailReportSummaryFooterDto;
import com.openhis.health.application.report.feedetail.dto.FeeDetailReportSummaryRowDto;
import com.openhis.health.application.report.feedetail.inpatient.request.ExportInpatientFeeDetailReportRequest;
import com.openhis.health.application.report.feedetail.inpatient.request.PageInpatientFeeDetailReportRequest;
import com.openhis.health.application.report.feedetail.inpatient.request.SummarizeInpatientFeeDetailReportRequest;
import com.openhis.health.application.report.feedetail.inpatient.response.ExportInpatientFeeDetailReportResponse;
import com.openhis.health.application.report.feedetail.inpatient.response.LoadInpatientFeeDetailReportPageInitResponse;
import com.openhis.health.application.report.feedetail.inpatient.response.PageInpatientFeeDetailReportResponse;
import com.openhis.health.application.report.feedetail.inpatient.response.SummarizeInpatientFeeDetailReportResponse;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientFeeDetailReportConstants;
import com.openhis.health.domain.share.constants.OutpatientFeeDetailConstants;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * {@link InpatientFeeDetailReportMapper} 实现（AS-RPT-009）。
 */
@Component
@RequiredArgsConstructor
public class InpatientFeeDetailReportMapperImpl implements InpatientFeeDetailReportMapper {

    /**
     * 页初始化主数据 → 初始化响应。
     *
     * @param defaultDateFrom               默认查询日起
     * @param defaultDateTo                 默认查询日止
     * @param defaultPageSize               默认分页大小
     * @param tradeDirectionOptions         收退 Tab
     * @param feeCategoryOptions            费用大类
     * @param medicationSubCategoryOptions  药品细分类
     * @param activitySubCategoryOptions    诊疗细分类
     * @param deviceSubCategoryOptions      耗材细分类
     * @param registrationDepartmentOptions 住院科室
     * @param cashierOptions                收费员
     * @param presentationModeOptions       汇总/明细 Tab
     * @param defaultActiveTab              默认激活 Tab
     * @param feeNatureOptions              费用性质
     * @param settlementStatusOptions       结算状态筛
     * @param summaryDimensionOptions       汇总维度下拉
     * @return 初始化响应
     */
    @Override
    public LoadInpatientFeeDetailReportPageInitResponse toLoadPageInitResponse(
        String defaultDateFrom,
        String defaultDateTo,
        int defaultPageSize,
        List<OptionDto> tradeDirectionOptions,
        List<OptionDto> feeCategoryOptions,
        List<OptionDto> medicationSubCategoryOptions,
        List<OptionDto> activitySubCategoryOptions,
        List<OptionDto> deviceSubCategoryOptions,
        List<OptionDto> registrationDepartmentOptions,
        List<OptionDto> cashierOptions,
        List<OptionDto> presentationModeOptions,
        String defaultActiveTab,
        List<OptionDto> feeNatureOptions,
        List<OptionDto> settlementStatusOptions,
        List<OptionDto> summaryDimensionOptions) {
        LoadInpatientFeeDetailReportPageInitResponse response = new LoadInpatientFeeDetailReportPageInitResponse();
        response.setDefaultDateFrom(defaultDateFrom);
        response.setDefaultDateTo(defaultDateTo);
        response.setDefaultPageSize(defaultPageSize);
        response.setTradeDirectionOptions(tradeDirectionOptions);
        response.setFeeCategoryOptions(feeCategoryOptions);
        response.setMedicationSubCategoryOptions(medicationSubCategoryOptions);
        response.setActivitySubCategoryOptions(activitySubCategoryOptions);
        response.setDeviceSubCategoryOptions(deviceSubCategoryOptions);
        response.setRegistrationDepartmentOptions(registrationDepartmentOptions);
        response.setCashierOptions(cashierOptions);
        response.setPresentationModeOptions(presentationModeOptions);
        response.setDefaultActiveTab(defaultActiveTab);
        response.setFeeNatureOptions(feeNatureOptions);
        response.setSettlementStatusOptions(settlementStatusOptions);
        response.setSummaryDimensionOptions(summaryDimensionOptions);
        return response;
    }

    /**
     * 汇总请求 → 领域 Query（强制 tradeDirection=ALL）。
     *
     * @param request 汇总请求
     * @return 领域查询；request 为 null 时返回 null
     */
    @Override
    public OutpatientFeeDetailPageQuery toSummarizeQuery(SummarizeInpatientFeeDetailReportRequest request) {
        if (request == null) {
            return null;
        }
        OutpatientFeeDetailPageQuery query = new OutpatientFeeDetailPageQuery();
        query.setPageNo(1);
        query.setPageSize(InpatientFeeDetailReportConstants.DEFAULT_PAGE_SIZE);
        this.applyFilterFields(query, request.getDateFrom(), request.getDateTo(),
            request.getRegistrationDepartmentId(), request.getRequestingOrgId(),
            request.getFeeCategoryCodes(), request.getSubCategoryCodes(),
            request.getPatientKeyword(), request.getProductNameKeyword(), request.getProductCodeKeyword(),
            request.getCashierId(), OutpatientFeeDetailConstants.TRADE_DIRECTION_ALL,
            request.getFeeNatureContractNo(), request.getSettlementStatus());
        return query;
    }

    /**
     * 明细分页请求 → 领域 Query。
     *
     * @param request 分页请求
     * @return 领域查询；request 为 null 时返回 null
     */
    @Override
    public OutpatientFeeDetailPageQuery toPageQuery(PageInpatientFeeDetailReportRequest request) {
        if (request == null) {
            return null;
        }
        OutpatientFeeDetailPageQuery query = new OutpatientFeeDetailPageQuery();
        PageQueryBinder.bind(request, query);
        this.applyFilterFields(query, request.getDateFrom(), request.getDateTo(),
            request.getRegistrationDepartmentId(), request.getRequestingOrgId(),
            request.getFeeCategoryCodes(), request.getSubCategoryCodes(),
            request.getPatientKeyword(), request.getProductNameKeyword(), request.getProductCodeKeyword(),
            request.getCashierId(), request.getTradeDirection(), request.getFeeNatureContractNo(),
            request.getSettlementStatus());
        return query;
    }

    /**
     * 导出请求 → 领域 Query。
     *
     * @param request 导出请求
     * @return 领域查询；request 为 null 时返回 null
     */
    @Override
    public OutpatientFeeDetailPageQuery toExportQuery(ExportInpatientFeeDetailReportRequest request) {
        if (request == null) {
            return null;
        }
        OutpatientFeeDetailPageQuery query = new OutpatientFeeDetailPageQuery();
        query.setPageNo(1);
        query.setPageSize(InpatientFeeDetailReportConstants.EXPORT_MAX_ROWS);
        this.applyFilterFields(query, request.getDateFrom(), request.getDateTo(),
            request.getRegistrationDepartmentId(), request.getRequestingOrgId(),
            request.getFeeCategoryCodes(), request.getSubCategoryCodes(),
            request.getPatientKeyword(), request.getProductNameKeyword(), request.getProductCodeKeyword(),
            request.getCashierId(), request.getTradeDirection(), request.getFeeNatureContractNo(),
            request.getSettlementStatus());
        return query;
    }

    /**
     * 汇总行 + 页脚 → 汇总响应。
     *
     * @param rows   汇总行
     * @param footer 页脚
     * @return 汇总响应
     */
    @Override
    public SummarizeInpatientFeeDetailReportResponse toSummarizeResponse(
        List<FeeDetailReportSummaryRowDto> rows,
        FeeDetailReportSummaryFooterDto footer) {
        SummarizeInpatientFeeDetailReportResponse response = new SummarizeInpatientFeeDetailReportResponse();
        if (rows == null) {
            response.setRows(List.of());
        } else {
            response.setRows(rows);
        }
        response.setFooter(footer);
        return response;
    }

    /**
     * 就诊块 + summary → 分页响应。
     *
     * @param blocks     当前页就诊块
     * @param totalCount 命中总数
     * @param summary    全量合计
     * @param request    原分页请求
     * @return 分页响应
     */
    @Override
    public PageInpatientFeeDetailReportResponse toPageResponse(
        List<OutpatientFeeDetailEncounterBlockDto> blocks,
        long totalCount,
        OutpatientFeeDetailAmountSummaryDto summary,
        PageInpatientFeeDetailReportRequest request) {
        PageInpatientFeeDetailReportResponse response = new PageInpatientFeeDetailReportResponse();
        if (blocks == null) {
            response.setContent(List.of());
        } else {
            response.setContent(blocks);
        }
        response.setSummary(summary);
        response.setTotalCount(totalCount);
        if (request != null) {
            response.setPageNo(this.resolvePageNo(request.getPageNo()));
            response.setPageSize(this.resolvePageSize(request.getPageSize()));
        } else {
            response.setPageNo(1);
            response.setPageSize(InpatientFeeDetailReportConstants.DEFAULT_PAGE_SIZE);
        }
        return response;
    }

    /**
     * 明细导出 xlsx → 导出响应。
     *
     * @param body      xlsx 字节
     * @param rowCount  行数
     * @param truncated 是否截断
     * @return 导出响应
     */
    @Override
    public ExportInpatientFeeDetailReportResponse toDetailExportResponse(
        byte[] body,
        int rowCount,
        boolean truncated) {
        ExportInpatientFeeDetailReportResponse response = new ExportInpatientFeeDetailReportResponse();
        String timestamp = LocalDateTime.now().format(GeneralSharedConstants.EXPORT_FILE_TIMESTAMP_FORMATTER);
        response.setFileName("住院费用明细_" + timestamp + ".xlsx");
        response.setContentType(InpatientFeeDetailReportConstants.EXPORT_CONTENT_TYPE);
        response.setBody(body);
        response.setRowCount(rowCount);
        response.setTruncated(truncated);
        return response;
    }

    /**
     * 汇总导出 xlsx → 导出响应。
     *
     * @param body      xlsx 字节
     * @param rowCount  行数
     * @param truncated 是否截断
     * @return 导出响应
     */
    @Override
    public ExportInpatientFeeDetailReportResponse toSummaryExportResponse(
        byte[] body,
        int rowCount,
        boolean truncated) {
        ExportInpatientFeeDetailReportResponse response = new ExportInpatientFeeDetailReportResponse();
        String timestamp = LocalDateTime.now().format(GeneralSharedConstants.EXPORT_FILE_TIMESTAMP_FORMATTER);
        response.setFileName("住院费用类型汇总_" + timestamp + ".xlsx");
        response.setContentType(InpatientFeeDetailReportConstants.EXPORT_CONTENT_TYPE);
        response.setBody(body);
        response.setRowCount(rowCount);
        response.setTruncated(truncated);
        return response;
    }

    /**
     * 将 HTTP 筛选字段写入领域 Query。
     *
     * @param query                    目标领域 Query
     * @param dateFrom                 结算日起
     * @param dateTo                   结算日止
     * @param registrationDepartmentId 住院科室（遗留）
     * @param requestingOrgId          开立科室（账单 requesting_org_id）
     * @param feeCategoryCodes         费用大类
     * @param subCategoryCodes         细分类
     * @param patientKeyword           患者关键字
     * @param productNameKeyword       项目名称
     * @param productCodeKeyword       项目编码
     * @param cashierId                收费人
     * @param tradeDirection           收退 Tab
     * @param feeNatureContractNo      费用性质合同 bus_no
     * @param settlementStatus         结算状态筛
     */
    private void applyFilterFields(
        OutpatientFeeDetailPageQuery query,
        String dateFrom,
        String dateTo,
        Long registrationDepartmentId,
        Long requestingOrgId,
        List<String> feeCategoryCodes,
        List<String> subCategoryCodes,
        String patientKeyword,
        String productNameKeyword,
        String productCodeKeyword,
        Long cashierId,
        String tradeDirection,
        String feeNatureContractNo,
        String settlementStatus) {
        if (StringUtils.isNotEmpty(dateFrom)) {
            query.setBillDateFrom(LocalDate.parse(dateFrom.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
        }
        if (StringUtils.isNotEmpty(dateTo)) {
            query.setBillDateTo(LocalDate.parse(dateTo.trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
        }
        query.setRegistrationDepartmentId(registrationDepartmentId);
        query.setRequestingOrgId(requestingOrgId);
        query.setFeeCategoryCodes(feeCategoryCodes);
        query.setSubCategoryCodes(subCategoryCodes);
        query.setPatientKeyword(trimToNull(patientKeyword));
        query.setProductNameKeyword(trimToNull(productNameKeyword));
        query.setProductCodeKeyword(trimToNull(productCodeKeyword));
        query.setCashierId(cashierId);
        String resolvedTradeDirection = trimToNull(tradeDirection);
        if (resolvedTradeDirection == null) {
            query.setTradeDirection(OutpatientFeeDetailConstants.TRADE_DIRECTION_ALL);
        } else {
            query.setTradeDirection(resolvedTradeDirection);
        }
        query.setFeeNatureContractNo(trimToNull(feeNatureContractNo));
        query.setAllowedChargeItemStatusCodes(this.resolveAllowedChargeItemStatusCodes(settlementStatus));
    }

    /**
     * 结算状态筛 → ChargeItem 状态白名单。
     *
     * @param settlementStatus ALL / SETTLED / PENDING_SETTLEMENT；空默认 ALL
     * @return 状态码列表
     */
    private List<String> resolveAllowedChargeItemStatusCodes(String settlementStatus) {
        String normalized = trimToNull(settlementStatus);
        if (normalized == null
            || InpatientFeeDetailReportConstants.SETTLEMENT_STATUS_ALL.equals(normalized)) {
            return List.of(
                InpatientFeeDetailReportConstants.SETTLEMENT_STATUS_SETTLED,
                InpatientFeeDetailReportConstants.SETTLEMENT_STATUS_PENDING);
        }
        if (InpatientFeeDetailReportConstants.SETTLEMENT_STATUS_SETTLED.equals(normalized)) {
            return List.of(InpatientFeeDetailReportConstants.SETTLEMENT_STATUS_SETTLED);
        }
        if (InpatientFeeDetailReportConstants.SETTLEMENT_STATUS_PENDING.equals(normalized)) {
            return List.of(InpatientFeeDetailReportConstants.SETTLEMENT_STATUS_PENDING);
        }
        // 非法值回落全部，避免空窗
        return List.of(
            InpatientFeeDetailReportConstants.SETTLEMENT_STATUS_SETTLED,
            InpatientFeeDetailReportConstants.SETTLEMENT_STATUS_PENDING);
    }

    /**
     * 解析页码。
     *
     * @param pageNo 请求页码
     * @return 有效页码
     */
    private int resolvePageNo(Integer pageNo) {
        if (pageNo == null || pageNo < 1) {
            return 1;
        }
        return pageNo;
    }

    /**
     * 解析页大小。
     *
     * @param pageSize 请求页大小
     * @return 有效页大小
     */
    private int resolvePageSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return InpatientFeeDetailReportConstants.DEFAULT_PAGE_SIZE;
        }
        return pageSize;
    }
}
