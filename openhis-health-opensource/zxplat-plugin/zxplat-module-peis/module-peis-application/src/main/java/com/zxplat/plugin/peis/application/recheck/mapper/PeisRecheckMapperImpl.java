/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.recheck.mapper;

import com.zxplat.plugin.peis.application.recheck.dto.RecheckRowDto;
import com.zxplat.plugin.peis.application.recheck.request.SearchRecheckRequest;
import com.zxplat.plugin.peis.application.recheck.response.SearchRecheckResponse;
import com.zxplat.framework.core.application.paging.PageQueryBinder;
import com.zxplat.framework.core.application.paging.PagedResultBuilder;
import com.zxplat.plugin.peis.domain.encounterext.service.PeisEncounterExtQueryService;
import com.zxplat.plugin.peis.domain.recheck.queries.model.RecheckTaskRowModel;
import com.zxplat.plugin.peis.domain.recheck.queries.query.RecheckTaskQuery;
import com.zxplat.domain.share.constants.PeisRecheckTaskStatusConstants;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 复查管理应用层映射实现（AS-PEIS-REC-001）。
 */
@Component
@RequiredArgsConstructor
public class PeisRecheckMapperImpl implements PeisRecheckMapper {

    private final PeisEncounterExtQueryService peisEncounterExtQueryService;

    /**
     * HTTP 请求转领域查询。
     *
     * @param request HTTP 请求
     * @return 领域查询
     */
    @Override
    public RecheckTaskQuery toRecheckTaskQuery(SearchRecheckRequest request) {
        RecheckTaskQuery query = new RecheckTaskQuery();
        if (request == null) {
            return query;
        }
        PageQueryBinder.bind(request, query);
        query.setOrgId(request.getOrgId());
        query.setStatusCode(request.getStatusCode());
        return query;
    }

    /**
     * 领域行转 DTO。
     *
     * @param rows 领域行
     * @return DTO 列表
     */
    @Override
    public List<RecheckRowDto> toRecheckRows(List<RecheckTaskRowModel> rows) {
        List<RecheckRowDto> result = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return result;
        }
        Set<Long> encounterIds = new HashSet<>();
        for (RecheckTaskRowModel row : rows) {
            if (row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        Map<Long, com.zxplat.plugin.peis.domain.encounterext.queries.model.PeisEncounterExtModel> extMap =
            peisEncounterExtQueryService.mapByEncounterIds(encounterIds);
        for (RecheckTaskRowModel row : rows) {
            RecheckRowDto dto = new RecheckRowDto();
            dto.setTaskId(row.getTaskId());
            dto.setEncounterId(row.getEncounterId());
            dto.setStatusCode(row.getStatusCode());
            dto.setReason(row.getReason());
            dto.setProgressLabel(resolveProgressLabel(row.getStatusCode()));
            if (row.getEncounterId() != null) {
                com.zxplat.plugin.peis.domain.encounterext.queries.model.PeisEncounterExtModel ext =
                    extMap.get(row.getEncounterId());
                if (ext != null) {
                    dto.setBusNo(ext.getBusNo());
                }
            }
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
    public SearchRecheckResponse toSearchRecheckResponse(
        Page<RecheckTaskRowModel> page, List<RecheckRowDto> rows) {
        return PagedResultBuilder.build(new SearchRecheckResponse(), rows, page);
    }

    private String resolveProgressLabel(String statusCode) {
        if (PeisRecheckTaskStatusConstants.COMPLETED.equals(statusCode)) {
            return "复查已完成";
        }
        if (PeisRecheckTaskStatusConstants.IN_PROGRESS.equals(statusCode)) {
            return "复查中";
        }
        if (PeisRecheckTaskStatusConstants.REQUESTED.equals(statusCode)) {
            return "复查待确认";
        }
        return "复查中";
    }
}
