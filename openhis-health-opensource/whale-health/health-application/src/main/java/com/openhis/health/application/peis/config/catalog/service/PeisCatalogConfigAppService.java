/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.config.catalog.service;

import com.openhis.health.application.peis.config.catalog.dto.PeisActivityDto;
import com.openhis.health.application.peis.config.catalog.dto.PeisObservationDefDto;
import com.openhis.health.application.peis.config.catalog.dto.PeisPackageDto;
import com.openhis.health.application.peis.config.catalog.mapper.PeisCatalogMapper;
import com.openhis.health.application.peis.config.catalog.request.DeleteObservationDefRequest;
import com.openhis.health.application.peis.config.catalog.request.SaveActivityRequest;
import com.openhis.health.application.peis.config.catalog.request.SaveObservationDefRequest;
import com.openhis.health.application.peis.config.catalog.request.SavePackageRequest;
import com.openhis.health.application.peis.config.catalog.request.SearchCatalogRequest;
import com.openhis.health.application.peis.config.catalog.response.SearchActivityResponse;
import com.openhis.health.application.peis.config.catalog.response.SearchObservationDefResponse;
import com.openhis.health.application.peis.config.catalog.response.SearchPackageResponse;
import com.openhis.health.application.shared.pagination.PageQueryBinder;
import com.openhis.health.application.shared.pagination.PagedResultBuilder;
import com.openhis.health.application.shared.validator.CurrentPractitionerResolver;
import com.openhis.health.domain.clinical.observationdefinition.command.DeleteObservationDefinitionCommand;
import com.openhis.health.domain.clinical.observationdefinition.entity.ObservationDefinition;
import com.openhis.health.domain.clinical.observationdefinition.queries.ObservationDefinitionQuery;
import com.openhis.health.domain.clinical.observationdefinition.queries.model.ObservationDefinitionModel;
import com.openhis.health.domain.clinical.observationdefinition.service.ObservationDefinitionDomainService;
import com.openhis.health.domain.clinical.observationdefinition.service.ObservationDefinitionQueryService;
import com.openhis.health.domain.custom.activityobservationlink.queries.model.ActivityObservationLinkModel;
import com.openhis.health.domain.custom.activityobservationlink.service.ActivityObservationLinkDomainService;
import com.openhis.health.domain.custom.activityobservationlink.service.ActivityObservationLinkQueryService;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.ObservationDefinitionConstants;
import com.openhis.health.domain.share.constants.PeisCatalogConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.workflow.activitydefinition.command.ActivateActivityDefinitionsCommand;
import com.openhis.health.domain.workflow.activitydefinition.command.CreateActivityDefinitionCommand;
import com.openhis.health.domain.workflow.activitydefinition.command.RetireActivityDefinitionsCommand;
import com.openhis.health.domain.workflow.activitydefinition.command.UpdateActivityDefinitionCommand;
import com.openhis.health.domain.workflow.activitydefinition.queries.ActivityDefinitionPageQuery;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionChildQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionDetailQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.queries.model.ActivityDefinitionPageQueryModel;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionDomainService;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.whale.core.application.exception.ValidationException;
import com.openhis.whale.core.application.util.StringUtils;
import com.openhis.whale.core.infrastructure.multitenancy.TenantUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 体检目录配置应用服务（AS-PEIS-CFG-001）。
 *
 * <p>编排小项 OD、项目 AD、套餐 AD 及项目↔小项绑定维护。
 * 执行科室走基础数据 {@code DepartmentAppService}；项目类型为 {@code ActivityType} 枚举，
 * 在保存体检项目时选择，不在本 AS 单独维护。</p>
 */
@Service
@RequiredArgsConstructor
public class PeisCatalogConfigAppService {

    private final ObservationDefinitionDomainService observationDefinitionDomainService;
    private final ObservationDefinitionQueryService observationDefinitionQueryService;
    private final ActivityDefinitionDomainService activityDefinitionDomainService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final ActivityObservationLinkDomainService activityObservationLinkDomainService;
    private final ActivityObservationLinkQueryService activityObservationLinkQueryService;
    private final PeisCatalogMapper peisCatalogMapper;
    private final CurrentPractitionerResolver currentPractitionerResolver;

