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
import com.zxplat.domain.share.constants.ObservationDefinitionConstants;
import com.zxplat.domain.share.constants.PeisCatalogConstants;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.workflow.activitydefinition.command.CreateActivityDefinitionCommand;
import com.zxplat.domain.workflow.activitydefinition.command.UpdateActivityDefinitionCommand;
import com.zxplat.domain.workflow.activitydefinition.queries.model.ActivityDefinitionPageQueryModel;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * {@link PeisCatalogMapper} 默认实现（AS-PEIS-CFG-001）。
 */
@Component
public class PeisCatalogMapperImpl implements PeisCatalogMapper {

    /**
     * 小项读模型 → 列表/详情 DTO（科室名与引用次数由 AppService enrich）。
     *
     * @param source 观察定义读模型
     * @return PEIS 小项 DTO；source 为空时返回 null
     */
    @Override
    public PeisObservationDefDto toPeisObservationDefDto(ObservationDefinitionModel source) {
        if (source == null) {
            return null;
        }
        PeisObservationDefDto dto = new PeisObservationDefDto();
        dto.setDefinitionId(source.getId());
        dto.setCode(source.getCode());
        dto.setDisplay(source.getDisplay());
        dto.setValueTypeEnum(source.getValueTypeEnum());
        dto.setCategoryCode(source.getCategoryCode());
        dto.setUnitCode(source.getUnitCode());
        dto.setStatusEnum(source.getStatusEnum());
        // 执行科室主键；名称由 AppService enrich
        dto.setOrgId(source.getOrgId());
        return dto;
    }

    @Override
    public SaveObservationDefinitionCommand toSaveObservationDefCommand(
            SaveObservationDefRequest request, Long tenantId) {
        SaveObservationDefinitionCommand command = new SaveObservationDefinitionCommand();
        command.setId(request.getDefinitionId());
        command.setTenantId(tenantId);
        command.setOrgId(request.getOrgId());
        command.setCode(request.getCode());
        command.setDisplay(request.getDisplay());
        command.setValueTypeEnum(request.getValueTypeEnum());
        command.setCategoryCode(request.getCategoryCode());
        command.setUnitCode(request.getUnitCode());
        command.setStatusEnum(request.getStatusEnum());
        return command;
    }

    /**
     * 项目分页行 + 绑定小项 id → 列表 DTO（科室名与原价由 AppService enrich）。
     *
     * @param source 诊疗定义分页行
     * @param observationDefinitionIds 绑定小项主键（可空）
     * @return PEIS 项目 DTO；source 为空时返回 null
     */
    @Override
    public PeisActivityDto toPeisActivityDto(
            ActivityDefinitionPageQueryModel source, List<Long> observationDefinitionIds) {
        if (source == null) {
            return null;
        }
        PeisActivityDto dto = new PeisActivityDto();
        dto.setActivityId(source.getId());
        dto.setCode(source.getBusNo());
        dto.setDisplay(source.getName());
        dto.setTypeEnum(source.getTypeEnum());
        dto.setStatusEnum(toPeisLifecycleStatus(source.getStatusEnum()));
        dto.setObservationDefinitionIds(observationDefinitionIds);
        // 列表「绑定小项数」列直接用，避免前端再算
        if (observationDefinitionIds == null) {
            dto.setObservationCount(0);
        } else {
            dto.setObservationCount(observationDefinitionIds.size());
        }
        dto.setOrgId(source.getOrgId());
        dto.setPermittedGenderEnum(source.getPermittedGenderEnum());
        dto.setSortOrder(source.getSortOrder());
        dto.setPricingFlag(source.getPricingFlag());
        dto.setPyStr(source.getPyStr());
        return dto;
    }

