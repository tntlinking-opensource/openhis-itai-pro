/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusgfapproval;

import com.zxplat.plugin.campus.application.campusgfapproval.request.ApproveRequestRequest;
import com.zxplat.plugin.campus.application.campusgfapproval.request.GetApprovalDetailRequest;
import com.zxplat.plugin.campus.application.campusgfapproval.request.PageFinishedRequest;
import com.zxplat.plugin.campus.application.campusgfapproval.request.PageTodoRequest;
import com.zxplat.plugin.campus.application.campusgfapproval.request.RejectRequestRequest;
import com.zxplat.plugin.campus.application.campusgfapproval.response.ApprovalActionResponse;
import com.zxplat.plugin.campus.application.campusgfapproval.response.PageFinishedResponse;
import com.zxplat.plugin.campus.application.campusgfapproval.response.PageTodoResponse;
import com.zxplat.plugin.campus.application.campusgfrequestrecord.response.GfRatioRequestDto;
import com.zxplat.plugin.campus.application.share.CampusGfMapper;
import com.zxplat.plugin.campus.application.share.CampusOperatorResolver;
import com.zxplat.plugin.campus.application.share.CampusPagedResultBuilder;
import com.zxplat.plugin.campus.domain.gfratiorequest.entity.GfRatioRequest;
import com.zxplat.plugin.campus.domain.gfratiorequest.service.GfRatioRequestDomainService;
import com.zxplat.plugin.campus.domain.gfratiorequest.service.GfRatioRequestQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 比例审批应用服务。
 *
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/campusGfApproval/{methodName}}。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class CampusGfApprovalAppService {

    private final GfRatioRequestQueryService gfRatioRequestQueryService;
    private final GfRatioRequestDomainService gfRatioRequestDomainService;
    private final CampusGfMapper campusGfMapper;
    private final CampusOperatorResolver campusOperatorResolver;

    public PageTodoResponse pageTodo(@Valid PageTodoRequest request) {
        Page<GfRatioRequest> page = gfRatioRequestQueryService.pageTodo(
            request.getTenantId(),
            request.getChargeModeEnum(),
            request.getKeyword(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        return buildPageResponse(new PageTodoResponse(), page);
    }

    public PageFinishedResponse pageFinished(@Valid PageFinishedRequest request) {
        Page<GfRatioRequest> page = gfRatioRequestQueryService.pageFinished(
            request.getTenantId(),
            request.getChargeModeEnum(),
            request.getKeyword(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        return buildPageResponse(new PageFinishedResponse(), page);
    }

    public GfRatioRequestDto getApprovalDetail(@Valid GetApprovalDetailRequest request) {
        return campusGfMapper.toRequestDto(
            gfRatioRequestQueryService.getById(request.getTenantId(), request.getRequestId()));
    }

    @Transactional
    public ApprovalActionResponse approve(@Valid ApproveRequestRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        GfRatioRequest saved = gfRatioRequestDomainService.approve(
            request.getTenantId(), request.getRequestId(), operatorId, request.getComment());
        return toActionResponse(saved);
    }

    public ApprovalActionResponse reject(@Valid RejectRequestRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        GfRatioRequest saved = gfRatioRequestDomainService.reject(
            request.getTenantId(), request.getRequestId(), operatorId, request.getComment());
        return toActionResponse(saved);
    }

    private <T extends com.zxplat.framework.core.application.dto.PagedResult<GfRatioRequestDto>> T buildPageResponse(
        T response,
        Page<GfRatioRequest> page) {
        List<GfRatioRequestDto> rows = new ArrayList<>();
        for (GfRatioRequest entity : page.getContent()) {
            rows.add(campusGfMapper.toRequestDto(entity));
        }
        return CampusPagedResultBuilder.build(response, rows, page);
    }

    private ApprovalActionResponse toActionResponse(GfRatioRequest saved) {
        ApprovalActionResponse response = new ApprovalActionResponse();
        response.setRequestId(saved.getId());
        response.setStatusEnum(saved.getStatusEnum());
        return response;
    }
}
