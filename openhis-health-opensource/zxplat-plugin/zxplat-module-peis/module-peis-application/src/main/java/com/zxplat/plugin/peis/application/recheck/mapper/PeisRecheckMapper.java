/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.recheck.mapper;

import com.zxplat.plugin.peis.application.recheck.dto.RecheckRowDto;
import com.zxplat.plugin.peis.application.recheck.request.SearchRecheckRequest;
import com.zxplat.plugin.peis.application.recheck.response.SearchRecheckResponse;
import com.zxplat.plugin.peis.domain.recheck.queries.model.RecheckTaskRowModel;
import com.zxplat.plugin.peis.domain.recheck.queries.query.RecheckTaskQuery;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 复查管理应用层映射（AS-PEIS-REC-001）。
 */
public interface PeisRecheckMapper {

    /**
     * HTTP 请求转领域查询。
     *
     * @param request HTTP 请求
     * @return 领域查询
     */
    RecheckTaskQuery toRecheckTaskQuery(SearchRecheckRequest request);

    /**
     * 领域行转 DTO（含 progressLabel）。
     *
     * @param rows 领域行
     * @return DTO 列表
     */
    List<RecheckRowDto> toRecheckRows(List<RecheckTaskRowModel> rows);

    /**
     * 分页响应。
     *
     * @param page 领域分页
     * @param rows DTO 行
     * @return 分页响应
     */
    SearchRecheckResponse toSearchRecheckResponse(Page<RecheckTaskRowModel> page, List<RecheckRowDto> rows);
}
