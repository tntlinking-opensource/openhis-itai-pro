/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.org;

import com.zxplat.plugin.campus.application.campuscollege.mapper.CampusCollegeMapper;
import com.zxplat.plugin.campus.application.campussite.mapper.CampusSiteMapper;
import com.zxplat.plugin.campus.application.campusdepartment.mapper.CampusDepartmentMapper;
import com.zxplat.plugin.campus.domain.campuscollege.entity.CampusCollege;
import com.zxplat.plugin.campus.domain.campuscollege.service.CampusCollegeQueryService;
import com.zxplat.plugin.campus.domain.campusdepartment.entity.CampusDepartment;
import com.zxplat.plugin.campus.domain.campusdepartment.service.CampusDepartmentQueryService;
import com.zxplat.plugin.campus.domain.campussite.entity.CampusSite;
import com.zxplat.plugin.campus.domain.campussite.repository.CampusSiteRepository;
import com.zxplat.plugin.campus.domain.campussite.service.CampusSiteQueryService;
import com.zxplat.plugin.campus.domain.org.CampusOrgQueryReader;
import com.zxplat.plugin.campus.domain.org.view.CampusCollegeView;
import com.zxplat.plugin.campus.domain.org.view.CampusDepartmentView;
import com.zxplat.plugin.campus.domain.org.view.CampusSiteView;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CampusOrgQueryReaderImpl implements CampusOrgQueryReader {

    private final CampusSiteQueryService campusSiteQueryService;
    private final CampusCollegeQueryService campusCollegeQueryService;
    private final CampusDepartmentQueryService campusDepartmentQueryService;
    private final CampusSiteRepository campusSiteRepository;
    private final CampusSiteMapper campusSiteMapper;
    private final CampusCollegeMapper campusCollegeMapper;
    private final CampusDepartmentMapper campusDepartmentMapper;

    @Override
    public List<CampusSiteView> listActiveCampusSites(Long tenantId) {
        List<CampusSiteView> views = new ArrayList<>();
        for (CampusSite site : campusSiteQueryService.listActive(tenantId)) {
            views.add(campusSiteMapper.toView(site));
        }
        return views;
    }

    @Override
    public Optional<CampusSiteView> resolveCampusByCode(Long tenantId, String campusCode) {
        try {
            CampusSite site = campusSiteQueryService.getByCode(tenantId, campusCode);
            return Optional.of(campusSiteMapper.toView(site));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<CampusCollegeView> listActiveColleges(Long tenantId, String campusCodeOrNull) {
        List<CampusCollege> colleges = campusCollegeQueryService.listActive(tenantId, campusCodeOrNull);
        Map<Long, CampusSite> siteById = loadSites(tenantId, colleges);
        List<CampusCollegeView> views = new ArrayList<>();
        for (CampusCollege college : colleges) {
            CampusSite site = college.getCampusSiteId() == null ? null : siteById.get(college.getCampusSiteId());
            views.add(campusCollegeMapper.toView(college, site));
        }
        return views;
    }

    @Override
    public Optional<CampusCollegeView> getCollegeById(Long tenantId, Long collegeId) {
        if (collegeId == null) {
            return Optional.empty();
        }
        try {
            CampusCollege college = campusCollegeQueryService.getById(tenantId, collegeId);
            CampusSite site = null;
            if (college.getCampusSiteId() != null) {
                Long resolvedTenantId = tenantId != null ? tenantId : TenantUtils.getCurrentTenantId();
                site = campusSiteRepository.findByIdAndTenantIdAndIsDeletedFalse(
                    college.getCampusSiteId(), resolvedTenantId).orElse(null);
            }
            return Optional.of(campusCollegeMapper.toView(college, site));
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    @Override
    public List<CampusDepartmentView> listDepartments(
        Long tenantId,
        Long collegeIdOrNull,
        String campusCodeOrNull) {
        Long campusSiteId = null;
        if (campusCodeOrNull != null && !campusCodeOrNull.isBlank()) {
            Optional<CampusSiteView> site = resolveCampusByCode(tenantId, campusCodeOrNull);
            campusSiteId = site.map(CampusSiteView::getId).orElse(null);
        }
        List<CampusDepartment> departments = campusDepartmentQueryService.listActive(
            tenantId, campusSiteId, collegeIdOrNull);
        List<CampusDepartmentView> views = new ArrayList<>();
        for (CampusDepartment department : departments) {
            views.add(campusDepartmentMapper.toView(department));
        }
        return views;
    }

    private Map<Long, CampusSite> loadSites(Long tenantId, List<CampusCollege> colleges) {
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
