/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.wardbed.service;

import com.zxplat.application.basic.wardbed.dto.WardBedDto;
import com.zxplat.application.basic.wardbed.dto.WardBedInitDto;
import com.zxplat.application.basic.wardbed.mapper.WardBedMapper;
import com.zxplat.application.basic.wardbed.request.CreateWardBedRequest;
import com.zxplat.application.basic.wardbed.request.QueryWardBedRequest;
import com.zxplat.application.basic.wardbed.request.UpdateWardBedRequest;
import com.zxplat.domain.administration.location.command.EditLocationInfoCommand;
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
import lombok.extern.slf4j.Slf4j;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

/**
 * 病区/病房/床位位置应用服务：基于 {@link Location}，编排初始化、列表、登记与启停。
 *
 * <p>读侧统一经 {@link LocationQueryService}，写侧经 {@link LocationDomainService}，禁止注入仓储。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WardBedAppService {

    private final LocationQueryService locationQueryService;
    private final LocationDomainService locationDomainService;
    private final WardBedMapper wardBedMapper;
    private final CodeSystemQueryService codeSystemQueryService;
    private final OrganizationQueryService organizationQueryService;

    /**
     * 维护页初始化：位置状态字典、机构树、形态固定三项及病区/病房候选下拉。
     *
     * @return 初始化 DTO
     */
    public WardBedInitDto init() {
        // Gate：只读页初始化，无写侧操作人
        // Pre：加载位置状态字典、住院科室树、形态枚举与病区/病房候选
        WardBedInitDto dto = new WardBedInitDto();
        dto.setLocationStatusOptions(codeSystemQueryService.getOptionListByEnum(LocationStatus.class));
        dto.setOrganizationOptions(this.loadOrganizationOptions());
        dto.setFormEnums(this.buildFormEnumOptions());
        List<OptionDto> wardList = this.loadOptionsByForm(LocationForm.WARD.getCode());
        List<OptionDto> houseList = this.loadOptionsByForm(LocationForm.HOUSE.getCode());
        dto.setWardOptions(wardList);
        dto.setHouseOptions(houseList);
        log.info("病区床位初始化：{} 个病区，{} 个病房", wardList.size(), houseList.size());
        // Asm：映射维护页初始化 DTO
        return dto;
    }

    /**
     * 病区列表：传机构 id 时查该机构下启用病区；未传时按形态在库内过滤全部病区。
     *
     * @param query {@code id} 为机构主键，可空
     * @return 病区 DTO 列表
     */
    public List<WardBedDto> getWardList(QueryWardBedRequest query) {
        List<Location> wards;
        // 业务分支：传机构 id 时仅查该机构下病区，否则查全院病区
        if (query != null && query.getId() != null) {
            // Pre：按机构与 WARD 形态过滤
            wards = locationQueryService.listByOrganizationIdAndFormCode(
                query.getId(), LocationForm.WARD.getCode());
        } else {
            // Pre：按 WARD 形态全量过滤
            wards = locationQueryService.listAllByFormCode(LocationForm.WARD.getCode());
        }
        // Asm：映射病区 DTO 列表
        return this.mapToDtoList(wards);
    }

    /**
     * 指定病区下的「病房 + 床位」二级树。
     *
     * @param query {@code id} 为病区位置主键，缺失时返回空列表
     * @return 病房 DTO，{@code children} 为床位
     */
    public List<WardBedDto> getHouseAndBedList(QueryWardBedRequest query) {
        // Gate：病区主键缺失时返回空树，避免误查全库
        if (query == null || query.getId() == null) {
            return new ArrayList<>();
        }
        // Pre：加载病区下病房及各自床位
        List<Location> rooms = locationQueryService.listRoomsUnderWard(query.getId());
        List<WardBedDto> houses = new ArrayList<>();
        for (Location room : rooms) {
            WardBedDto houseDto = wardBedMapper.toDto(room);
            List<Location> beds = locationQueryService.listBedsUnderParent(room.getId());
            houseDto.setChildren(this.mapToDtoList(beds));
            houses.add(houseDto);
        }
        // Asm：映射病房+床位二级树
        return houses;
    }

    /**
     * 按主键加载位置，并在存在父级时填充父级快照。
     *
     * @param id 位置主键
     * @return DTO
     */
    public WardBedDto getById(Long id) {
        // Gate：主键非空
        if (id == null) {
            throw new ValidationException("位置ID不能为空");
        }
        // Pre：按 id 加载位置实体
        Location entity = locationQueryService.findOptionalById(id)
            .orElseThrow(() -> new EntityNotFoundException("位置", id));
        // Asm：映射 DTO 并 enrich 父级快照
        WardBedDto dto = wardBedMapper.toDto(entity);
        this.attachParentIfPresent(entity, dto);
        return dto;
    }

    /**
     * 登记病区/病房/床位（领域登记即启用），并按新主键回查。
     *
     * <p>病区可传 {@code organizationIds} 多科挂靠：首项写入主挂靠，全集写入 type_json。</p>
     *
     * @param request 创建请求
     * @return 新建位置 DTO
     */
    public WardBedDto create(CreateWardBedRequest request) {
        // Gate：请求体、形态与挂靠机构必填
        if (request == null) {
            throw new ValidationException("创建请求不能为空");
        }
        if (StringUtils.isEmpty(request.getFormEnum())) {
            throw new ValidationException("位置类型不能为空");
        }
        // Pre：病区多选科室时规范化主挂靠与 organizationIds
        this.normalizeWardOrganizationBinding(request);
        if (request.getOrganizationId() == null) {
            throw new ValidationException("机构不能为空");
        }
        // Cmd：领域登记位置（登记即启用）
        Long createdId = locationDomainService.registerLocation(wardBedMapper.toCreateCommand(request));
        // Asm：按新主键回查完整 DTO
        return this.getById(createdId);
    }

    /**
     * 编辑位置基础信息并回查。
     *
     * <p>病区可改多科挂靠；主挂靠与 type_json 一并更新。</p>
     *
     * @param request 更新请求
     * @return 更新后 DTO
     */
    public WardBedDto edit(UpdateWardBedRequest request) {
        // Gate：主键非空
        if (request == null || request.getId() == null) {
            throw new ValidationException("位置ID不能为空");
        }
        // Pre：病区多选科室时规范化主挂靠与 organizationIds
        this.normalizeWardOrganizationBinding(request);
        // Cmd：领域更新位置基础信息
        EditLocationInfoCommand command = wardBedMapper.toUpdateCommand(request);
        locationDomainService.editLocationInfo(command);
        // Asm：回查最新 DTO
        return this.getById(request.getId());
    }

    /**
     * 病区请求：将 organizationIds 去重保序，首项回写 organizationId（主挂靠）。
     *
     * <p>非病区形态不改写；仅 organizationId 时补成单元素 organizationIds。</p>
     *
     * @param request 创建/更新请求
     */
    private void normalizeWardOrganizationBinding(CreateWardBedRequest request) {
        if (request == null || StringUtils.isEmpty(request.getFormEnum())) {
            return;
        }
        // 仅病区支持多科挂靠
        if (!LocationForm.WARD.getCode().equals(request.getFormEnum().trim())) {
            return;
        }
        LinkedHashSet<Long> ordered = new LinkedHashSet<>();
        if (request.getOrganizationIds() != null) {
            for (Long id : request.getOrganizationIds()) {
                if (id != null) {
                    ordered.add(id);
                }
            }
        }
        if (ordered.isEmpty() && request.getOrganizationId() != null) {
            ordered.add(request.getOrganizationId());
        }
        if (ordered.isEmpty()) {
            return;
        }
        List<Long> ids = new ArrayList<>(ordered);
        request.setOrganizationIds(ids);
        // 主挂靠取多选首项，保证 organization_id NOT NULL 与入院排序一致
        request.setOrganizationId(ids.get(0));
    }

    /**
     * 废止位置。
     *
     * @param id 位置主键
     */
    public void deleteLocation(Long id) {
        // Gate：主键非空
        if (id == null) {
            throw new ValidationException("位置ID不能为空");
        }
        // Cmd：领域废止位置
        locationDomainService.abolishLocation(wardBedMapper.toAbolishCommand(id));
        log.info("废止位置: {}", id);
    }

    /**
     * 启用位置。
     *
     * @param id 位置主键
     * @return 命令已下发时恒为 true
     */
    public boolean enableLocation(Long id) {
        // Gate：主键非空
        if (id == null) {
            throw new ValidationException("位置ID不能为空");
        }
        // Cmd：领域启用位置
        locationDomainService.activateLocation(wardBedMapper.toActivateCommand(id));
        return true;
    }

    /**
     * 停用位置。
     *
     * @param id 位置主键
     * @return 命令已下发时恒为 true
     */
    public boolean deactivateLocation(Long id) {
        // Gate：主键非空
        if (id == null) {
            throw new ValidationException("位置ID不能为空");
        }
        // Cmd：领域停用位置
        locationDomainService.deactivateLocation(wardBedMapper.toDeactivateCommand(id));
        return true;
    }

    /**
     * 住院科室拍平为下拉选项（病区挂靠科室须为类别=住院的启用科室）。
     *
     * @return 选项列表
     */
    private List<OptionDto> loadOrganizationOptions() {
        List<Organization> organizations = organizationQueryService.listOrganizationsForInpatientAdmission();
        List<OptionDto> options = new ArrayList<>();
        long sort = 0L;
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
     * 固定三项：病区、病房、床位形态下拉。
     *
     * @return 形态选项
     */
    private List<OptionDto> buildFormEnumOptions() {
        List<OptionDto> formEnums = new ArrayList<>();
        formEnums.add(new OptionDto(
            LocationForm.WARD.getSortOrder(), LocationForm.WARD.getCode(), LocationForm.WARD.getDisplay()));
        formEnums.add(new OptionDto(
            LocationForm.HOUSE.getSortOrder(), LocationForm.HOUSE.getCode(), LocationForm.HOUSE.getDisplay()));
        formEnums.add(new OptionDto(
            LocationForm.BED.getSortOrder(), LocationForm.BED.getCode(), LocationForm.BED.getDisplay()));
        return formEnums;
    }

    /**
     * 按形态 code 在仓储层过滤后生成下拉（非全表内存过滤）。
     *
     * @param formCode {@link LocationForm} 的 code
     * @return 选项列表
     */
    private List<OptionDto> loadOptionsByForm(String formCode) {
        List<Location> locations = locationQueryService.listAllByFormCode(formCode);
        List<OptionDto> options = new ArrayList<>();
        long sort = 0L;
        for (Location location : locations) {
            if (location == null || location.getId() == null) {
                continue;
            }
            sort++;
            options.add(new OptionDto(sort, String.valueOf(location.getId()), location.getName()));
        }
        return options;
    }

    /**
     * 批量映射为 DTO，跳过 null 实体。
     *
     * @param locations 位置列表
     * @return DTO 列表
     */
    private List<WardBedDto> mapToDtoList(List<Location> locations) {
        List<WardBedDto> result = new ArrayList<>();
        if (locations == null || locations.isEmpty()) {
            return result;
        }
        for (Location location : locations) {
            if (location == null) {
                continue;
            }
            result.add(wardBedMapper.toDto(location));
        }
        return result;
    }

    /**
     * 存在父级时嵌套父级 DTO，供前端展示层级。
     *
     * @param entity 当前位置
     * @param dto    当前 DTO
     */
    private void attachParentIfPresent(Location entity, WardBedDto dto) {
        if (entity.getParentId() == null) {
            return;
        }
        Optional<Location> parentOptional = locationQueryService.findOptionalById(entity.getParentId());
        if (parentOptional.isEmpty()) {
            return;
        }
        dto.setParent(wardBedMapper.toDto(parentOptional.get()));
    }
}
