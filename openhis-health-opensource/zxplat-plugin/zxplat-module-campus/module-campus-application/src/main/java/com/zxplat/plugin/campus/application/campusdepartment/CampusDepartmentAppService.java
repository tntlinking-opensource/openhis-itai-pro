/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campusdepartment;

import com.zxplat.plugin.campus.application.campusdepartment.mapper.CampusDepartmentMapper;
import com.zxplat.plugin.campus.application.campusdepartment.request.ChangeDepartmentActiveRequest;
import com.zxplat.plugin.campus.application.campusdepartment.request.CreateDepartmentRequest;
import com.zxplat.plugin.campus.application.campusdepartment.request.GetDepartmentByIdRequest;
import com.zxplat.plugin.campus.application.campusdepartment.request.GetDepartmentTreeRequest;
import com.zxplat.plugin.campus.application.campusdepartment.request.ListDepartmentsRequest;
import com.zxplat.plugin.campus.application.campusdepartment.request.PageDepartmentsRequest;
import com.zxplat.plugin.campus.application.campusdepartment.request.ReparentDepartmentRequest;
import com.zxplat.plugin.campus.application.campusdepartment.request.UpdateDepartmentRequest;
import com.zxplat.plugin.campus.application.campusdepartment.response.CampusDepartmentDto;
import com.zxplat.plugin.campus.application.campusdepartment.response.DepartmentTreeNodeDto;
import com.zxplat.plugin.campus.application.campusdepartment.response.GetDepartmentTreeResponse;
import com.zxplat.plugin.campus.application.campusdepartment.response.ListDepartmentsResponse;
import com.zxplat.plugin.campus.application.campusdepartment.response.PageDepartmentsResponse;
import com.zxplat.plugin.campus.application.campusdepartment.response.SaveCampusDepartmentResponse;
import com.zxplat.plugin.campus.application.share.CampusOperatorResolver;
import com.zxplat.plugin.campus.application.share.CampusPagedResultBuilder;
import com.zxplat.plugin.campus.domain.campuscollege.entity.CampusCollege;
import com.zxplat.plugin.campus.domain.campuscollege.service.CampusCollegeQueryService;
import com.zxplat.plugin.campus.domain.campusdepartment.entity.CampusDepartment;
import com.zxplat.plugin.campus.domain.campusdepartment.service.CampusDepartmentDomainService;
import com.zxplat.plugin.campus.domain.campusdepartment.service.CampusDepartmentQueryService;
import com.zxplat.plugin.campus.domain.campussite.entity.CampusSite;
import com.zxplat.plugin.campus.domain.campussite.service.CampusSiteQueryService;
import com.zxplat.framework.core.infrastructure.multitenancy.TenantUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.validation.annotation.Validated;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 部门应用服务。
 *
 * <p>接入：AppServiceControllerRegistrar → {@code POST /api/app/campusDepartment/{methodName}}。</p>
 */
@Service
@Validated
@RequiredArgsConstructor
public class CampusDepartmentAppService {

    private final CampusDepartmentQueryService campusDepartmentQueryService;
    private final CampusDepartmentDomainService campusDepartmentDomainService;
    private final CampusDepartmentMapper campusDepartmentMapper;
    private final CampusSiteQueryService campusSiteQueryService;
    private final CampusCollegeQueryService campusCollegeQueryService;
    private final CampusOperatorResolver campusOperatorResolver;

    public PageDepartmentsResponse pageDepartments(@Valid PageDepartmentsRequest request) {
        Page<CampusDepartment> page = campusDepartmentQueryService.page(
            request.getTenantId(),
            request.getKeyword(),
            request.getActiveFlag(),
            request.getCampusSiteId(),
            request.getCollegeId(),
            PageRequest.of(request.getPageNo() - 1, request.getPageSize()));
        List<CampusDepartmentDto> rows = new ArrayList<>();
        for (CampusDepartment entity : page.getContent()) {
            rows.add(campusDepartmentMapper.toDto(entity));
        }
        return CampusPagedResultBuilder.build(new PageDepartmentsResponse(), rows, page);
    }

    public SaveCampusDepartmentResponse createDepartment(@Valid CreateDepartmentRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        CampusDepartment saved = campusDepartmentDomainService.create(
            campusDepartmentMapper.toEntity(request), operatorId);
        SaveCampusDepartmentResponse response = new SaveCampusDepartmentResponse();
        response.setId(saved.getId());
        return response;
    }

    public SaveCampusDepartmentResponse updateDepartment(@Valid UpdateDepartmentRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        CampusDepartment saved = campusDepartmentDomainService.update(
            campusDepartmentMapper.toEntity(request), operatorId);
        SaveCampusDepartmentResponse response = new SaveCampusDepartmentResponse();
        response.setId(saved.getId());
        return response;
    }

