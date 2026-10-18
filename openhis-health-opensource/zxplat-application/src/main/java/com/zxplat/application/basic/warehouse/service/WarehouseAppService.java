/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.warehouse.service;

import com.zxplat.application.basic.warehouse.dto.WarehouseDto;
import com.zxplat.application.basic.warehouse.dto.WarehouseInitDto;
import com.zxplat.application.basic.warehouse.mapper.WarehouseMapper;
import com.zxplat.application.basic.warehouse.request.CreateWarehouseRequest;
import com.zxplat.application.basic.warehouse.request.ListWarehouseRequest;
import com.zxplat.application.basic.warehouse.request.QueryWarehouseRequest;
import com.zxplat.application.basic.warehouse.request.UpdateWarehouseRequest;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.location.service.LocationDomainService;
import com.zxplat.domain.administration.location.service.LocationQueryService;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.organization.service.OrganizationQueryService;
import com.zxplat.domain.share.enums.LocationForm;
import com.zxplat.domain.share.enums.LocationStatus;
import com.zxplat.domain.terminology.service.CodeSystemQueryService;
import com.zxplat.framework.core.application.dto.OptionDto;
import com.zxplat.framework.core.application.exception.EntityNotFoundException;
import com.zxplat.framework.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 库房/药房应用服务：以 {@link com.zxplat.domain.administration.location.entity.Location} 为承载，
 * 暴露药库、药房与科室药房形态，并完成注册、编辑、启停与废止编排。
 *
 * <p>读侧统一经 {@link LocationQueryService}，写侧经 {@link LocationDomainService}，禁止注入仓储。</p>
 */
@Service
@RequiredArgsConstructor
public class WarehouseAppService {

    /** 位置读侧：库房/药房列表与按 id 查询。 */
    private final LocationQueryService locationQueryService;
    /** 位置写侧：注册、编辑、启停与废止。 */
    private final LocationDomainService locationDomainService;
    /** 组织读侧：初始化机构下拉与创建时默认挂靠机构。 */
    private final OrganizationQueryService organizationQueryService;
    /** 术语读侧：位置状态等初始化下拉。 */
    private final CodeSystemQueryService codeSystemQueryService;
    /** 库房请求/实体与 DTO、领域命令映射。 */
    private final WarehouseMapper warehouseMapper;

    /**
     * 维护页初始化：位置状态字典、上级科室选项、药库/药房/科室药房形态固定选项。
     *
     * @return 初始化 DTO
     */
    public WarehouseInitDto init() {
        // Asm：组装维护页下拉，形态 value 一律为 {@link LocationForm#getCode()}
        WarehouseInitDto dto = new WarehouseInitDto();
        dto.setLocationStatusOptions(codeSystemQueryService.getOptionListByEnum(LocationStatus.class));
        dto.setOrganizationOptions(this.loadDepartmentOrganizationOptions());
        dto.setFormEnumOptions(this.buildFormEnumOptions());
        return dto;
    }

    /**
     * 库房/药房列表（非树，平铺过滤形态后映射；可选关键词检索）。
     *
     * <p>注意：全表扫描模式，数据量大时存在性能风险；生产环境宜加分页或索引过滤。</p>
     *
     * @param request 列表查询请求，可为 null；{@code keyword} 为空时返回全部
     * @return DTO 列表；无匹配数据时返回空列表
     */
    public List<WarehouseDto> getWarehouseList(ListWarehouseRequest request) {
        // Pre：加载库房/药房/科室药房平铺主数据
        List<Location> allLocations = locationQueryService.listAllWarehouseAndPharmacyLocations();
        if (allLocations == null || allLocations.isEmpty()) {
            return new ArrayList<>();
        }
        String keyword = normalizeKeyword(request);
        List<WarehouseDto> rows = allLocations.stream()
            .filter(item -> this.isWarehouseMaintenanceForm(item.getFormEnum()))
            .map(warehouseMapper::toDto)
            .filter(dto -> matchesKeyword(dto, keyword))
            .collect(Collectors.toList());
        // Asm：批量补齐上级科室展示名，避免列表 N+1
        this.enrichOrganizationNames(rows);
        return rows;
    }

