/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.mrms.application.list.service;

import com.openhis.whale.core.application.dto.PagedResult;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.mrms.application.list.dto.CaseRecordListItemDto;
import com.openhis.whale.mrms.application.list.request.QueryCaseRecordListRequest;
import com.openhis.whale.mrms.application.shared.assembler.CaseRecordAssembler;
import com.openhis.whale.mrms.application.support.MrmsOperatorResolver;
import com.openhis.whale.mrms.domain.entity.CaseRecord;
import com.openhis.whale.mrms.domain.service.CaseRecordQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 病案检索列表 AppService → {@code POST /api/app/mrmsCaseRecordList/*}
 */
@Service
@RequiredArgsConstructor
public class MrmsCaseRecordListAppService {

    private final CaseRecordQueryService caseRecordQueryService;
    private final CaseRecordAssembler caseRecordAssembler;
    private final MrmsOperatorResolver operatorResolver;

    /**
     * 分页检索病案列表。
     *
     * @param request 检索条件
     * @return 分页结果
     */
    public PagedResult<CaseRecordListItemDto> queryPage(QueryCaseRecordListRequest request) {
        int pageNo = 1;
        if (request.getPageNo() != null && request.getPageNo() > 0) {
            pageNo = request.getPageNo();
        }
        int pageSize = 20;
        if (request.getPageSize() != null && request.getPageSize() > 0) {
            pageSize = request.getPageSize();
        }
        if (pageSize > 200) {
            throw new ValidationException("每页条数不能超过 200");
        }
        Long tenantId = operatorResolver.requireTenantId();
        Page<CaseRecord> page = caseRecordQueryService.queryPage(
                tenantId,
                request.getKeyword(),
                request.getStatusCode(),
                request.getStatusCodes(),
                request.getAdmCatyName(),
                request.getAttendingPractitionerId(),
                request.getDscgFrom(),
                request.getDscgTo(),
                PageRequest.of(pageNo - 1, pageSize, Sort.by(Sort.Direction.DESC, "dscgTime", "id")));
        List<CaseRecordListItemDto> items = new ArrayList<>();
        for (CaseRecord record : page.getContent()) {
            items.add(caseRecordAssembler.toListItem(record));
        }
        return PagedResult.of(items, page.getTotalElements(), pageNo, pageSize);
    }
}
