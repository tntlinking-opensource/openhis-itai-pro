/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.mrms.list.service;

import com.openhis.health.application.mrms.list.dto.CaseRecordListItemDto;
import com.openhis.health.application.mrms.list.mapper.CaseRecordListMapper;
import com.openhis.health.application.mrms.list.request.QueryCaseRecordListRequest;
import com.openhis.health.domain.mrms.caserecord.queries.CaseRecordPageQuery;
import com.openhis.health.domain.mrms.caserecord.queries.model.CaseRecordListItemQueryModel;
import com.openhis.health.domain.mrms.caserecord.service.CaseRecordQueryService;
import com.openhis.health.domain.share.constants.MrmsErrorCodes;
import com.openhis.health.application.mrms.support.MrmsOperatorResolver;
import com.openhis.health.application.shared.validator.PageWindowGate;
import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 病案检索列表应用服务（IF-MR-List）。
 *
 * <p>路由前缀 {@code POST /api/app/mrmsCaseRecordList/*}。病案室/临床按多条件分页检索病案主档。</p>
 */
@Service
@RequiredArgsConstructor
public class MrmsCaseRecordListAppService {

    private final CaseRecordQueryService caseRecordQueryService;
    private final CaseRecordListMapper caseRecordListMapper;
    private final MrmsOperatorResolver operatorResolver;
    private final PageWindowGate pageWindowGate;

    /**
     * 分页检索病案列表。
     *
     * @param request 检索条件
     * @return 分页结果
     */
    public PagedResult<CaseRecordListItemDto> queryPage(QueryCaseRecordListRequest request) {
        // Gate：分页窗口合法
        pageWindowGate.requirePositivePageWindow(request, MrmsErrorCodes.CASE_RECORD_INVALID_STATUS, "分页参数非法");
        int pageNo = request.getPageNo();
        int pageSize = request.getPageSize();
        // 分页上限 200，防止大页拖垮列表
        if (pageSize > 200) {
            throw new ValidationException("每页条数不能超过 200");
        }
        // Pre：组装领域分页查询
        CaseRecordPageQuery query = new CaseRecordPageQuery();
        query.setTenantId(operatorResolver.requireTenantId());
        query.setKeyword(request.getKeyword());
        query.setStatusCode(request.getStatusCode());
        query.setStatusCodes(request.getStatusCodes());
        query.setAdmCatyName(request.getAdmCatyName());
        query.setAttendingPractitionerId(request.getAttendingPractitionerId());
        // 前端日期选择器传 yyyy-MM-dd；领域按 dscg_time 闭区间含当日
        if (request.getDscgFrom() != null) {
            query.setDscgFrom(request.getDscgFrom().atStartOfDay());
        }
        if (request.getDscgTo() != null) {
            query.setDscgTo(request.getDscgTo().atTime(LocalTime.MAX));
        }
        Page<CaseRecordListItemQueryModel> page = caseRecordQueryService.queryPage(
                query,
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "dscgTime", "id")));
        // Asm：映射列表 DTO
        List<CaseRecordListItemDto> items = new ArrayList<>();
        for (CaseRecordListItemQueryModel row : page.getContent()) {
            items.add(caseRecordListMapper.toListItem(row));
        }
        return PagedResult.of(items, page.getTotalElements(), pageNo, pageSize);
    }
}
