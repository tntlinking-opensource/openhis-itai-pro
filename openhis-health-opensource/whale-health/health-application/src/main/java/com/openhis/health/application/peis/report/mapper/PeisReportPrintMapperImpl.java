/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.report.mapper;

import com.openhis.health.application.peis.report.dto.PrintableReportRowDto;
import com.openhis.health.application.peis.report.request.SearchPrintableRequest;
import com.openhis.health.application.peis.report.response.SearchPrintableResponse;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.domain.peis.encounterprogress.queries.model.PrintableReportRowModel;
import com.openhis.health.domain.peis.encounterprogress.queries.query.ReportPrintQuery;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 报告打印应用层映射实现（AS-PEIS-REP-001）。
 */
@Component
public class PeisReportPrintMapperImpl implements PeisReportPrintMapper {

    /**
     * HTTP 请求转领域查询。
     *
     * @param request HTTP 请求
     * @return 领域查询
     */
    @Override
    public ReportPrintQuery toReportPrintQuery(SearchPrintableRequest request) {
        ReportPrintQuery query = new ReportPrintQuery();
        if (request == null) {
            return query;
        }
        PageQueryBinder.bind(request, query);
        query.setOrgId(request.getOrgId());
        if (request.getDateFrom() != null) {
            query.setStartDate(request.getDateFrom().atStartOfDay());
        }
        if (request.getDateTo() != null) {
            query.setEndDate(request.getDateTo().atTime(LocalTime.MAX));
        }
        return query;
    }

    /**
     * 领域行转 DTO。
     *
     * @param rows 领域行
     * @return DTO 列表
     */
    @Override
    public List<PrintableReportRowDto> toPrintableRows(List<PrintableReportRowModel> rows) {
        List<PrintableReportRowDto> result = new ArrayList<>();
        if (rows == null) {
            return result;
        }
        for (PrintableReportRowModel row : rows) {
            PrintableReportRowDto dto = new PrintableReportRowDto();
            dto.setDiagnosticReportId(row.getDiagnosticReportId());
            dto.setEncounterId(row.getEncounterId());
            dto.setPatientId(row.getPatientId());
            dto.setPatientName(row.getPatientName());
            dto.setBusNo(row.getBusNo());
            dto.setFinalizedAt(row.getFinalizedAt());
            dto.setPdfStorageKey(row.getPdfStorageKey());
            dto.setPrintCount(row.getPrintCount());
            result.add(dto);
        }
        return result;
    }

    /**
     * 分页响应装配。
     *
     * @param page 领域分页
     * @param rows DTO 行
     * @return 分页响应
     */
    @Override
    public SearchPrintableResponse toSearchPrintableResponse(
        Page<PrintableReportRowModel> page, List<PrintableReportRowDto> rows) {
        return PagedResultBuilder.build(new SearchPrintableResponse(), rows, page);
    }
}
