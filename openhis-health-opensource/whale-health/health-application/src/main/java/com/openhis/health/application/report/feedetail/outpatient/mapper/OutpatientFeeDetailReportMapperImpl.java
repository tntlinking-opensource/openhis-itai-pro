/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.feedetail.outpatient.mapper;

import com.openhis.health.application.outpatient.financial.feedetail.request.ExportOutpatientFeeDetailRequest;
import com.openhis.health.application.outpatient.financial.feedetail.request.PageOutpatientFeeDetailRequest;
import com.openhis.health.application.outpatient.financial.feedetail.response.ExportOutpatientFeeDetailResponse;
import com.openhis.health.application.outpatient.financial.feedetail.response.LoadOutpatientFeeDetailPageInitResponse;
import com.openhis.health.application.outpatient.financial.feedetail.response.PageOutpatientFeeDetailResponse;
import com.openhis.health.application.report.feedetail.dto.FeeDetailReportSummaryFooterDto;
import com.openhis.health.application.report.feedetail.dto.FeeDetailReportSummaryRowDto;
import com.openhis.health.application.report.feedetail.outpatient.request.ExportOutpatientFeeDetailReportRequest;
import com.openhis.health.application.report.feedetail.outpatient.request.PageOutpatientFeeDetailReportRequest;
import com.openhis.health.application.report.feedetail.outpatient.request.SummarizeOutpatientFeeDetailReportRequest;
import com.openhis.health.application.report.feedetail.outpatient.response.ExportOutpatientFeeDetailReportResponse;
import com.openhis.health.application.report.feedetail.outpatient.response.LoadOutpatientFeeDetailReportPageInitResponse;
import com.openhis.health.application.report.feedetail.outpatient.response.PageOutpatientFeeDetailReportResponse;
import com.openhis.health.application.report.feedetail.outpatient.response.SummarizeOutpatientFeeDetailReportResponse;
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.OutpatientFeeDetailConstants;
import com.openhis.health.domain.share.constants.OutpatientFeeDetailReportConstants;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * {@link OutpatientFeeDetailReportMapper} 实现（AS-RPT-008）。
 */
@Component
@RequiredArgsConstructor
public class OutpatientFeeDetailReportMapperImpl implements OutpatientFeeDetailReportMapper {

