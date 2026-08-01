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
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.domain.administration.encounter.queries.OutpatientRegistrationReportPageQuery;
import com.openhis.whale.core.application.dto.OptionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 门诊挂号明细报表 Mapper 实现（AS-RPT-007）。
 *
 * <p>仅字段搬运；scope 预解析与 enrich 在 Support/Enricher。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientRegistrationDetailReportMapperImpl implements OutpatientRegistrationDetailReportMapper {

    /**
     * 汇总请求 → 领域筛选 Query（不含 scope 预解析）。
     *
     * @param request 汇总请求；调用方保证非空
     * @return 领域 Query
     */
    @Override
    public OutpatientRegistrationReportPageQuery toSummarizeQuery(
        SummarizeOutpatientRegistrationDetailReportRequest request) {
        OutpatientRegistrationReportPageQuery query = new OutpatientRegistrationReportPageQuery();
        // 空请求：返回空壳 Query，由调用方 Gate 保证非空
        if (request == null) {
            return query;
        }
        query.setOrganizationId(request.getOrganizationId());
        return query;
    }

    /**
     * 明细分页请求 → 领域分页 Query（不含 scope 预解析）。
     *
     * @param request 分页请求；调用方保证非空
     * @return 领域 Query
     */
    @Override
    public OutpatientRegistrationReportPageQuery toDetailPageQuery(
        PageOutpatientRegistrationDetailReportDetailRequest request) {
        OutpatientRegistrationReportPageQuery query = new OutpatientRegistrationReportPageQuery();
        // 空请求：返回空壳 Query，由调用方 Gate 保证非空
        if (request == null) {
            return query;
        }
        query.setOrganizationId(request.getOrganizationId());
        query.setPageNo(request.getPageNo());
        query.setPageSize(request.getPageSize());
        return query;
    }

    /**
     * 导出请求 → 领域 Query（明细/汇总共用筛选字段）。
     *
     * @param request 导出请求；调用方保证非空
     * @return 领域 Query
     */
    @Override
    public OutpatientRegistrationReportPageQuery toExportQuery(
        ExportOutpatientRegistrationDetailReportRequest request) {
        OutpatientRegistrationReportPageQuery query = new OutpatientRegistrationReportPageQuery();
        // 空请求：返回空壳 Query，由调用方 Gate 保证非空
        if (request == null) {
            return query;
        }
        query.setOrganizationId(request.getOrganizationId());
        return query;
    }

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
    @Override
    public LoadOutpatientRegistrationDetailReportPageInitResponse toLoadPageInitResponse(
        String defaultDateFrom,
        String defaultDateTo,
        int defaultPageSize,
        String defaultSummaryView,
        List<OptionDto> organizationOptions,
        List<OptionDto> practitionerOptions,
        List<OptionDto> serviceTypeOptions,
        List<OptionDto> summaryViewOptions) {
        LoadOutpatientRegistrationDetailReportPageInitResponse response =
            new LoadOutpatientRegistrationDetailReportPageInitResponse();
        response.setDefaultDateFrom(defaultDateFrom);
        response.setDefaultDateTo(defaultDateTo);
        response.setDefaultPageSize(defaultPageSize);
        response.setDefaultSummaryView(defaultSummaryView);
        response.setOrganizationOptions(organizationOptions);
        response.setPractitionerOptions(practitionerOptions);
        response.setServiceTypeOptions(serviceTypeOptions);
        response.setSummaryViewOptions(summaryViewOptions);
        return response;
    }

    /**
     * 汇总行 + 合计 → 汇总响应。
     *
     * @param rows                       展示行
     * @param totalNetRegistrationCount  净笔数合计
     * @return 汇总响应
     */
    @Override
    public SummarizeOutpatientRegistrationDetailReportResponse toSummarizeResponse(
        List<OutpatientRegistrationDetailSummaryRowDto> rows,
        long totalNetRegistrationCount) {
        SummarizeOutpatientRegistrationDetailReportResponse response =
            new SummarizeOutpatientRegistrationDetailReportResponse();
        response.setRows(rows);
        response.setTotalNetRegistrationCount(totalNetRegistrationCount);
        return response;
    }

    /**
     * 明细展示行 + Spring Page → 分页响应。
     *
     * @param rows 当前页展示行
     * @param page 领域分页壳
     * @return 分页响应
     */
    @Override
    public PageOutpatientRegistrationDetailReportDetailResponse toDetailPageResponse(
        List<OutpatientRegistrationDetailRowDto> rows,
        Page<?> page) {
        return PagedResultBuilder.build(
            new PageOutpatientRegistrationDetailReportDetailResponse(),
            rows,
            page);
    }

    /**
     * 导出二进制 → 导出响应。
     *
     * @param fileName    文件名
     * @param body        xlsx 字节
     * @param rowCount    行数
     * @param truncated   是否截断
     * @param contentType MIME
     * @return 导出响应
     */
    @Override
    public ExportOutpatientRegistrationDetailReportResponse toExportResponse(
        String fileName,
        byte[] body,
        int rowCount,
        boolean truncated,
        String contentType) {
        ExportOutpatientRegistrationDetailReportResponse response =
            new ExportOutpatientRegistrationDetailReportResponse();
        response.setFileName(fileName);
        response.setBody(body);
        response.setRowCount(rowCount);
        response.setTruncated(truncated);
        response.setContentType(contentType);
        return response;
    }
}

