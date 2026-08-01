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

/**
 * PEIS 模块参数 DTO/命令映射（AS-PEIS-CFG-003）。
 */
public interface PeisModuleSettingsMapper {

    PeisConfigDto toDto(PeisConfigQueryModel source);

    PeisConfigDto toDto(PeisConfig entity);

    SavePeisConfigCommand toSaveCommand(SaveConfigRequest request, Long tenantId);
}