    /**
     * 按主键查询单条库房/药房。
     *
     * @param request 含 id 的查询请求
     * @return 库房 DTO
     * @throws ValidationException 请求或主键为空，或该位置非库房维护形态时
     * @throws EntityNotFoundException 位置不存在时
     */
    public WarehouseDto getWarehouse(QueryWarehouseRequest request) {
        // Gate：主键必填，否则无法定位库房点位
        if (request == null || request.getId() == null) {
            throw new ValidationException("库房ID不能为空");
        }
        Optional<Location> entityOptional = locationQueryService.findOptionalById(request.getId());
        if (entityOptional.isEmpty()) {
            throw new EntityNotFoundException("库房", request.getId());
        }
        Location entity = entityOptional.get();
        if (!this.isWarehouseMaintenanceForm(entity.getFormEnum())) {
            throw new ValidationException("该位置不是库房/药房");
        }
        // Asm：映射详情并补齐科室药房的上级科室名
        WarehouseDto dto = warehouseMapper.toDto(entity);
        this.enrichOrganizationNames(List.of(dto));
        return dto;
    }

    /**
     * 新建库房/药房/科室药房，注册位置后回查详情。
     *
     * @param request 创建请求（含形态、名称等必填字段）
     * @return 新建后的 DTO
     * @throws ValidationException 请求体为空、科室药房未选上级科室或无可用的默认机构时
     */
    public WarehouseDto createWarehouse(CreateWarehouseRequest request) {
        // Gate：请求体与挂靠机构须满足形态约束
        if (request == null) {
            throw new ValidationException("创建请求不能为空");
        }
        Long organizationId = this.resolveOrganizationIdForSave(request);
        // Cmd：领域注册位置
        Long locationId = locationDomainService.registerLocation(warehouseMapper.toCreateCommand(request, organizationId));
        // Asm：按新 id 回查详情 DTO
        QueryWarehouseRequest query = new QueryWarehouseRequest();
        query.setId(locationId);
        return getWarehouse(query);
    }

    /**
     * 编辑库房基础信息与形态。
     *
     * @param request 更新请求（须含主键 id）
     * @return 更新后 DTO
     * @throws ValidationException 请求体或主键为空，或科室药房未选上级科室时
     */
    public WarehouseDto editWarehouse(UpdateWarehouseRequest request) {
        // Gate：主键与挂靠机构须满足形态约束
        if (request == null || request.getId() == null) {
            throw new ValidationException("库房ID不能为空");
        }
        Long organizationId = this.resolveOrganizationIdForSave(request);
        // Cmd：领域增量编辑
        locationDomainService.editLocationInfo(warehouseMapper.toUpdateCommand(request, organizationId));
        // Asm：回查详情 DTO
        QueryWarehouseRequest query = new QueryWarehouseRequest();
        query.setId(request.getId());
        return getWarehouse(query);
    }

    /**
     * 批量启用位置（支持单 id 或 ids 列表合并解析）。
     *
     * @param request 查询请求，解析出 id 列表；支持单字段 id 或批量字段 ids
     * @return 固定 true（全部项处理完成后）
     * @throws ValidationException 解析后的 id 列表为空时
     */
    public boolean enableWarehouse(QueryWarehouseRequest request) {
        // Gate：至少一条主键，否则无法下发启用
        List<Long> ids = this.resolveRequestIds(request);
        if (ids.isEmpty()) {
            throw new ValidationException("库房ID列表不能为空");
        }
        // Cmd：逐条校验形态后下发启用（停用态可重新启用，无草稿/树形门禁）
        for (Long id : ids) {
            if (id != null) {
                this.requireWarehouseMaintenanceLocation(id);
                locationDomainService.activateLocation(warehouseMapper.toActivateCommand(id));
            }
        }
        return true;
    }

    /**
     * 批量停用位置（支持单 id 或 ids 列表合并解析）。
     *
     * @param request 查询请求，解析出 id 列表；支持单字段 id 或批量字段 ids
     * @return 固定 true（全部项处理完成后）
     * @throws ValidationException 解析后的 id 列表为空时
     */
    public boolean deactivateWarehouse(QueryWarehouseRequest request) {
        // Gate：至少一条主键，否则无法下发停用
        List<Long> ids = this.resolveRequestIds(request);
        if (ids.isEmpty()) {
            throw new ValidationException("库房ID列表不能为空");
        }
        // Cmd：逐条校验形态后下发停用（扁平节点，无子节点门禁）
        for (Long id : ids) {
            if (id != null) {
                this.requireWarehouseMaintenanceLocation(id);
                locationDomainService.deactivateLocation(warehouseMapper.toDeactivateCommand(id));
            }
        }
        return true;
    }

