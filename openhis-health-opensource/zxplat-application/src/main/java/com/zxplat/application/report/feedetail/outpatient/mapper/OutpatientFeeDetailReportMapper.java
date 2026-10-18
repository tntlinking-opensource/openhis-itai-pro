/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.report.feedetail.outpatient.mapper;

import com.zxplat.application.outpatient.financial.feedetail.request.ExportOutpatientFeeDetailRequest;
import com.zxplat.application.outpatient.financial.feedetail.request.PageOutpatientFeeDetailRequest;
import com.zxplat.application.outpatient.financial.feedetail.response.ExportOutpatientFeeDetailResponse;
import com.zxplat.application.outpatient.financial.feedetail.response.LoadOutpatientFeeDetailPageInitResponse;
import com.zxplat.application.outpatient.financial.feedetail.response.PageOutpatientFeeDetailResponse;
import com.zxplat.application.report.feedetail.dto.FeeDetailReportSummaryFooterDto;
import com.zxplat.application.report.feedetail.dto.FeeDetailReportSummaryRowDto;
import com.zxplat.application.report.feedetail.outpatient.request.ExportOutpatientFeeDetailReportRequest;
import com.zxplat.application.report.feedetail.outpatient.request.PageOutpatientFeeDetailReportRequest;
import com.zxplat.application.report.feedetail.outpatient.request.SummarizeOutpatientFeeDetailReportRequest;
import com.zxplat.application.report.feedetail.outpatient.response.ExportOutpatientFeeDetailReportResponse;
import com.zxplat.application.report.feedetail.outpatient.response.LoadOutpatientFeeDetailReportPageInitResponse;
import com.zxplat.application.report.feedetail.outpatient.response.PageOutpatientFeeDetailReportResponse;
import com.zxplat.application.report.feedetail.outpatient.response.SummarizeOutpatientFeeDetailReportResponse;
import com.zxplat.domain.administration.chargeitem.query.OutpatientFeeDetailPageQuery;
import com.zxplat.framework.core.application.dto.OptionDto;

import java.util.List;

/**
 * 门诊费用明细报表 Request/Response 映射（AS-RPT-008）。
 */
public interface OutpatientFeeDetailReportMapper {

    /**
     * 前身初始化响应 → 报表初始化响应（补呈现模式与汇总维度）。
     *
     * @param source                     前身 init 响应
     * @param presentationModeOptions    汇总/明细 Tab
     * @param defaultActiveTab           默认激活 Tab
     * @param summaryDimensionOptions    汇总维度下拉
     * @return 报表 init 响应；source 为 null 时返回 null
     */
    LoadOutpatientFeeDetailReportPageInitResponse toLoadPageInitResponse(
        LoadOutpatientFeeDetailPageInitResponse source,
        List<OptionDto> presentationModeOptions,
        String defaultActiveTab,
        List<OptionDto> summaryDimensionOptions);

    /**
     * 汇总请求 → 领域 Query（强制 tradeDirection=ALL）。
     *
     * @param request 汇总请求
     * @return 领域查询；request 为 null 时返回 null
     */
    OutpatientFeeDetailPageQuery toSummarizeQuery(SummarizeOutpatientFeeDetailReportRequest request);

    /**
     * 汇总行 + 页脚 → 汇总响应。
     *
     * @param rows   汇总行
     * @param footer 页脚
     * @return 汇总响应
     */
    SummarizeOutpatientFeeDetailReportResponse toSummarizeResponse(
        List<FeeDetailReportSummaryRowDto> rows,
        FeeDetailReportSummaryFooterDto footer);

    /**
     * 报表明细分页请求 → 前身明细分页请求。
     *
     * @param request 报表分页请求
     * @return 前身分页请求；request 为 null 时返回 null
     */
    PageOutpatientFeeDetailRequest toPageOutpatientFeeDetailRequest(PageOutpatientFeeDetailReportRequest request);

    /**
     * 前身明细分页响应 → 报表明细分页响应。
     *
     * @param source 前身分页响应
     * @return 报表分页响应；source 为 null 时返回 null
     */
    PageOutpatientFeeDetailReportResponse toPageReportResponse(PageOutpatientFeeDetailResponse source);

    /**
     * 报表导出请求 → 前身明细导出请求。
     *
     * @param request 报表导出请求
     * @return 前身导出请求；request 为 null 时返回 null
     */
    ExportOutpatientFeeDetailRequest toExportOutpatientFeeDetailRequest(
        ExportOutpatientFeeDetailReportRequest request);

    /**
     * 前身导出响应 → 报表导出响应。
     *
     * @param source 前身导出响应
     * @return 报表导出响应；source 为 null 时返回 null
     */
    ExportOutpatientFeeDetailReportResponse toExportReportResponse(ExportOutpatientFeeDetailResponse source);

    /**
     * 汇总导出 xlsx → 导出响应。
     *
     * @param body      xlsx 字节
     * @param rowCount  行数
     * @param truncated 是否截断
     * @return 导出响应
     */
    ExportOutpatientFeeDetailReportResponse toSummaryExportResponse(byte[] body, int rowCount, boolean truncated);
}
