/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.report.mapper;

import com.openhis.health.application.peis.report.dto.PrintableReportRowDto;
import com.openhis.health.application.peis.report.request.SearchPrintableRequest;
import com.openhis.health.application.peis.report.response.SearchPrintableResponse;
import com.openhis.health.domain.peis.encounterprogress.queries.model.PrintableReportRowModel;
import com.openhis.health.domain.peis.encounterprogress.queries.query.ReportPrintQuery;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 报告打印应用层映射（AS-PEIS-REP-001）。
 */
public interface PeisReportPrintMapper {

    /**
     * HTTP 请求转领域查询。
     *
     * @param request HTTP 请求
     * @return 领域查询
     */
    ReportPrintQuery toReportPrintQuery(SearchPrintableRequest request);

    /**
     * 领域行转 DTO。
     *
     * @param rows 领域行
     * @return DTO 列表
     */
    List<PrintableReportRowDto> toPrintableRows(List<PrintableReportRowModel> rows);

    /**
     * 分页响应装配。
     *
     * @param page 领域分页
     * @param rows DTO 行
     * @return 分页响应
     */
    SearchPrintableResponse toSearchPrintableResponse(
        Page<PrintableReportRowModel> page, List<PrintableReportRowDto> rows);
}
