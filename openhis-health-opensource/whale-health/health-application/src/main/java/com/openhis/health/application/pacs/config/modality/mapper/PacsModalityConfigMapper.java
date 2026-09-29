/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.pacs.config.modality.mapper;

import com.openhis.health.application.pacs.config.modality.dto.PacsCapabilityRowDto;
import com.openhis.health.application.pacs.config.modality.response.SaveCapabilityResponse;
import com.openhis.health.domain.pacs.workitem.entity.PacsModalityCapability;
import com.openhis.health.domain.pacs.workitem.queries.model.PacsModalityCapabilityQueryModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 模态能力 DTO 装配。
 */
@Component
public class PacsModalityConfigMapper {

    public PacsCapabilityRowDto toRowDto(PacsModalityCapabilityQueryModel model) {
        PacsCapabilityRowDto dto = new PacsCapabilityRowDto();
        if (model == null) {
            return dto;
        }
        dto.setId(model.getId());
        dto.setActivityId(model.getActivityId());
        dto.setModalityCode(model.getModalityCode());
        dto.setEnabledFlag(model.getEnabledFlag());
        dto.setOrganizationId(model.getOrganizationId());
        dto.setUpdatedAt(model.getUpdatedAt());
        return dto;
    }

    public List<PacsCapabilityRowDto> toRowDtos(List<PacsModalityCapabilityQueryModel> models) {
        List<PacsCapabilityRowDto> rows = new ArrayList<>();
        if (models == null) {
            return rows;
        }
        for (PacsModalityCapabilityQueryModel model : models) {
            rows.add(this.toRowDto(model));
        }
        return rows;
    }

    public SaveCapabilityResponse toSaveResponse(PacsModalityCapability entity) {
        SaveCapabilityResponse resp = new SaveCapabilityResponse();
        if (entity == null) {
            return resp;
        }
        resp.setId(entity.getId());
        resp.setActivityId(entity.getActivityId());
        resp.setModalityCode(entity.getModalityCode());
        resp.setEnabledFlag(entity.getEnabledFlag());
        return resp;
    }
}