    /**
     * 废止单条库房位置（领域侧废止命令，逻辑删除语义）。
     *
     * <p>注意：废止后该位置对后续业务不可见，但历史数据保留。</p>
     *
     * @param request 含单 id 的查询请求
     * @throws ValidationException 请求体或主键为空时
     */
    public void deleteWarehouse(QueryWarehouseRequest request) {
        // Gate：单条废止须带主键
        if (request == null || request.getId() == null) {
            throw new ValidationException("库房ID不能为空");
        }
        // Cmd：校验形态后逻辑删除
        this.requireWarehouseMaintenanceLocation(request.getId());
        locationDomainService.abolishLocation(warehouseMapper.toAbolishCommand(request.getId()));
    }

    /**
     * 固定三项：药库、药房、科室药房形态下拉（value 为 {@link LocationForm#getCode()}）。
     *
     * @return 形态选项
     */
    private List<OptionDto> buildFormEnumOptions() {
        List<OptionDto> formEnums = new ArrayList<>();
        formEnums.add(new OptionDto(
            LocationForm.WAREHOUSE.getSortOrder(),
            LocationForm.WAREHOUSE.getCode(),
            LocationForm.WAREHOUSE.getDisplay()));
        formEnums.add(new OptionDto(
            LocationForm.PHARMACY.getSortOrder(),
            LocationForm.PHARMACY.getCode(),
            LocationForm.PHARMACY.getDisplay()));
        formEnums.add(new OptionDto(
            LocationForm.DEPARTMENT_PHARMACY.getSortOrder(),
            LocationForm.DEPARTMENT_PHARMACY.getCode(),
            LocationForm.DEPARTMENT_PHARMACY.getDisplay()));
        return formEnums;
    }

    /**
     * 判断形态编码是否为库房维护页支持的形态（药库、药房、科室药房）。
     *
     * @param formCode 形态编码
     * @return 是否属于库房维护范畴
     */
    private boolean isWarehouseMaintenanceForm(String formCode) {
        return LocationForm.WAREHOUSE.getCode().equals(formCode)
            || LocationForm.PHARMACY.getCode().equals(formCode)
            || LocationForm.DEPARTMENT_PHARMACY.getCode().equals(formCode);
    }

    /**
     * 启停/废止前加载位置并断言为库房维护页支持的形态。
     *
     * @param locationId 位置主键
     * @return 位置实体
     * @throws EntityNotFoundException 位置不存在时
     * @throws ValidationException     非库房维护形态时
     */
    private Location requireWarehouseMaintenanceLocation(Long locationId) {
        Optional<Location> entityOptional = locationQueryService.findOptionalById(locationId);
        if (entityOptional.isEmpty()) {
            throw new EntityNotFoundException("库房", locationId);
        }
        Location entity = entityOptional.get();
        if (!this.isWarehouseMaintenanceForm(entity.getFormEnum())) {
            throw new ValidationException("该位置不是库房/药房");
        }
        return entity;
    }

    /**
     * 解析创建/编辑时的挂靠机构：科室药房取请求中的上级科室，其余形态回落默认机构。
     *
     * @param request 创建或更新请求
     * @return 机构主键
     * @throws ValidationException 科室药房未选上级科室或机构树为空时
     */
    private Long resolveOrganizationIdForSave(CreateWarehouseRequest request) {
        if (LocationForm.DEPARTMENT_PHARMACY.getCode().equals(request.getFormEnum())) {
            if (request.getOrganizationId() == null) {
                throw new ValidationException("科室药房须选择上级科室");
            }
            return request.getOrganizationId();
        }
        return this.resolveDefaultOrganizationId();
    }

