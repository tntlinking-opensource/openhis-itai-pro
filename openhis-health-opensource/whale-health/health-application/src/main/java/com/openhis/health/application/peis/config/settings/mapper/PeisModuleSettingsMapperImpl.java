/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.config.settings.mapper;

import com.openhis.health.application.peis.config.settings.dto.PeisConfigDto;
import com.openhis.health.application.peis.config.settings.request.SaveConfigRequest;
import com.openhis.health.domain.peis.peisconfig.command.SavePeisConfigCommand;
import com.openhis.health.domain.peis.peisconfig.entity.PeisConfig;
import com.openhis.health.domain.peis.peisconfig.queries.model.PeisConfigQueryModel;
import org.springframework.stereotype.Component;

/**
 * {@link PeisModuleSettingsMapper} 默认实现（AS-PEIS-CFG-003）。
 */
@Component
public class PeisModuleSettingsMapperImpl implements PeisModuleSettingsMapper {

    @Override
    public PeisConfigDto toDto(PeisConfigQueryModel source) {
        if (source == null) {
            return null;
        }
        PeisConfigDto dto = new PeisConfigDto();
        dto.setId(source.getId());
        dto.setConfigKey(source.getConfigKey());
        dto.setConfigValue(source.getConfigValue());
        dto.setOrgId(source.getOrgId());
        return dto;
    }

    @Override
    public PeisConfigDto toDto(PeisConfig entity) {
        if (entity == null) {
            return null;
        }
        PeisConfigDto dto = new PeisConfigDto();
        dto.setId(entity.getId());
        dto.setConfigKey(entity.getConfigKey());
        dto.setConfigValue(entity.getConfigValue());
        dto.setOrgId(entity.getOrgId());
        return dto;
    }

    @Override
    public SavePeisConfigCommand toSaveCommand(SaveConfigRequest request, Long tenantId) {
        SavePeisConfigCommand command = new SavePeisConfigCommand();
        command.setTenantId(tenantId);
        command.setConfigKey(request.getConfigKey());
        command.setConfigValue(request.getConfigValue());
        return command;
    }
}
