/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.basic.wardbed.service;

import com.openhis.health.application.basic.wardbed.dto.WardBedDto;
import com.openhis.health.application.basic.wardbed.dto.WardBedInitDto;
import com.openhis.health.application.basic.wardbed.mapper.WardBedMapper;
import com.openhis.health.application.basic.wardbed.request.CreateWardBedRequest;
import com.openhis.health.application.basic.wardbed.request.QueryWardBedRequest;
import com.openhis.health.application.basic.wardbed.request.UpdateWardBedRequest;
import com.openhis.health.domain.administration.location.command.EditLocationInfoCommand;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationDomainService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import com.openhis.whale.core.application.exception.EntityNotFoundException;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
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
        WardBedInitDto dto = new WardBedInitDto();
        dto.setLocationStatusOptions(codeSystemQueryService.getOptionListByEnum(LocationStatus.class));
        dto.setOrganizationOptions(this.loadOrganizationOptions());
        dto.setFormEnums(this.buildFormEnumOptions());
        List<OptionDto> wardList = this.loadOptionsByForm(LocationForm.WARD.getCode());
        List<OptionDto> houseList = this.loadOptionsByForm(LocationForm.HOUSE.getCode());
        dto.setWardOptions(wardList);
        dto.setHouseOptions(houseList);
        log.info("病区床位初始化：{} 个病区，{} 个病房", wardList.size(), houseList.size());
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
        if (query != null && query.getId() != null) {
            wards = locationQueryService.listWardsByOrganization(query.getId());
        } else {
            wards = locationQueryService.listAllByFormCode(LocationForm.WARD.getCode());
        }
        return this.mapToDtoList(wards);
    }

    /**
     * 指定病区下的「病房 + 床位」二级树。
     *
     * @param query {@code id} 为病区位置主键，缺失时返回空列表
     * @return 病房 DTO，{@code children} 为床位
     */
    public List<WardBedDto> getHouseAndBedList(QueryWardBedRequest query) {
        if (query == null || query.getId() == null) {
            return new ArrayList<>();
        }
        List<Location> rooms = locationQueryService.listRoomsUnderWard(query.getId());
        List<WardBedDto> houses = new ArrayList<>();
        for (Location room : rooms) {
            WardBedDto houseDto = wardBedMapper.toDto(room);
            List<Location> beds = locationQueryService.listBedsUnderParent(room.getId());
            houseDto.setChildren(this.mapToDtoList(beds));
            houses.add(houseDto);
        }
        return houses;
    }

    /**
     * 按主键加载位置，并在存在父级时填充父级快照。
     *
     * @param id 位置主键
     * @return DTO
     */
    public WardBedDto getById(Long id) {
        if (id == null) {
            throw new ValidationException("位置ID不能为空");
        }
        Location entity = locationQueryService.findOptionalById(id)
            .orElseThrow(() -> new EntityNotFoundException("位置", id));
        WardBedDto dto = wardBedMapper.toDto(entity);
        this.attachParentIfPresent(entity, dto);
        return dto;
    }

    /**
     * 登记病区/病房/床位（领域登记即启用），并按新主键回查。
     *
     * @param request 创建请求
     * @return 新建位置 DTO
     */
    public WardBedDto create(CreateWardBedRequest request) {
        if (request == null) {
            throw new ValidationException("创建请求不能为空");
        }
        if (StringUtils.isBlank(request.getFormEnum())) {
            throw new ValidationException("位置类型不能为空");
        }
        if (request.getOrganizationId() == null) {
            throw new ValidationException("机构不能为空");
        }
        Long createdId = locationDomainService.registerLocation(wardBedMapper.toCreateCommand(request));
        return this.getById(createdId);
    }

    /**
     * 编辑位置基础信息并回查。
     *
     * @param request 更新请求
     * @return 更新后 DTO
     */
    public WardBedDto edit(UpdateWardBedRequest request) {
        if (request == null || request.getId() == null) {
            throw new ValidationException("位置ID不能为空");
        }
        EditLocationInfoCommand command = wardBedMapper.toUpdateCommand(request);
        locationDomainService.editLocationInfo(command);
        return this.getById(request.getId());
    }

    /**
     * 废止位置。
     *
     * @param id 位置主键
     */
    public void deleteLocation(Long id) {
        if (id == null) {
            throw new ValidationException("位置ID不能为空");
        }
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
        if (id == null) {
            throw new ValidationException("位置ID不能为空");
        }
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
        if (id == null) {
            throw new ValidationException("位置ID不能为空");
        }
        locationDomainService.deactivateLocation(wardBedMapper.toDeactivateCommand(id));
        return true;
    }

    /**
     * 机构树拍平为下拉选项。
     *
     * @return 选项列表
     */
    private List<OptionDto> loadOrganizationOptions() {
        List<Organization> organizations = organizationQueryService.getOrganizationTree();
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
