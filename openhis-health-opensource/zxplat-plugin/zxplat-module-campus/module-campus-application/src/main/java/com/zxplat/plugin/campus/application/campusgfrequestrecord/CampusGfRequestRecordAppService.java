/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusgfrequestrecord;

import com.zxplat.plugin.campus.application.campusgfrequestrecord.request.CreateAdjustRequestRequest;
import com.zxplat.plugin.campus.application.campusgfrequestrecord.request.GetRequestDetailRequest;
import com.zxplat.plugin.campus.application.campusgfrequestrecord.request.PageRequestsRequest;
import com.zxplat.plugin.campus.application.campusgfrequestrecord.response.CreateAdjustRequestResponse;
import com.zxplat.plugin.campus.application.campusgfrequestrecord.response.GfRatioRequestDto;
import com.zxplat.plugin.campus.application.campusgfrequestrecord.response.PageRequestsResponse;
import com.zxplat.plugin.campus.application.share.CampusGfMapper;
import com.zxplat.plugin.campus.application.share.CampusOperatorResolver;
import com.zxplat.plugin.campus.application.share.CampusPagedResultBuilder;
import com.zxplat.plugin.campus.domain.gfratiorequest.entity.GfRatioRequest;
import com.zxplat.plugin.campus.domain.gfratiorequest.service.GfRatioRequestDomainService;
import com.zxplat.plugin.campus.domain.gfratiorequest.service.GfRatioRequestQueryService;
import com.zxplat.framework.core.infrastructure.security.ICurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 比例调整申请记录应用服务。
 *
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/campusGfRequestRecord/{methodName}}。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class CampusGfRequestRecordAppService {

    private final GfRatioRequestDomainService gfRatioRequestDomainService;
    private final GfRatioRequestQueryService gfRatioRequestQueryService;
    private final CampusGfMapper campusGfMapper;
    private final CampusOperatorResolver campusOperatorResolver;

    @Autowired(required = false)
    private ICurrentUser currentUser;

    public CreateAdjustRequestResponse createAdjustRequest(@Valid CreateAdjustRequestRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        String applicantName = currentUser != null ? currentUser.getCurrentUserName() : null;
        GfRatioRequest saved = gfRatioRequestDomainService.createRequest(
            campusGfMapper.toRequestEntity(request), operatorId, applicantName);
        CreateAdjustRequestResponse response = new CreateAdjustRequestResponse();
        response.setId(saved.getId());
        response.setRequestNo(saved.getRequestNo());
        return response;
    }

    public PageRequestsResponse pageRequests(@Valid PageRequestsRequest request) {
        Page<GfRatioRequest> page = gfRatioRequestQueryService.pageRequests(
            request.getTenantId(),
            request.getChargeModeEnum(),
            request.getStatusEnum(),
            request.getKeyword(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<GfRatioRequestDto> rows = new ArrayList<>();
        for (GfRatioRequest entity : page.getContent()) {
            rows.add(campusGfMapper.toRequestDto(entity));
        }
        return CampusPagedResultBuilder.build(new PageRequestsResponse(), rows, page);
    }

    public GfRatioRequestDto getRequestDetail(@Valid GetRequestDetailRequest request) {
        return campusGfMapper.toRequestDto(
            gfRatioRequestQueryService.getById(request.getTenantId(), request.getRequestId()));
    }
}
