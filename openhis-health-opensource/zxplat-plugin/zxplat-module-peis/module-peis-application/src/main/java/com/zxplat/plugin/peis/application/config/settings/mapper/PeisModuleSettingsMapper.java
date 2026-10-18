/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.config.settings.mapper;

import com.zxplat.plugin.peis.application.config.settings.dto.PeisConfigDto;
import com.zxplat.plugin.peis.application.config.settings.request.SaveConfigRequest;
import com.zxplat.plugin.peis.domain.peisconfig.command.SavePeisConfigCommand;
import com.zxplat.plugin.peis.domain.peisconfig.entity.PeisConfig;
import com.zxplat.plugin.peis.domain.peisconfig.queries.model.PeisConfigQueryModel;

/**
 * PEIS 模块参数 DTO/命令映射（AS-PEIS-CFG-003）。
 */
public interface PeisModuleSettingsMapper {

    PeisConfigDto toDto(PeisConfigQueryModel source);

    PeisConfigDto toDto(PeisConfig entity);

    SavePeisConfigCommand toSaveCommand(SaveConfigRequest request, Long tenantId);
}
