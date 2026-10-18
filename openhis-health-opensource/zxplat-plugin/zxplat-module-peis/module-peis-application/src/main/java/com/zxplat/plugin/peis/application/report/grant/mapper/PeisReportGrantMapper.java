/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.report.grant.mapper;

import com.zxplat.plugin.peis.application.report.grant.dto.GrantRowDto;
import com.zxplat.plugin.peis.application.report.grant.request.SearchPendingGrantRequest;
import com.zxplat.plugin.peis.application.report.grant.response.SearchPendingGrantResponse;
import com.zxplat.plugin.peis.domain.reportgrantext.queries.model.GrantRowModel;
import com.zxplat.plugin.peis.domain.reportgrantext.queries.query.GrantSearchQuery;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 报告发放应用层映射（AS-PEIS-REP-002）。
 */
public interface PeisReportGrantMapper {

    /**
     * HTTP 请求转领域查询。
     *
     * @param request HTTP 请求
     * @return 领域查询
     */
    GrantSearchQuery toGrantSearchQuery(SearchPendingGrantRequest request);

    /**
     * 装配 enriched 行。
     *
     * @param models 领域行
     * @param rows   已 enrich 的 DTO
     * @return DTO 列表
     */
    List<GrantRowDto> toGrantRows(List<GrantRowModel> models, List<GrantRowDto> rows);

    /**
     * 分页响应。
     *
     * @param page 领域分页
     * @param rows DTO 行
     * @return 分页响应
     */
    SearchPendingGrantResponse toSearchPendingGrantResponse(Page<GrantRowModel> page, List<GrantRowDto> rows);
}