    /**
     * 批量补齐列表/详情中的上级科室名称（仅科室药房展示）。
     *
     * @param rows 库房 DTO 列表
     */
    private void enrichOrganizationNames(List<WarehouseDto> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        List<Long> organizationIds = new ArrayList<>();
        for (WarehouseDto row : rows) {
            if (row == null || row.getOrganizationId() == null) {
                continue;
            }
            if (LocationForm.DEPARTMENT_PHARMACY.getCode().equals(row.getFormEnum())) {
                organizationIds.add(row.getOrganizationId());
            }
        }
        if (organizationIds.isEmpty()) {
            return;
        }
        Map<Long, String> nameById = organizationQueryService.mapNamesByIds(organizationIds);
        for (WarehouseDto row : rows) {
            if (row == null || row.getOrganizationId() == null) {
                continue;
            }
            if (!LocationForm.DEPARTMENT_PHARMACY.getCode().equals(row.getFormEnum())) {
                continue;
            }
            row.setOrganizationName(nameById.get(row.getOrganizationId()));
        }
    }

    /**
     * 归一化列表检索关键词：trim 后转小写；空串视为无关键词。
     *
     * @param request 列表请求，可为 null
     * @return 小写关键词；无有效关键词时返回 null
     */
    private String normalizeKeyword(ListWarehouseRequest request) {
        if (request == null || StringUtils.isEmpty(request.getKeyword())) {
            return null;
        }
        return request.getKeyword().trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 判断 DTO 是否命中关键词（名称、编号、拼音、五笔、类型、状态与上级科室展示文案）。
     *
     * @param dto     库房 DTO
     * @param keyword 已归一化的小写关键词；null 表示不过滤
     * @return 是否命中
     */
    private boolean matchesKeyword(WarehouseDto dto, String keyword) {
        if (keyword == null) {
            return true;
        }
        return containsIgnoreCase(dto.getName(), keyword)
            || containsIgnoreCase(dto.getBusNo(), keyword)
            || containsIgnoreCase(dto.getPyStr(), keyword)
            || containsIgnoreCase(dto.getWbStr(), keyword)
            || containsIgnoreCase(dto.getFormEnumText(), keyword)
            || containsIgnoreCase(dto.getStatusEnumText(), keyword)
            || containsIgnoreCase(dto.getOrganizationName(), keyword);
    }

    /**
     * 字段值转小写后是否包含关键词。
     *
     * @param value   原始字段值
     * @param keyword 已归一化的小写关键词
     * @return 是否包含
     */
    private boolean containsIgnoreCase(String value, String keyword) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(keyword);
    }

    /**
     * 将责任科室机构树拍平为下拉选项（科室药房选上级科室）。
     *
     * @return 选项列表；无机构时返回空列表
     */
    private List<OptionDto> loadDepartmentOrganizationOptions() {
        List<Organization> organizations = organizationQueryService.getOrganizationTreeForResponsibilityDept();
        List<OptionDto> options = new ArrayList<>();
        if (organizations == null || organizations.isEmpty()) {
            return options;
        }
        long sort = 0L;
        // Loop：遍历科室机构，过滤无效节点后生成下拉选项
        for (Organization organization : organizations) {
            if (organization == null || organization.getId() == null) {
                continue;
            }
            sort++;
            options.add(new OptionDto(sort, String.valueOf(organization.getId()), organization.getName()));
        }
        return options;
    }

    /**
     * 创建药库/药房时解析默认所属机构：取机构树中第一个有效主键；无则抛校验异常。
     *
     * @return 机构主键
     * @throws ValidationException 机构树为空或无有效节点时
     */
    private Long resolveDefaultOrganizationId() {
        List<Organization> organizations = organizationQueryService.getOrganizationTree();
        // Loop：遍历查找首个有效机构节点
        for (Organization organization : organizations) {
            if (organization != null && organization.getId() != null) {
                return organization.getId();
            }
        }
        throw new ValidationException("缺少可用机构，无法创建库房");
    }

    /**
     * 从启停/废止请求中解析 id 列表。
     *
     * @param request 查询请求，可为 null
     * @return id 列表，永不为 null
     */
    private List<Long> resolveRequestIds(QueryWarehouseRequest request) {
        if (request == null) {
            return List.of();
        }
        return request.resolveIds();
    }
}