    @Override
    public CreateActivityDefinitionCommand toCreateActivityCommand(
            SaveActivityRequest request, Long operatorId) {
        CreateActivityDefinitionCommand command = new CreateActivityDefinitionCommand();
        command.setOperatorId(operatorId);
        CreateActivityDefinitionCommand.CreateActivityDefinitionItemCommand item =
            new CreateActivityDefinitionCommand.CreateActivityDefinitionItemCommand();
        item.setCategoryCode(PeisCatalogConstants.ACTIVITY_CATEGORY_PEIS);
        item.setName(request.getDisplay());
        item.setTypeEnum(request.getTypeEnum());
        item.setStatusEnum(toPublicationStatusCode(request.getStatusEnum()));
        item.setOrgId(request.getOrgId());
        item.setChildrenFlag(Boolean.FALSE);
        item.setPermittedGenderEnum(request.getPermittedGenderEnum());
        item.setSortOrder(request.getSortOrder());
        if (StringUtils.isNotEmpty(request.getCode())) {
            item.setDescriptionText(request.getCode());
        }
        command.setItem(item);
        return command;
    }

    @Override
    public UpdateActivityDefinitionCommand toUpdateActivityCommand(
            SaveActivityRequest request, Long operatorId) {
        UpdateActivityDefinitionCommand command = new UpdateActivityDefinitionCommand();
        command.setOperatorId(operatorId);
        UpdateActivityDefinitionCommand.UpdateActivityDefinitionItemCommand item =
            new UpdateActivityDefinitionCommand.UpdateActivityDefinitionItemCommand();
        item.setId(request.getActivityId());
        item.setCategoryCode(PeisCatalogConstants.ACTIVITY_CATEGORY_PEIS);
        item.setName(request.getDisplay());
        item.setTypeEnum(request.getTypeEnum());
        item.setOrgId(request.getOrgId());
        item.setChildrenFlag(Boolean.FALSE);
        item.setPermittedGenderEnum(request.getPermittedGenderEnum());
        item.setSortOrder(request.getSortOrder());
        if (StringUtils.isNotEmpty(request.getCode())) {
            item.setDescriptionText(request.getCode());
        }
        command.setItem(item);
        return command;
    }

    @Override
    public SaveActivityObservationLinksCommand toSaveLinksCommand(
            Long activityId,
            List<Long> observationDefinitionIds,
            Long tenantId,
            Long orgId,
            Long operatorId) {
        SaveActivityObservationLinksCommand command = new SaveActivityObservationLinksCommand();
        command.setActivityDefinitionId(activityId);
        command.setTenantId(tenantId);
        command.setOrgId(orgId);
        command.setOperatorId(operatorId);
        List<ActivityObservationLinkItemCommand> links = new ArrayList<>();
        if (observationDefinitionIds != null) {
            int sortNum = 0;
            for (Long odId : observationDefinitionIds) {
                if (odId == null) {
                    continue;
                }
                sortNum++;
                ActivityObservationLinkItemCommand linkItem = new ActivityObservationLinkItemCommand();
                linkItem.setObservationDefinitionId(odId);
                linkItem.setSortNum(sortNum);
                links.add(linkItem);
            }
        }
        command.setLinks(links);
        return command;
    }

    @Override
    public PeisPackageDto toPeisPackageDto(
            ActivityDefinitionPageQueryModel source, List<Long> childActivityIds) {
        if (source == null) {
            return null;
        }
        PeisPackageDto dto = new PeisPackageDto();
        dto.setPackageActivityId(source.getId());
        dto.setCode(source.getBusNo());
        dto.setDisplay(source.getName());
        // 透传目录业务类型标签，供编辑回显（选项来自术语，不写死）
        dto.setTypeEnum(source.getTypeEnum());
        dto.setStatusEnum(toPeisLifecycleStatus(source.getStatusEnum()));
        dto.setChildActivityIds(childActivityIds);
        return dto;
    }

    @Override
    public CreateActivityDefinitionCommand toCreatePackageCommand(
            SavePackageRequest request, Long operatorId) {
        CreateActivityDefinitionCommand command = new CreateActivityDefinitionCommand();
        command.setOperatorId(operatorId);
        CreateActivityDefinitionCommand.CreateActivityDefinitionItemCommand item =
            new CreateActivityDefinitionCommand.CreateActivityDefinitionItemCommand();
        item.setCategoryCode(PeisCatalogConstants.ACTIVITY_CATEGORY_PEIS);
        item.setName(request.getDisplay());
        // 业务类型由前端术语下拉必填传入，禁止写死码值
        item.setTypeEnum(request.getTypeEnum());
        item.setStatusEnum(toPublicationStatusCode(request.getStatusEnum()));
        item.setChildrenFlag(Boolean.TRUE);
        item.setChildren(buildCreateChildren(request.getChildActivityIds()));
        if (StringUtils.isNotEmpty(request.getCode())) {
            item.setDescriptionText(request.getCode());
        }
        command.setItem(item);
        return command;
    }