    /**
     * 分页检索体检小项（CFG-03）。
     */
    public SearchObservationDefResponse searchObservationDef(SearchCatalogRequest request) {
        // Gate：请求体缺省时补默认分页
        SearchCatalogRequest query = ensureCatalogRequest(request);
        // Pre：组装 OD 查询条件
        ObservationDefinitionQuery pageQuery = new ObservationDefinitionQuery();
        pageQuery.setTenantId(TenantUtils.getCurrentTenantId());
        if (StringUtils.isNotEmpty(query.getKeyword())) {
            pageQuery.setKeyword(query.getKeyword().trim());
        }
        if (StringUtils.isNotEmpty(query.getCategoryCode())) {
            pageQuery.setCategoryCode(query.getCategoryCode().trim());
        }
        if (StringUtils.isNotEmpty(query.getStatusEnum())) {
            pageQuery.setStatusEnum(query.getStatusEnum().trim());
        }
        Sort sort = Sort.by(Sort.Direction.ASC, FieldNameConstants.CODE);
        Page<ObservationDefinitionModel> page = observationDefinitionQueryService.search(
            pageQuery,
            PageRequest.of(query.springPageIndex(), resolvePageSize(query), sort));
        List<PeisObservationDefDto> content = new ArrayList<>();
        for (ObservationDefinitionModel row : page.getContent()) {
            content.add(peisCatalogMapper.toPeisObservationDefDto(row));
        }
        // Asm：装配分页响应
        return PagedResultBuilder.build(new SearchObservationDefResponse(), content, page);
    }

    /**
     * 保存体检小项（CFG-03）。
     */
    public PeisObservationDefDto saveObservationDef(SaveObservationDefRequest request) {
        // Gate：必填字段校验
        validateSaveObservationDefRequest(request);
        // Cmd：领域保存 OD
        ObservationDefinition saved = observationDefinitionDomainService.save(
            peisCatalogMapper.toSaveObservationDefCommand(request, TenantUtils.getCurrentTenantId()));
        // Pre：回读读模型
        ObservationDefinitionModel model = observationDefinitionQueryService.getById(saved.getId());
        // Asm：映射 DTO
        PeisObservationDefDto dto = peisCatalogMapper.toPeisObservationDefDto(model);
        dto.setOrgId(saved.getOrgId());
        return dto;
    }

    /**
     * 软删体检小项（CFG-03；写 {@code is_deleted}，列表默认不再返回）。
     *
     * @param request 含小项主键
     */
    public void deleteObservationDef(DeleteObservationDefRequest request) {
        // Gate：主键必填
        if (request == null || request.getDefinitionId() == null) {
            throw new ValidationException("删除体检小项失败：小项主键不能为空");
        }
        // Gate：写路径操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.OBSERVATION_DEFINITION_INVALID_ARGUMENT, "请先登录后再删除体检小项");
        // Cmd：领域软删
        DeleteObservationDefinitionCommand command = new DeleteObservationDefinitionCommand();
        command.setId(request.getDefinitionId());
        command.setOperatorId(operatorId);
        observationDefinitionDomainService.delete(command);
    }

    /**
     * 分页检索体检项目（CFG-04；category=PEIS，childrenFlag=false）。
     */
    public SearchActivityResponse searchActivity(SearchCatalogRequest request) {
        // Gate：请求体缺省时补默认分页
        SearchCatalogRequest query = ensureCatalogRequest(request);
        // Pre：组装 AD 分页查询
        ActivityDefinitionPageQuery pageQuery = buildPeisActivityPageQuery(query, Boolean.FALSE);
        Sort sort = Sort.by(Sort.Direction.DESC, FieldNameConstants.BUS_NO);
        Page<ActivityDefinitionPageQueryModel> page = activityDefinitionQueryService.pageServiceCatalog(
            pageQuery,
            PageRequest.of(query.springPageIndex(), resolvePageSize(query), sort));
        Map<Long, List<Long>> odIdsByActivityId = loadObservationDefinitionIds(page.getContent());
        List<PeisActivityDto> content = new ArrayList<>();
        for (ActivityDefinitionPageQueryModel row : page.getContent()) {
            List<Long> odIds = odIdsByActivityId.get(row.getId());
            if (odIds == null) {
                odIds = Collections.emptyList();
            }
            content.add(peisCatalogMapper.toPeisActivityDto(row, odIds));
        }
        // Asm：装配分页响应
        return PagedResultBuilder.build(new SearchActivityResponse(), content, page);
    }

