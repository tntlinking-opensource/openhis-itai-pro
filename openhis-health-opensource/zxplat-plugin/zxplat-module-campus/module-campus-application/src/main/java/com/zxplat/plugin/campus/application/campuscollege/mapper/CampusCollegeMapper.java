/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.campus.application.campuscollege.mapper;

import com.zxplat.plugin.campus.application.campuscollege.request.CreateCollegeRequest;
import com.zxplat.plugin.campus.application.campuscollege.request.UpdateCollegeRequest;
import com.zxplat.plugin.campus.application.campuscollege.response.CampusCollegeDto;
import com.zxplat.plugin.campus.domain.campuscollege.entity.CampusCollege;
import com.zxplat.plugin.campus.domain.campussite.entity.CampusSite;
import com.zxplat.plugin.campus.domain.org.view.CampusCollegeView;
import org.springframework.stereotype.Component;

@Component
public class CampusCollegeMapper {

    public CampusCollegeDto toDto(CampusCollege entity, CampusSite siteOrNull) {
        CampusCollegeDto dto = new CampusCollegeDto();
        dto.setId(entity.getId());
        dto.setCollegeCode(entity.getCollegeCode());
        dto.setCollegeName(entity.getCollegeName());
        dto.setExternalCollegeId(entity.getExternalCollegeId());
        dto.setCampusSiteId(entity.getCampusSiteId());
        dto.setActiveFlag(entity.getActiveFlag());
        dto.setSortOrder(entity.getSortOrder());
        dto.setRemark(entity.getRemark());
        if (siteOrNull != null) {
            dto.setCampusCode(siteOrNull.getCampusCode());
            dto.setCampusName(siteOrNull.getCampusName());
        }
        return dto;
    }

    public CampusCollegeView toView(CampusCollege entity, CampusSite siteOrNull) {
        CampusCollegeView view = new CampusCollegeView();
        view.setId(entity.getId());
        view.setCollegeCode(entity.getCollegeCode());
        view.setCollegeName(entity.getCollegeName());
        view.setCampusSiteId(entity.getCampusSiteId());
        if (siteOrNull != null) {
            view.setCampusCode(siteOrNull.getCampusCode());
        }
        return view;
    }

    public CampusCollege toEntity(CreateCollegeRequest request) {
        CampusCollege entity = new CampusCollege();
        entity.setTenantId(request.getTenantId());
        entity.setCollegeCode(request.getCollegeCode());
        entity.setCollegeName(request.getCollegeName());
        entity.setExternalCollegeId(request.getExternalCollegeId());
        entity.setCampusSiteId(request.getCampusSiteId());
        entity.setActiveFlag(request.getActiveFlag());
        entity.setSortOrder(request.getSortOrder());
        entity.setRemark(request.getRemark());
        return entity;
    }

    public CampusCollege toEntity(UpdateCollegeRequest request) {
        CampusCollege entity = new CampusCollege();
        entity.setTenantId(request.getTenantId());
        entity.setId(request.getId());
        entity.setCollegeName(request.getCollegeName());
        entity.setExternalCollegeId(request.getExternalCollegeId());
        entity.setSortOrder(request.getSortOrder());
        entity.setRemark(request.getRemark());
        return entity;
    }
}