    @Override
    public UpdateActivityDefinitionCommand toUpdatePackageCommand(
            SavePackageRequest request, Long operatorId) {
        UpdateActivityDefinitionCommand command = new UpdateActivityDefinitionCommand();
        command.setOperatorId(operatorId);
        UpdateActivityDefinitionCommand.UpdateActivityDefinitionItemCommand item =
            new UpdateActivityDefinitionCommand.UpdateActivityDefinitionItemCommand();
        item.setId(request.getPackageActivityId());
        item.setCategoryCode(PeisCatalogConstants.ACTIVITY_CATEGORY_PEIS);
        item.setName(request.getDisplay());
        // 业务类型由前端术语下拉必填传入，禁止写死码值
        item.setTypeEnum(request.getTypeEnum());
        item.setChildrenFlag(Boolean.TRUE);
        item.setChildren(buildUpdateChildren(request.getChildActivityIds()));
        if (StringUtils.isNotEmpty(request.getCode())) {
            item.setDescriptionText(request.getCode());
        }
        command.setItem(item);
        return command;
    }

    private List<CreateActivityDefinitionCommand.ActivityDefinitionChildItemCommand> buildCreateChildren(
            List<Long> childActivityIds) {
        List<CreateActivityDefinitionCommand.ActivityDefinitionChildItemCommand> children = new ArrayList<>();
        if (childActivityIds == null) {
            return children;
        }
        for (Long childId : childActivityIds) {
            if (childId == null) {
                continue;
            }
            CreateActivityDefinitionCommand.ActivityDefinitionChildItemCommand child =
                new CreateActivityDefinitionCommand.ActivityDefinitionChildItemCommand();
            child.setChildrenId(childId);
            child.setChildrenNum(1);
            children.add(child);
        }
        return children;
    }

    private List<UpdateActivityDefinitionCommand.ActivityDefinitionChildItemCommand> buildUpdateChildren(
            List<Long> childActivityIds) {
        List<UpdateActivityDefinitionCommand.ActivityDefinitionChildItemCommand> children = new ArrayList<>();
        if (childActivityIds == null) {
            return children;
        }
        for (Long childId : childActivityIds) {
            if (childId == null) {
                continue;
            }
            UpdateActivityDefinitionCommand.ActivityDefinitionChildItemCommand child =
                new UpdateActivityDefinitionCommand.ActivityDefinitionChildItemCommand();
            child.setChildrenId(childId);
            child.setChildrenNum(1);
            children.add(child);
        }
        return children;
    }

    private String toPeisLifecycleStatus(String publicationStatusCode) {
        if (PublicationStatus.ACTIVE.getCode().equals(publicationStatusCode)) {
            return ObservationDefinitionConstants.STATUS_ACTIVE;
        }
        if (PublicationStatus.INACTIVE.getCode().equals(publicationStatusCode)) {
            return ObservationDefinitionConstants.STATUS_INACTIVE;
        }
        return publicationStatusCode;
    }

    private String toPublicationStatusCode(String peisStatusEnum) {
        if (StringUtils.isEmpty(peisStatusEnum)) {
            return PublicationStatus.ACTIVE.getCode();
        }
        String normalized = peisStatusEnum.trim();
        if (ObservationDefinitionConstants.STATUS_ACTIVE.equalsIgnoreCase(normalized)
            || PublicationStatus.ACTIVE.getCode().equalsIgnoreCase(normalized)) {
            return PublicationStatus.ACTIVE.getCode();
        }
        if (ObservationDefinitionConstants.STATUS_INACTIVE.equalsIgnoreCase(normalized)
            || PublicationStatus.INACTIVE.getCode().equalsIgnoreCase(normalized)) {
            return PublicationStatus.INACTIVE.getCode();
        }
        return normalized.toUpperCase();
    }
}