    /**
     * 保存体检项目并同步小项链接（CFG-04）。
     */
    public PeisActivityDto saveActivity(SaveActivityRequest request) {
        // Gate：必填字段校验
        validateSaveActivityRequest(request);
        // Gate：写路径操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "请先登录后再保存体检项目");
        Long activityId;
        if (request.getActivityId() == null) {
            // Cmd：领域新建 AD
            CreateActivityDefinitionCommand createCommand =
                peisCatalogMapper.toCreateActivityCommand(request, operatorId);
            activityId = activityDefinitionDomainService.createActivityDefinition(createCommand);
        } else {
            activityId = request.getActivityId();
            // Cmd：领域更新 AD
            UpdateActivityDefinitionCommand updateCommand =
                peisCatalogMapper.toUpdateActivityCommand(request, operatorId);
            activityDefinitionDomainService.updateActivityDefinition(updateCommand);
        }
        // Cmd 续：同步项目↔小项绑定
        activityObservationLinkDomainService.saveLinks(
            peisCatalogMapper.toSaveLinksCommand(
                activityId,
                request.getObservationDefinitionIds(),
                TenantUtils.getCurrentTenantId(),
                request.getOrgId(),
                operatorId));
        // Cmd 续：按状态启停
        applyActivityStatus(activityId, request.getStatusEnum());
        // Pre：回读列表行并 enrich 小项 id
        ActivityDefinitionPageQueryModel row = loadActivityPageRow(activityId);
        List<Long> odIds = listObservationDefinitionIds(activityId);
        // Asm：映射 DTO
        return peisCatalogMapper.toPeisActivityDto(row, odIds);
    }

    /**
     * 分页检索体检套餐（CFG-05；category=PEIS，childrenFlag=true）。
     */
    public SearchPackageResponse searchPackage(SearchCatalogRequest request) {
        // Gate：请求体缺省时补默认分页
        SearchCatalogRequest query = ensureCatalogRequest(request);
        // Pre：组装套餐分页查询
        ActivityDefinitionPageQuery pageQuery = buildPeisActivityPageQuery(query, Boolean.TRUE);
        Sort sort = Sort.by(Sort.Direction.DESC, FieldNameConstants.BUS_NO);
        Page<ActivityDefinitionPageQueryModel> page = activityDefinitionQueryService.pageServiceCatalog(
            pageQuery,
            PageRequest.of(query.springPageIndex(), resolvePageSize(query), sort));
        Map<Long, List<Long>> childIdsByPackageId = loadChildActivityIds(page.getContent());
        List<PeisPackageDto> content = new ArrayList<>();
        for (ActivityDefinitionPageQueryModel row : page.getContent()) {
            List<Long> childIds = childIdsByPackageId.get(row.getId());
            if (childIds == null) {
                childIds = Collections.emptyList();
            }
            content.add(peisCatalogMapper.toPeisPackageDto(row, childIds));
        }
        // Asm：装配分页响应
        return PagedResultBuilder.build(new SearchPackageResponse(), content, page);
    }

    /**
     * 保存体检套餐（CFG-05）。
     */
    public PeisPackageDto savePackage(SavePackageRequest request) {
        // Gate：必填字段校验
        validateSavePackageRequest(request);
        // Gate：写路径操作人
        Long operatorId = currentPractitionerResolver.requireOperatorId(
            ErrorCodeConstants.ACTIVITY_DEFINITION_INVALID_ARGUMENT, "请先登录后再保存体检套餐");
        Long packageId;
        if (request.getPackageActivityId() == null) {
            // Cmd：领域新建套餐 AD
            CreateActivityDefinitionCommand createCommand =
                peisCatalogMapper.toCreatePackageCommand(request, operatorId);
            packageId = activityDefinitionDomainService.createActivityDefinition(createCommand);
        } else {
            packageId = request.getPackageActivityId();
            // Cmd：领域更新套餐 AD
            UpdateActivityDefinitionCommand updateCommand =
                peisCatalogMapper.toUpdatePackageCommand(request, operatorId);
            activityDefinitionDomainService.updateActivityDefinition(updateCommand);
        }
        // Cmd 续：按状态启停
        applyActivityStatus(packageId, request.getStatusEnum());
        // Pre：回读列表行与子项
        ActivityDefinitionPageQueryModel row = loadActivityPageRow(packageId);
        List<Long> childIds = listChildActivityIds(packageId);
        // Asm：映射 DTO
        return peisCatalogMapper.toPeisPackageDto(row, childIds);
    }

