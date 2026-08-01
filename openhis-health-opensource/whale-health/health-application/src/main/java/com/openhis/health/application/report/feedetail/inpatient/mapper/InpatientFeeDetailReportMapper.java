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
import com.openhis.health.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.openhis.whale.core.application.dto.OptionDto;

import java.util.List;

/**
 * 住院费用明细报表 Request/Response 映射（AS-RPT-009）。
 */
public interface InpatientFeeDetailReportMapper {

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
     * @param feeNatureOptions              费用性质（bus_no）
     * @return 初始化响应
     */
    LoadInpatientFeeDetailReportPageInitResponse toLoadPageInitResponse(
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
        List<OptionDto> feeNatureOptions);

    /**
     * 汇总请求 → 领域 Query（强制 tradeDirection=ALL）。
     *
     * @param request 汇总请求
     * @return 领域查询；request 为 null 时返回 null
     */
    OutpatientFeeDetailPageQuery toSummarizeQuery(SummarizeInpatientFeeDetailReportRequest request);

    /**
     * 明细分页请求 → 领域 Query。
     *
     * @param request 分页请求
     * @return 领域查询；request 为 null 时返回 null
     */
    OutpatientFeeDetailPageQuery toPageQuery(PageInpatientFeeDetailReportRequest request);

    /**
     * 导出请求 → 领域 Query。
     *
     * @param request 导出请求
     * @return 领域查询；request 为 null 时返回 null
     */
    OutpatientFeeDetailPageQuery toExportQuery(ExportInpatientFeeDetailReportRequest request);

    /**
     * 汇总行 + 页脚 → 汇总响应。
     *
     * @param rows   汇总行
     * @param footer 页脚
     * @return 汇总响应
     */
    SummarizeInpatientFeeDetailReportResponse toSummarizeResponse(
        List<FeeDetailReportSummaryRowDto> rows,
        FeeDetailReportSummaryFooterDto footer);

    /**
     * 就诊块 + summary → 分页响应。
     *
     * @param blocks     当前页就诊块
     * @param totalCount 命中总数
     * @param summary    全量合计
     * @param request    原分页请求
     * @return 分页响应
     */
    PageInpatientFeeDetailReportResponse toPageResponse(
        List<OutpatientFeeDetailEncounterBlockDto> blocks,
        long totalCount,
        OutpatientFeeDetailAmountSummaryDto summary,
        PageInpatientFeeDetailReportRequest request);

    /**
     * 明细导出 xlsx → 导出响应。
     *
     * @param body      xlsx 字节
     * @param rowCount  行数
     * @param truncated 是否截断
     * @return 导出响应
     */
    ExportInpatientFeeDetailReportResponse toDetailExportResponse(byte[] body, int rowCount, boolean truncated);

    /**
     * 汇总导出 xlsx → 导出响应。
     *
     * @param body      xlsx 字节
     * @param rowCount  行数
     * @param truncated 是否截断
     * @return 导出响应
     */
    ExportInpatientFeeDetailReportResponse toSummaryExportResponse(byte[] body, int rowCount, boolean truncated);
}
