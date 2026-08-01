/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campuscollege;

import com.openhis.campus.application.campuscollege.mapper.CampusCollegeMapper;
import com.openhis.campus.application.campuscollege.request.BindCollegeCampusRequest;
import com.openhis.campus.application.campuscollege.request.ChangeCollegeActiveRequest;
import com.openhis.campus.application.campuscollege.request.CreateCollegeRequest;
import com.openhis.campus.application.campuscollege.request.GetCollegeByIdRequest;
import com.openhis.campus.application.campuscollege.request.ListActiveCollegesRequest;
import com.openhis.campus.application.campuscollege.request.PageCollegesRequest;
import com.openhis.campus.application.campuscollege.request.UpdateCollegeRequest;
import com.openhis.campus.application.campuscollege.response.CampusCollegeDto;
import com.openhis.campus.application.campuscollege.response.ListActiveCollegesResponse;
import com.openhis.campus.application.campuscollege.response.PageCollegesResponse;
import com.openhis.campus.application.campuscollege.response.SaveCampusCollegeResponse;
import com.openhis.campus.application.share.CampusOperatorResolver;
import com.openhis.campus.application.share.CampusPagedResultBuilder;
import com.openhis.campus.domain.campuscollege.entity.CampusCollege;
import com.openhis.campus.domain.campuscollege.service.CampusCollegeDomainService;
import com.openhis.campus.domain.campuscollege.service.CampusCollegeQueryService;
import com.openhis.campus.domain.campussite.entity.CampusSite;
import com.openhis.campus.domain.campussite.repository.CampusSiteRepository;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 学院应用服务。
 *
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/campusCollege/{methodName}}。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class CampusCollegeAppService {

    private final CampusCollegeQueryService campusCollegeQueryService;
    private final CampusCollegeDomainService campusCollegeDomainService;
    private final CampusCollegeMapper campusCollegeMapper;
    private final CampusSiteRepository campusSiteRepository;
    private final CampusOperatorResolver campusOperatorResolver;

    public PageCollegesResponse pageColleges(@Valid PageCollegesRequest request) {
        Page<CampusCollege> page = campusCollegeQueryService.page(
            request.getTenantId(),
            request.getKeyword(),
            request.getActiveFlag(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        Map<Long, CampusSite> siteById = loadSitesForColleges(request.getTenantId(), page.getContent());
        List<CampusCollegeDto> rows = new ArrayList<>();
        for (CampusCollege entity : page.getContent()) {
            CampusSite site = entity.getCampusSiteId() == null ? null : siteById.get(entity.getCampusSiteId());
            rows.add(campusCollegeMapper.toDto(entity, site));
        }
        return CampusPagedResultBuilder.build(new PageCollegesResponse(), rows, page);
    }

    public SaveCampusCollegeResponse createCollege(@Valid CreateCollegeRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        CampusCollege saved = campusCollegeDomainService.create(campusCollegeMapper.toEntity(request), operatorId);
        SaveCampusCollegeResponse response = new SaveCampusCollegeResponse();
        response.setId(saved.getId());
        return response;
    }

    public SaveCampusCollegeResponse updateCollege(@Valid UpdateCollegeRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        CampusCollege saved = campusCollegeDomainService.update(campusCollegeMapper.toEntity(request), operatorId);
        SaveCampusCollegeResponse response = new SaveCampusCollegeResponse();
        response.setId(saved.getId());
        return response;
    }

    public SaveCampusCollegeResponse changeCollegeActive(@Valid ChangeCollegeActiveRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        CampusCollege saved = campusCollegeDomainService.changeActive(
            request.getTenantId(), request.getId(), request.getActiveFlag(), operatorId);
        SaveCampusCollegeResponse response = new SaveCampusCollegeResponse();
        response.setId(saved.getId());
        return response;
    }

    public SaveCampusCollegeResponse bindCollegeCampus(@Valid BindCollegeCampusRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        CampusCollege saved = campusCollegeDomainService.bindCampus(
            request.getTenantId(), request.getId(), request.getCampusSiteId(), operatorId);
        SaveCampusCollegeResponse response = new SaveCampusCollegeResponse();
        response.setId(saved.getId());
        return response;
    }

    public ListActiveCollegesResponse listActiveColleges(@Valid ListActiveCollegesRequest request) {
        ListActiveCollegesResponse response = new ListActiveCollegesResponse();
        List<CampusCollege> colleges = campusCollegeQueryService.listActive(
            request.getTenantId(), request.getCampusCode());
        Map<Long, CampusSite> siteById = loadSitesForColleges(request.getTenantId(), colleges);
        for (CampusCollege entity : colleges) {
            CampusSite site = entity.getCampusSiteId() == null ? null : siteById.get(entity.getCampusSiteId());
            response.getRecords().add(campusCollegeMapper.toDto(entity, site));
        }
        return response;
    }

    public CampusCollegeDto getCollegeById(@Valid GetCollegeByIdRequest request) {
        CampusCollege entity = campusCollegeQueryService.getById(request.getTenantId(), request.getId());
        CampusSite site = null;
        if (entity.getCampusSiteId() != null) {
            Long tenantId = request.getTenantId() != null ? request.getTenantId() : TenantUtils.getCurrentTenantId();
            site = campusSiteRepository.findByIdAndTenantIdAndIsDeletedFalse(entity.getCampusSiteId(), tenantId)
                .orElse(null);
        }
        return campusCollegeMapper.toDto(entity, site);
    }

    private Map<Long, CampusSite> loadSitesForColleges(Long tenantId, List<CampusCollege> colleges) {
        Long resolvedTenantId = tenantId != null ? tenantId : TenantUtils.getCurrentTenantId();
        Map<Long, CampusSite> siteById = new HashMap<>();
        if (resolvedTenantId == null) {
            return siteById;
        }
        for (CampusCollege college : colleges) {
            Long siteId = college.getCampusSiteId();
            if (siteId != null && !siteById.containsKey(siteId)) {
                campusSiteRepository.findByIdAndTenantIdAndIsDeletedFalse(siteId, resolvedTenantId)
                    .ifPresent(site -> siteById.put(siteId, site));
            }
        }
        return siteById;
    }
}