    private SearchCatalogRequest ensureCatalogRequest(SearchCatalogRequest request) {
        if (request != null) {
            return request;
        }
        SearchCatalogRequest defaults = new SearchCatalogRequest();
        defaults.setPageNo(1);
        defaults.setPageSize(50);
        return defaults;
    }

    private int resolvePageSize(SearchCatalogRequest request) {
        return request.getBoundedPageSize();
    }

    private ActivityDefinitionPageQuery buildPeisActivityPageQuery(
            SearchCatalogRequest request, Boolean childrenFlag) {
        ActivityDefinitionPageQuery pageQuery = new ActivityDefinitionPageQuery();
        PageQueryBinder.bind(request, pageQuery);
        pageQuery.setCategoryCode(PeisCatalogConstants.ACTIVITY_CATEGORY_PEIS);
        pageQuery.setChildrenFlag(childrenFlag);
        // 体检配置同目录维护：须能选到可组包的明细子项
        pageQuery.setIncludeReferencedPackageChildren(Boolean.TRUE);
        if (StringUtils.isNotEmpty(request.getTypeEnum())) {
            pageQuery.setTypeEnum(request.getTypeEnum().trim());
        }
        if (StringUtils.isNotEmpty(request.getStatusEnum())) {
            pageQuery.setStatusEnum(toPublicationStatus(request.getStatusEnum()));
        }
        return pageQuery;
    }

    private void applyActivityStatus(Long activityId, String peisStatusEnum) {
        if (activityId == null || StringUtils.isEmpty(peisStatusEnum)) {
            return;
        }
        String normalized = peisStatusEnum.trim();
        if (ObservationDefinitionConstants.STATUS_ACTIVE.equalsIgnoreCase(normalized)
            || PublicationStatus.ACTIVE.getCode().equalsIgnoreCase(normalized)) {
            ActivateActivityDefinitionsCommand command = new ActivateActivityDefinitionsCommand();
            ActivateActivityDefinitionsCommand.ActivateActivityDefinitionsItemCommand item =
                new ActivateActivityDefinitionsCommand.ActivateActivityDefinitionsItemCommand();
            item.setIdList(List.of(activityId));
            command.setItem(item);
            activityDefinitionDomainService.activateActivityDefinitions(command);
            return;
        }
        if (ObservationDefinitionConstants.STATUS_INACTIVE.equalsIgnoreCase(normalized)
            || PublicationStatus.INACTIVE.getCode().equalsIgnoreCase(normalized)) {
            RetireActivityDefinitionsCommand command = new RetireActivityDefinitionsCommand();
            RetireActivityDefinitionsCommand.RetireActivityDefinitionsItemCommand item =
                new RetireActivityDefinitionsCommand.RetireActivityDefinitionsItemCommand();
            item.setIdList(List.of(activityId));
            command.setItem(item);
            activityDefinitionDomainService.retireActivityDefinitions(command);
        }
    }

    private Map<Long, List<Long>> loadObservationDefinitionIds(List<ActivityDefinitionPageQueryModel> rows) {
        Map<Long, List<Long>> result = new HashMap<>();
        if (rows == null) {
            return result;
        }
        for (ActivityDefinitionPageQueryModel row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            result.put(row.getId(), listObservationDefinitionIds(row.getId()));
        }
        return result;
    }

    private List<Long> listObservationDefinitionIds(Long activityId) {
        List<Long> odIds = new ArrayList<>();
        List<ActivityObservationLinkModel> links = activityObservationLinkQueryService.listByActivityId(activityId);
        for (ActivityObservationLinkModel link : links) {
            if (link == null || link.getObservationDefinitionId() == null) {
                continue;
            }
            odIds.add(link.getObservationDefinitionId());
        }
        return odIds;
    }

    private Map<Long, List<Long>> loadChildActivityIds(List<ActivityDefinitionPageQueryModel> rows) {
        Map<Long, List<Long>> result = new HashMap<>();
        if (rows == null) {
            return result;
        }
        for (ActivityDefinitionPageQueryModel row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            result.put(row.getId(), listChildActivityIds(row.getId()));
        }
        return result;
    }

