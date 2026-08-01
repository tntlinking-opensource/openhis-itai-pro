/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.campus.application.campussite.mapper;

import com.openhis.campus.application.campussite.request.CreateCampusSiteRequest;
import com.openhis.campus.application.campussite.request.UpdateCampusSiteRequest;
import com.openhis.campus.application.campussite.response.CampusSiteDto;
import com.openhis.campus.domain.campussite.entity.CampusSite;
import com.openhis.campus.domain.org.view.CampusSiteView;
import org.springframework.stereotype.Component;

@Component
public class CampusSiteMapper {

    public CampusSiteDto toDto(CampusSite entity) {
        CampusSiteDto dto = new CampusSiteDto();
        dto.setId(entity.getId());
        dto.setCampusCode(entity.getCampusCode());
        dto.setCampusName(entity.getCampusName());
        dto.setShortName(entity.getShortName());
        dto.setAddress(entity.getAddress());
        dto.setIsPrimary(entity.getIsPrimary());
        dto.setActiveFlag(entity.getActiveFlag());
        dto.setSortOrder(entity.getSortOrder());
        dto.setRemark(entity.getRemark());
        return dto;
    }

    public CampusSiteView toView(CampusSite entity) {
        CampusSiteView view = new CampusSiteView();
        view.setId(entity.getId());
        view.setCampusCode(entity.getCampusCode());
        view.setCampusName(entity.getCampusName());
        view.setShortName(entity.getShortName());
        view.setIsPrimary(entity.getIsPrimary());
        return view;
    }

    public CampusSite toEntity(CreateCampusSiteRequest request) {
        CampusSite entity = new CampusSite();
        entity.setTenantId(request.getTenantId());
        entity.setCampusCode(request.getCampusCode());
        entity.setCampusName(request.getCampusName());
        entity.setShortName(request.getShortName());
        entity.setAddress(request.getAddress());
        entity.setIsPrimary(request.getIsPrimary());
        entity.setActiveFlag(request.getActiveFlag());
        entity.setSortOrder(request.getSortOrder());
        entity.setRemark(request.getRemark());
        return entity;
    }

    public CampusSite toEntity(UpdateCampusSiteRequest request) {
        CampusSite entity = new CampusSite();
        entity.setTenantId(request.getTenantId());
        entity.setId(request.getId());
        entity.setCampusName(request.getCampusName());
        entity.setShortName(request.getShortName());
        entity.setAddress(request.getAddress());
        entity.setSortOrder(request.getSortOrder());
        entity.setRemark(request.getRemark());
        return entity;
    }
}