    public SaveCampusDepartmentResponse changeDepartmentActive(@Valid ChangeDepartmentActiveRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        CampusDepartment saved = campusDepartmentDomainService.changeActive(
            request.getTenantId(), request.getId(), request.getActiveFlag(), operatorId);
        SaveCampusDepartmentResponse response = new SaveCampusDepartmentResponse();
        response.setId(saved.getId());
        return response;
    }

    public SaveCampusDepartmentResponse reparentDepartment(@Valid ReparentDepartmentRequest request) {
        Long operatorId = campusOperatorResolver.requireOperatorId();
        CampusDepartment saved = campusDepartmentDomainService.reparent(
            request.getTenantId(),
            request.getId(),
            request.getCampusSiteId(),
            request.getCollegeId(),
            operatorId);
        SaveCampusDepartmentResponse response = new SaveCampusDepartmentResponse();
        response.setId(saved.getId());
        return response;
    }

    public ListDepartmentsResponse listDepartments(@Valid ListDepartmentsRequest request) {
        ListDepartmentsResponse response = new ListDepartmentsResponse();
        List<CampusDepartment> departments;
        if (Boolean.TRUE.equals(request.getActiveOnly())) {
            departments = campusDepartmentQueryService.listActive(
                request.getTenantId(), request.getCampusSiteId(), request.getCollegeId());
        } else {
            departments = campusDepartmentQueryService.list(
                request.getTenantId(), request.getCampusSiteId(), request.getCollegeId());
        }
        for (CampusDepartment entity : departments) {
            response.getRecords().add(campusDepartmentMapper.toDto(entity));
        }
        return response;
    }

    public CampusDepartmentDto getDepartmentById(@Valid GetDepartmentByIdRequest request) {
        CampusDepartment entity = campusDepartmentQueryService.getById(request.getTenantId(), request.getId());
        return campusDepartmentMapper.toDto(entity);
    }

    public GetDepartmentTreeResponse getDepartmentTree(@Valid GetDepartmentTreeRequest request) {
        Long tenantId = request.getTenantId() != null ? request.getTenantId() : TenantUtils.getCurrentTenantId();
        boolean includeInactive = Boolean.TRUE.equals(request.getIncludeInactive());
        GetDepartmentTreeResponse response = new GetDepartmentTreeResponse();

        if (tenantId == null) {
            return response;
        }

        List<CampusSite> sites = includeInactive
            ? campusSiteQueryService.page(tenantId, null, null, PageRequest.of(0, 10_000)).getContent()
            : campusSiteQueryService.listActive(tenantId);

        List<CampusCollege> allColleges = campusCollegeQueryService.page(
            tenantId, null, includeInactive ? null : Boolean.TRUE, PageRequest.of(0, 10_000)).getContent();

        for (CampusSite site : sites) {
            DepartmentTreeNodeDto siteNode = new DepartmentTreeNodeDto();
            siteNode.setType("SITE");
            siteNode.setId(site.getId());
            siteNode.setCode(site.getCampusCode());
            siteNode.setName(site.getCampusName());

            for (CampusCollege college : allColleges) {
                if (college.getCampusSiteId() == null || !site.getId().equals(college.getCampusSiteId())) {
                    continue;
                }
                DepartmentTreeNodeDto collegeNode = new DepartmentTreeNodeDto();
                collegeNode.setType("COLLEGE");
                collegeNode.setId(college.getId());
                collegeNode.setCode(college.getCollegeCode());
                collegeNode.setName(college.getCollegeName());

                List<CampusDepartment> departments = filterDepartments(
                    includeInactive
                        ? campusDepartmentQueryService.list(tenantId, site.getId(), college.getId())
                        : campusDepartmentQueryService.listActive(tenantId, site.getId(), college.getId()),
                    college.getId(),
                    true);
                for (CampusDepartment department : departments) {
                    collegeNode.getChildren().add(toDeptNode(department));
                }
                siteNode.getChildren().add(collegeNode);
            }

            List<CampusDepartment> orphanDepts = filterDepartments(
                includeInactive
                    ? campusDepartmentQueryService.list(tenantId, site.getId(), null)
                    : campusDepartmentQueryService.listActive(tenantId, site.getId(), null),
                null,
                false);
            for (CampusDepartment department : orphanDepts) {
                siteNode.getChildren().add(toDeptNode(department));
            }

            response.getNodes().add(siteNode);
        }
        return response;
    }

    private List<CampusDepartment> filterDepartments(
        List<CampusDepartment> source,
        Long collegeId,
        boolean requireCollegeMatch) {
        return source.stream()
            .filter(dept -> requireCollegeMatch
                ? collegeId != null && collegeId.equals(dept.getCollegeId())
                : dept.getCollegeId() == null)
            .collect(Collectors.toList());
    }

    private DepartmentTreeNodeDto toDeptNode(CampusDepartment department) {
        DepartmentTreeNodeDto node = new DepartmentTreeNodeDto();
        node.setType("DEPT");
        node.setId(department.getId());
        node.setCode(department.getDepartmentCode());
        node.setName(department.getDepartmentName());
        return node;
    }
}
