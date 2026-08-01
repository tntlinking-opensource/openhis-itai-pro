/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.report.registrationdetail.mapper;

import com.openhis.health.application.report.registrationdetail.dto.OutpatientRegistrationDetailRowDto;
import com.openhis.health.application.report.registrationdetail.dto.OutpatientRegistrationDetailSummaryRowDto;
import com.openhis.health.application.report.registrationdetail.request.ExportOutpatientRegistrationDetailReportRequest;
import com.openhis.health.application.report.registrationdetail.request.PageOutpatientRegistrationDetailReportDetailRequest;
import com.openhis.health.application.report.registrationdetail.request.SummarizeOutpatientRegistrationDetailReportRequest;
import com.openhis.health.application.report.registrationdetail.response.ExportOutpatientRegistrationDetailReportResponse;
import com.openhis.health.application.report.registrationdetail.response.LoadOutpatientRegistrationDetailReportPageInitResponse;
import com.openhis.health.application.report.registrationdetail.response.PageOutpatientRegistrationDetailReportDetailResponse;
import com.openhis.health.application.report.registrationdetail.response.SummarizeOutpatientRegistrationDetailReportResponse;
import com.openhis.health.domain.administration.encounter.queries.OutpatientRegistrationReportPageQuery;
import com.openhis.whale.core.application.dto.OptionDto;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 门诊挂号明细报表 Request/Response 与领域 Query 映射（AS-RPT-007 IF-RPT-ORG-01～04）。
 *
 * <p>禁止承载业务流程判断、医生筛与费用性质 enrich。</p>
 */
public interface OutpatientRegistrationDetailReportMapper {

    /**
     * 汇总请求 → 领域筛选 Query（不含 scope 预解析）。
     *
     * @param request 汇总请求；调用方保证非空
     * @return 领域 Query
     */
    OutpatientRegistrationReportPageQuery toSummarizeQuery(SummarizeOutpatientRegistrationDetailReportRequest request);

    /**
     * 明细分页请求 → 领域分页 Query（不含 scope 预解析）。
     *
     * @param request 分页请求；调用方保证非空
     * @return 领域 Query
     */
    OutpatientRegistrationReportPageQuery toDetailPageQuery(
        PageOutpatientRegistrationDetailReportDetailRequest request);

    /**
     * 导出请求 → 领域 Query（明细/汇总共用筛选字段）。
     *
     * @param request 导出请求；调用方保证非空
     * @return 领域 Query
     */
    OutpatientRegistrationReportPageQuery toExportQuery(ExportOutpatientRegistrationDetailReportRequest request);

    /**
     * 页初始化主数据 → 响应。
     *
     * @param defaultDateFrom       默认日起
     * @param defaultDateTo         默认日止
     * @param defaultPageSize       默认页大小
     * @param defaultSummaryView    默认汇总视角
     * @param organizationOptions   科室下拉
     * @param practitionerOptions   医生下拉
     * @param serviceTypeOptions    号源下拉
     * @param summaryViewOptions    汇总视角下拉
     * @return 初始化响应
     */
    LoadOutpatientRegistrationDetailReportPageInitResponse toLoadPageInitResponse(
        String defaultDateFrom,
        String defaultDateTo,
        int defaultPageSize,
        String defaultSummaryView,
        List<OptionDto> organizationOptions,
        List<OptionDto> practitionerOptions,
        List<OptionDto> serviceTypeOptions,
        List<OptionDto> summaryViewOptions);

    /**
     * 汇总行 + 合计 → 汇总响应。
     *
     * @param rows                       展示行
     * @param totalNetRegistrationCount  净笔数合计
     * @return 汇总响应
     */
    SummarizeOutpatientRegistrationDetailReportResponse toSummarizeResponse(
        List<OutpatientRegistrationDetailSummaryRowDto> rows,
        long totalNetRegistrationCount);

    /**
     * 明细展示行 + Spring Page → 分页响应。
     *
     * @param rows 当前页展示行
     * @param page 领域分页壳
     * @return 分页响应
     */
    PageOutpatientRegistrationDetailReportDetailResponse toDetailPageResponse(
        List<OutpatientRegistrationDetailRowDto> rows,
        Page<?> page);

    /**
     * 导出二进制 → 导出响应。
     *
     * @param fileName  文件名
     * @param body      xlsx 字节
     * @param rowCount  行数
     * @param truncated 是否截断
     * @param contentType MIME
     * @return 导出响应
     */
    ExportOutpatientRegistrationDetailReportResponse toExportResponse(
        String fileName,
        byte[] body,
        int rowCount,
        boolean truncated,
        String contentType);
}