    /**
     * 前身初始化响应 → 报表初始化响应（补呈现模式与汇总维度）。
     *
     * @param source                  前身 init 响应
     * @param presentationModeOptions 汇总/明细 Tab
     * @param defaultActiveTab        默认激活 Tab
     * @param summaryDimensionOptions 汇总维度下拉
     * @return 报表 init 响应；source 为 null 时返回 null
     */
    @Override
    public LoadOutpatientFeeDetailReportPageInitResponse toLoadPageInitResponse(
        LoadOutpatientFeeDetailPageInitResponse source,
        List<OptionDto> presentationModeOptions,
        String defaultActiveTab,
        List<OptionDto> summaryDimensionOptions) {
        // 前身 init 失败返回 null 时透传，避免空壳下拉
        if (source == null) {
            return null;
        }
        LoadOutpatientFeeDetailReportPageInitResponse response = new LoadOutpatientFeeDetailReportPageInitResponse();
        response.setDefaultDateFrom(source.getDefaultDateFrom());
        response.setDefaultDateTo(source.getDefaultDateTo());
        response.setDefaultPageSize(source.getDefaultPageSize());
        response.setTradeDirectionOptions(source.getTradeDirectionOptions());
        response.setFeeCategoryOptions(source.getFeeCategoryOptions());
        response.setMedicationSubCategoryOptions(source.getMedicationSubCategoryOptions());
        response.setActivitySubCategoryOptions(source.getActivitySubCategoryOptions());
        response.setDeviceSubCategoryOptions(source.getDeviceSubCategoryOptions());
        response.setRegistrationDepartmentOptions(source.getRegistrationDepartmentOptions());
        response.setCashierOptions(source.getCashierOptions());
        response.setPresentationModeOptions(presentationModeOptions);
        response.setDefaultActiveTab(defaultActiveTab);
        response.setFeeNatureOptions(source.getFeeNatureOptions());
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
    public OutpatientFeeDetailPageQuery toSummarizeQuery(SummarizeOutpatientFeeDetailReportRequest request) {
        if (request == null) {
            return null;
        }
        OutpatientFeeDetailPageQuery query = new OutpatientFeeDetailPageQuery();
        query.setPageNo(1);
        query.setPageSize(OutpatientFeeDetailReportConstants.DEFAULT_PAGE_SIZE);
        if (StringUtils.isNotEmpty(request.getDateFrom())) {
            query.setBillDateFrom(LocalDate.parse(request.getDateFrom().trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
        }
        if (StringUtils.isNotEmpty(request.getDateTo())) {
            query.setBillDateTo(LocalDate.parse(request.getDateTo().trim(), GeneralSharedConstants.DATE_DISPLAY_FORMATTER));
        }
        query.setRegistrationDepartmentId(request.getRegistrationDepartmentId());
        query.setRequestingOrgId(request.getRequestingOrgId());
        query.setFeeCategoryCodes(request.getFeeCategoryCodes());
        query.setSubCategoryCodes(request.getSubCategoryCodes());
        query.setPatientKeyword(trimToNull(request.getPatientKeyword()));
        query.setProductNameKeyword(trimToNull(request.getProductNameKeyword()));
        query.setProductCodeKeyword(trimToNull(request.getProductCodeKeyword()));
        query.setCashierId(request.getCashierId());
        // BR-RPT-OFD-003：汇总强制净视角 ALL
        query.setTradeDirection(OutpatientFeeDetailConstants.TRADE_DIRECTION_ALL);
        query.setFeeNatureContractNo(trimToNull(request.getFeeNatureContractNo()));
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
    public SummarizeOutpatientFeeDetailReportResponse toSummarizeResponse(
        List<FeeDetailReportSummaryRowDto> rows,
        FeeDetailReportSummaryFooterDto footer) {
        SummarizeOutpatientFeeDetailReportResponse response = new SummarizeOutpatientFeeDetailReportResponse();
        if (rows == null) {
            response.setRows(List.of());
        } else {
            response.setRows(rows);
        }
        response.setFooter(footer);
        return response;
    }

    /**
     * 报表明细分页请求 → 前身明细分页请求。
     *
     * @param request 报表分页请求
     * @return 前身分页请求；request 为 null 时返回 null
     */
    @Override
    public PageOutpatientFeeDetailRequest toPageOutpatientFeeDetailRequest(
        PageOutpatientFeeDetailReportRequest request) {
        if (request == null) {
            return null;
        }
        PageOutpatientFeeDetailRequest target = new PageOutpatientFeeDetailRequest();
        target.setPageNo(request.getPageNo());
        target.setPageSize(request.getPageSize());
        target.setDateFrom(request.getDateFrom());
        target.setDateTo(request.getDateTo());
        target.setRegistrationDepartmentId(request.getRegistrationDepartmentId());
        target.setRequestingOrgId(request.getRequestingOrgId());
        target.setFeeCategoryCodes(request.getFeeCategoryCodes());
        target.setSubCategoryCodes(request.getSubCategoryCodes());
        target.setPatientKeyword(request.getPatientKeyword());
        target.setProductNameKeyword(request.getProductNameKeyword());
        target.setProductCodeKeyword(request.getProductCodeKeyword());
        target.setCashierId(request.getCashierId());
        target.setTradeDirection(request.getTradeDirection());
        target.setFeeNatureContractNo(request.getFeeNatureContractNo());
        return target;
    }

    /**
     * 前身明细分页响应 → 报表明细分页响应。
     *
     * @param source 前身分页响应
     * @return 报表分页响应；source 为 null 时返回 null
     */
    @Override
    public PageOutpatientFeeDetailReportResponse toPageReportResponse(PageOutpatientFeeDetailResponse source) {
        if (source == null) {
            return null;
        }
        PageOutpatientFeeDetailReportResponse response = new PageOutpatientFeeDetailReportResponse();
        response.setContent(source.getContent());
        response.setTotalCount(source.getTotalCount());
        response.setPageNo(source.getPageNo());
        response.setPageSize(source.getPageSize());
        response.setSummary(source.getSummary());
        return response;
    }

    /**
     * 报表导出请求 → 前身明细导出请求。
     *
     * @param request 报表导出请求
     * @return 前身导出请求；request 为 null 时返回 null
     */
    @Override
    public ExportOutpatientFeeDetailRequest toExportOutpatientFeeDetailRequest(
        ExportOutpatientFeeDetailReportRequest request) {
        if (request == null) {
            return null;
        }
        ExportOutpatientFeeDetailRequest target = new ExportOutpatientFeeDetailRequest();
        target.setDateFrom(request.getDateFrom());
        target.setDateTo(request.getDateTo());
        target.setRegistrationDepartmentId(request.getRegistrationDepartmentId());
        target.setRequestingOrgId(request.getRequestingOrgId());
        target.setFeeCategoryCodes(request.getFeeCategoryCodes());
        target.setSubCategoryCodes(request.getSubCategoryCodes());
        target.setPatientKeyword(request.getPatientKeyword());
        target.setProductNameKeyword(request.getProductNameKeyword());
        target.setProductCodeKeyword(request.getProductCodeKeyword());
        target.setCashierId(request.getCashierId());
        target.setTradeDirection(request.getTradeDirection());
        target.setFeeNatureContractNo(request.getFeeNatureContractNo());
        return target;
    }

    /**
     * 前身导出响应 → 报表导出响应。
     *
     * @param source 前身导出响应
     * @return 报表导出响应；source 为 null 时返回 null
     */
    @Override
    public ExportOutpatientFeeDetailReportResponse toExportReportResponse(ExportOutpatientFeeDetailResponse source) {
        if (source == null) {
            return null;
        }
        ExportOutpatientFeeDetailReportResponse response = new ExportOutpatientFeeDetailReportResponse();
        response.setFileName(source.getFileName());
        response.setContentType(source.getContentType());
        response.setBody(source.getBody());
        response.setRowCount(source.getRowCount());
        response.setTruncated(source.getTruncated());
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
    public ExportOutpatientFeeDetailReportResponse toSummaryExportResponse(
        byte[] body,
        int rowCount,
        boolean truncated) {
        ExportOutpatientFeeDetailReportResponse response = new ExportOutpatientFeeDetailReportResponse();
        String timestamp = LocalDateTime.now().format(GeneralSharedConstants.EXPORT_FILE_TIMESTAMP_FORMATTER);
        response.setFileName("门诊费用类型汇总_" + timestamp + ".xlsx");
        response.setContentType(OutpatientFeeDetailReportConstants.EXPORT_CONTENT_TYPE);
        response.setBody(body);
        response.setRowCount(rowCount);
        response.setTruncated(truncated);
        return response;
    }
}
