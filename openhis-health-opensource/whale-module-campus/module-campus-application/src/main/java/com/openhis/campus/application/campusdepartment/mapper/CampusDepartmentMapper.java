/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campusdepartment.mapper;

import com.openhis.campus.application.campusdepartment.request.CreateDepartmentRequest;
import com.openhis.campus.application.campusdepartment.request.UpdateDepartmentRequest;
import com.openhis.campus.application.campusdepartment.response.CampusDepartmentDto;
import com.openhis.campus.domain.campusdepartment.entity.CampusDepartment;
import com.openhis.campus.domain.org.view.CampusDepartmentView;
import org.springframework.stereotype.Component;

@Component
public class CampusDepartmentMapper {

    public CampusDepartmentDto toDto(CampusDepartment entity) {
        CampusDepartmentDto dto = new CampusDepartmentDto();
        dto.setId(entity.getId());
        dto.setDepartmentCode(entity.getDepartmentCode());
        dto.setDepartmentName(entity.getDepartmentName());
        dto.setCampusSiteId(entity.getCampusSiteId());
        dto.setCollegeId(entity.getCollegeId());
        dto.setDeptTypeEnum(entity.getDeptTypeEnum());
        dto.setActiveFlag(entity.getActiveFlag());
        dto.setSortOrder(entity.getSortOrder());
        dto.setRemark(entity.getRemark());
        return dto;
    }

    public CampusDepartmentView toView(CampusDepartment entity) {
        CampusDepartmentView view = new CampusDepartmentView();
        view.setId(entity.getId());
        view.setDepartmentCode(entity.getDepartmentCode());
        view.setDepartmentName(entity.getDepartmentName());
        view.setCampusSiteId(entity.getCampusSiteId());
        view.setCollegeId(entity.getCollegeId());
        return view;
    }

    public CampusDepartment toEntity(CreateDepartmentRequest request) {
        CampusDepartment entity = new CampusDepartment();
        entity.setTenantId(request.getTenantId());
        entity.setDepartmentCode(request.getDepartmentCode());
        entity.setDepartmentName(request.getDepartmentName());
        entity.setCampusSiteId(request.getCampusSiteId());
        entity.setCollegeId(request.getCollegeId());
        entity.setDeptTypeEnum(request.getDeptTypeEnum());
        entity.setActiveFlag(request.getActiveFlag());
        entity.setSortOrder(request.getSortOrder());
        entity.setRemark(request.getRemark());
        return entity;
    }

    public CampusDepartment toEntity(UpdateDepartmentRequest request) {
        CampusDepartment entity = new CampusDepartment();
        entity.setTenantId(request.getTenantId());
        entity.setId(request.getId());
        entity.setDepartmentName(request.getDepartmentName());
        entity.setDeptTypeEnum(request.getDeptTypeEnum());
        entity.setSortOrder(request.getSortOrder());
        entity.setRemark(request.getRemark());
        return entity;
    }
}
