/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.report.grant.mapper;

import com.zxplat.plugin.peis.application.report.grant.dto.GrantRowDto;
import com.zxplat.plugin.peis.application.report.grant.request.SearchPendingGrantRequest;
import com.zxplat.plugin.peis.application.report.grant.response.SearchPendingGrantResponse;
import com.zxplat.framework.core.application.paging.PageQueryBinder;
import com.zxplat.framework.core.application.paging.PagedResultBuilder;
import com.zxplat.plugin.peis.domain.reportgrantext.queries.model.GrantRowModel;
import com.zxplat.plugin.peis.domain.reportgrantext.queries.query.GrantSearchQuery;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 报告发放应用层映射实现（AS-PEIS-REP-002）。
 */
@Component
public class PeisReportGrantMapperImpl implements PeisReportGrantMapper {

    /**
     * HTTP 请求转领域查询。
     *
     * @param request HTTP 请求
     * @return 领域查询
     */
    @Override
    public GrantSearchQuery toGrantSearchQuery(SearchPendingGrantRequest request) {
        GrantSearchQuery query = new GrantSearchQuery();
        if (request == null) {
            return query;
        }
        PageQueryBinder.bind(request, query);
        query.setOrgId(request.getOrgId());
        query.setGrantStatusCode(request.getGrantStatusCode());
        if (request.getDateFrom() != null) {
            query.setStartDate(request.getDateFrom().atStartOfDay());
        }
        if (request.getDateTo() != null) {
            query.setEndDate(request.getDateTo().atTime(LocalTime.MAX));
        }
        return query;
    }

    /**
     * 合并 enrich 结果（rows 已由 AppService 填充 encounter/患者字段）。
     *
     * @param models 领域行
     * @param rows   enrich 后的 DTO
     * @return DTO 列表
     */
    @Override
    public List<GrantRowDto> toGrantRows(List<GrantRowModel> models, List<GrantRowDto> rows) {
        if (rows != null) {
            return rows;
        }
        List<GrantRowDto> result = new ArrayList<>();
        if (models == null) {
            return result;
        }
        for (GrantRowModel model : models) {
            GrantRowDto dto = new GrantRowDto();
            dto.setDiagnosticReportId(model.getDiagnosticReportId());
            dto.setGrantStatusCode(model.getGrantStatusCode());
            dto.setReportFinalTime(model.getGrantTime());
            result.add(dto);
        }
        return result;
    }

    /**
     * 分页响应。
     *
     * @param page 领域分页
     * @param rows DTO 行
     * @return 分页响应
     */
    @Override
    public SearchPendingGrantResponse toSearchPendingGrantResponse(
        Page<GrantRowModel> page, List<GrantRowDto> rows) {
        return PagedResultBuilder.build(new SearchPendingGrantResponse(), rows, page);
    }
}
