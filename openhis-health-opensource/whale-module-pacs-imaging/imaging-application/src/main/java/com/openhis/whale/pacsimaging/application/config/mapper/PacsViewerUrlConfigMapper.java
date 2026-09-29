/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.pacsimaging.application.config.mapper;

import com.openhis.whale.pacsimaging.application.config.dto.ViewerEndpointRowDto;
import com.openhis.whale.pacsimaging.application.config.response.ResolveViewerEndpointResponse;
import com.openhis.whale.pacsimaging.application.config.response.SaveViewerEndpointResponse;
import com.openhis.whale.pacsimaging.domain.viewerendpoint.entity.ViewerEndpoint;
import com.openhis.whale.pacsimaging.domain.viewerendpoint.queries.model.ViewerEndpointQueryModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 阅片端点 DTO 手工装配（非 MapStruct）。
 */
@Component
public class PacsViewerUrlConfigMapper {

    public ViewerEndpointRowDto toRowDto(ViewerEndpointQueryModel model) {
        ViewerEndpointRowDto dto = new ViewerEndpointRowDto();
        if (model == null) {
            return dto;
        }
        dto.setId(model.getId());
        dto.setOrgId(model.getOrganizationId());
        dto.setModalityCode(model.getModalityCode());
        dto.setBaseUrl(model.getBaseUrl());
        dto.setEnabled(model.getEnabled());
        return dto;
    }

    public List<ViewerEndpointRowDto> toRowDtos(List<ViewerEndpointQueryModel> models) {
        List<ViewerEndpointRowDto> rows = new ArrayList<>();
        if (models == null) {
            return rows;
        }
        for (ViewerEndpointQueryModel model : models) {
            rows.add(this.toRowDto(model));
        }
        return rows;
    }

    public SaveViewerEndpointResponse toSaveResponse(ViewerEndpoint entity) {
        SaveViewerEndpointResponse resp = new SaveViewerEndpointResponse();
        if (entity == null) {
            return resp;
        }
        resp.setId(entity.getId());
        resp.setOrgId(entity.getOrganizationId());
        resp.setModalityCode(entity.getModalityCode());
        resp.setBaseUrl(entity.getBaseUrl());
        resp.setEnabled(entity.getEnabled());
        return resp;
    }

    public ResolveViewerEndpointResponse toResolveResponse(ViewerEndpointQueryModel model) {
        ResolveViewerEndpointResponse resp = new ResolveViewerEndpointResponse();
        if (model == null) {
            return resp;
        }
        resp.setBaseUrl(model.getBaseUrl());
        resp.setMatchedBy(model.getMatchedBy());
        return resp;
    }
}
