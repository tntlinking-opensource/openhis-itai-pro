/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.config.catalog.mapper;

import com.zxplat.plugin.peis.application.config.catalog.dto.PeisActivityDto;
import com.zxplat.plugin.peis.application.config.catalog.dto.PeisObservationDefDto;
import com.zxplat.plugin.peis.application.config.catalog.dto.PeisPackageDto;
import com.zxplat.plugin.peis.application.config.catalog.request.SaveActivityRequest;
import com.zxplat.plugin.peis.application.config.catalog.request.SaveObservationDefRequest;
import com.zxplat.plugin.peis.application.config.catalog.request.SavePackageRequest;
import com.zxplat.domain.clinical.observationdefinition.command.SaveObservationDefinitionCommand;
import com.zxplat.domain.clinical.observationdefinition.queries.model.ObservationDefinitionModel;
import com.zxplat.domain.custom.activityobservationlink.command.ActivityObservationLinkItemCommand;
import com.zxplat.domain.custom.activityobservationlink.command.SaveActivityObservationLinksCommand;
import com.zxplat.domain.workflow.activitydefinition.command.CreateActivityDefinitionCommand;
import com.zxplat.domain.workflow.activitydefinition.command.UpdateActivityDefinitionCommand;
import com.zxplat.domain.workflow.activitydefinition.queries.model.ActivityDefinitionPageQueryModel;

import java.util.List;

/**
 * PEIS 体检目录 DTO 与领域命令映射（AS-PEIS-CFG-001）。
 */
public interface PeisCatalogMapper {

    PeisObservationDefDto toPeisObservationDefDto(ObservationDefinitionModel source);

    SaveObservationDefinitionCommand toSaveObservationDefCommand(SaveObservationDefRequest request, Long tenantId);

    PeisActivityDto toPeisActivityDto(ActivityDefinitionPageQueryModel source, List<Long> observationDefinitionIds);

    CreateActivityDefinitionCommand toCreateActivityCommand(SaveActivityRequest request, Long operatorId);

    UpdateActivityDefinitionCommand toUpdateActivityCommand(SaveActivityRequest request, Long operatorId);

    SaveActivityObservationLinksCommand toSaveLinksCommand(
        Long activityId, List<Long> observationDefinitionIds, Long tenantId, Long orgId, Long operatorId);

    PeisPackageDto toPeisPackageDto(ActivityDefinitionPageQueryModel source, List<Long> childActivityIds);

    CreateActivityDefinitionCommand toCreatePackageCommand(SavePackageRequest request, Long operatorId);

    UpdateActivityDefinitionCommand toUpdatePackageCommand(SavePackageRequest request, Long operatorId);
}