    private List<Long> listChildActivityIds(Long packageActivityId) {
        List<Long> childIds = new ArrayList<>();
        ActivityDefinitionDetailQueryModel detail =
            activityDefinitionQueryService.getServiceCatalog(packageActivityId);
        if (detail == null || detail.getChildren() == null) {
            return childIds;
        }
        for (ActivityDefinitionChildQueryModel child : detail.getChildren()) {
            if (child == null || child.getChildrenId() == null) {
                continue;
            }
            childIds.add(child.getChildrenId());
        }
        return childIds;
    }

    private ActivityDefinitionPageQueryModel loadActivityPageRow(Long activityId) {
        ActivityDefinitionDetailQueryModel detail = activityDefinitionQueryService.getServiceCatalog(activityId);
        ActivityDefinitionPageQueryModel row = new ActivityDefinitionPageQueryModel();
        if (detail == null || detail.getBaseInfo() == null) {
            row.setId(activityId);
            return row;
        }
        row.setId(detail.getBaseInfo().getId());
        row.setBusNo(detail.getBaseInfo().getBusNo());
        row.setName(detail.getBaseInfo().getName());
        row.setTypeEnum(detail.getBaseInfo().getTypeEnum());
        row.setStatusEnum(detail.getBaseInfo().getStatusEnum());
        row.setCategoryCode(detail.getBaseInfo().getCategoryCode());
        row.setChildrenFlag(detail.getBaseInfo().getChildrenFlag());
        return row;
    }

    private String toPublicationStatus(String peisStatusEnum) {
        if (StringUtils.isEmpty(peisStatusEnum)) {
            return null;
        }
        String normalized = peisStatusEnum.trim();
        if (ObservationDefinitionConstants.STATUS_ACTIVE.equalsIgnoreCase(normalized)) {
            return PublicationStatus.ACTIVE.getCode();
        }
        if (ObservationDefinitionConstants.STATUS_INACTIVE.equalsIgnoreCase(normalized)) {
            return PublicationStatus.INACTIVE.getCode();
        }
        return normalized.toUpperCase();
    }

    private void validateSaveObservationDefRequest(SaveObservationDefRequest request) {
        if (request == null) {
            throw new ValidationException("保存体检小项失败：请求不能为空");
        }
        if (StringUtils.isEmpty(request.getCode())) {
            throw new ValidationException("保存体检小项失败：编码不能为空");
        }
        if (StringUtils.isEmpty(request.getDisplay())) {
            throw new ValidationException("保存体检小项失败：名称不能为空");
        }
        if (StringUtils.isEmpty(request.getValueTypeEnum())) {
            throw new ValidationException("保存体检小项失败：值类型不能为空");
        }
        if (StringUtils.isEmpty(request.getCategoryCode())) {
            throw new ValidationException("保存体检小项失败：分类不能为空");
        }
        if (StringUtils.isEmpty(request.getStatusEnum())) {
            throw new ValidationException("保存体检小项失败：状态不能为空");
        }
    }

    private void validateSaveActivityRequest(SaveActivityRequest request) {
        if (request == null) {
            throw new ValidationException("保存体检项目失败：请求不能为空");
        }
        if (StringUtils.isEmpty(request.getDisplay())) {
            throw new ValidationException("保存体检项目失败：名称不能为空");
        }
        if (StringUtils.isEmpty(request.getTypeEnum())) {
            throw new ValidationException("保存体检项目失败：项目类型不能为空");
        }
        if (StringUtils.isEmpty(request.getStatusEnum())) {
            throw new ValidationException("保存体检项目失败：状态不能为空");
        }
    }

    private void validateSavePackageRequest(SavePackageRequest request) {
        if (request == null) {
            throw new ValidationException("保存体检套餐失败：请求不能为空");
        }
        if (StringUtils.isEmpty(request.getDisplay())) {
            throw new ValidationException("保存体检套餐失败：名称不能为空");
        }
        if (request.getChildActivityIds() == null || request.getChildActivityIds().isEmpty()) {
            throw new ValidationException("保存体检套餐失败：子项目不能为空");
        }
        if (StringUtils.isEmpty(request.getStatusEnum())) {
            throw new ValidationException("保存体检套餐失败：状态不能为空");
        }
    }
}
