/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campussite;

import com.openhis.campus.application.campussite.mapper.CampusSiteMapper;
import com.openhis.campus.application.campussite.request.ChangeCampusSiteActiveRequest;
import com.openhis.campus.application.campussite.request.CreateCampusSiteRequest;
import com.openhis.campus.application.campussite.request.ListActiveCampusSitesRequest;
import com.openhis.campus.application.campussite.request.PageCampusSitesRequest;
import com.openhis.campus.application.campussite.request.ResolveCampusByCodeRequest;
import com.openhis.campus.application.campussite.request.SetPrimaryCampusSiteRequest;
import com.openhis.campus.application.campussite.request.UpdateCampusSiteRequest;
import com.openhis.campus.application.campussite.response.CampusSiteDto;
import com.openhis.campus.application.campussite.response.ListActiveCampusSitesResponse;
import com.openhis.campus.application.campussite.response.PageCampusSitesResponse;
import com.openhis.campus.application.campussite.response.SaveCampusSiteResponse;
import com.openhis.campus.application.share.CampusOperatorResolver;
import com.openhis.campus.application.share.CampusPagedResultBuilder;
import com.openhis.campus.domain.campussite.entity.CampusSite;
import com.openhis.campus.domain.campussite.service.CampusSiteDomainService;
import com.openhis.campus.domain.campussite.service.CampusSiteQueryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;

/**
 * 校区应用服务。
 *
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/campusSite/{methodName}}。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class CampusSiteAppService {

    private final CampusSiteQueryService campusSiteQueryService;
    private final CampusSiteDomainService campusSiteDomainService;
    private final CampusSiteMapper campusSiteMapper;
    private final CampusOperatorResolver campusOperatorResolver;

    public PageCampusSitesResponse pageCampusSites(@Valid PageCampusSitesRequest request) {
        Page<CampusSite> page = campusSiteQueryService.page(
            request.getTenantId(),
            request.getKeyword(),
            request.getActiveFlag(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<CampusSiteDto> rows = new ArrayList<>();
        for (CampusSite entity : page.getContent()) {
            rows.add(campusSiteMapper.toDto(entity));
        }
        return CampusPagedResultBuilder.build(new PageCampusSitesResponse(), rows, page);
    }

    public SaveCampusSiteResponse createCampusSite(@Valid CreateCampusSiteRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        CampusSite saved = campusSiteDomainService.create(campusSiteMapper.toEntity(request), operatorId);
        SaveCampusSiteResponse response = new SaveCampusSiteResponse();
        response.setId(saved.getId());
        return response;
    }

    public SaveCampusSiteResponse updateCampusSite(@Valid UpdateCampusSiteRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        CampusSite saved = campusSiteDomainService.update(campusSiteMapper.toEntity(request), operatorId);
        SaveCampusSiteResponse response = new SaveCampusSiteResponse();
        response.setId(saved.getId());
        return response;
    }

    public SaveCampusSiteResponse changeCampusSiteActive(@Valid ChangeCampusSiteActiveRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        CampusSite saved = campusSiteDomainService.changeActive(
            request.getTenantId(), request.getId(), request.getActiveFlag(), operatorId);
        SaveCampusSiteResponse response = new SaveCampusSiteResponse();
        response.setId(saved.getId());
        return response;
    }

    public SaveCampusSiteResponse setPrimaryCampusSite(@Valid SetPrimaryCampusSiteRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        CampusSite saved = campusSiteDomainService.setPrimary(request.getTenantId(), request.getId(), operatorId);
        SaveCampusSiteResponse response = new SaveCampusSiteResponse();
        response.setId(saved.getId());
        return response;
    }

    public ListActiveCampusSitesResponse listActiveCampusSites(@Valid ListActiveCampusSitesRequest request) {
        ListActiveCampusSitesResponse response = new ListActiveCampusSitesResponse();
        for (CampusSite entity : campusSiteQueryService.listActive(request.getTenantId())) {
            response.getRecords().add(campusSiteMapper.toDto(entity));
        }
        return response;
    }

    public CampusSiteDto resolveCampusByCode(@Valid ResolveCampusByCodeRequest request) {
        CampusSite entity = campusSiteQueryService.getByCode(request.getTenantId(), request.getCampusCode());
        return campusSiteMapper.toDto(entity);
    }
}
