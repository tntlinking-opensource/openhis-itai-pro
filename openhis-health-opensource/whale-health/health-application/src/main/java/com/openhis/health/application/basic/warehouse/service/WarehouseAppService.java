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

package com.openhis.health.application.basic.warehouse.service;

import com.openhis.health.application.basic.warehouse.dto.WarehouseDto;
import com.openhis.health.application.basic.warehouse.dto.WarehouseInitDto;
import com.openhis.health.application.basic.warehouse.mapper.WarehouseMapper;
import com.openhis.health.application.basic.warehouse.request.CreateWarehouseRequest;
import com.openhis.health.application.basic.warehouse.request.ListWarehouseRequest;
import com.openhis.health.application.basic.warehouse.request.QueryWarehouseRequest;
import com.openhis.health.application.basic.warehouse.request.UpdateWarehouseRequest;
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
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 库房/药房应用服务：以 {@link com.openhis.health.domain.administration.location.entity.Location} 为承载，
 * 仅暴露物理形式为仓库或药房的节点，并完成注册、编辑、启停与废止编排。
 */
@Service
@RequiredArgsConstructor
public class WarehouseAppService {
    /** 前端下拉用的仓库形态编码（与 {@link com.openhis.health.domain.share.enums.LocationForm#WAREHOUSE} 对应）。 */
    private static final String FORM_CODE_WAREHOUSE = "17";
    /** 前端下拉用的药房形态编码（与 {@link com.openhis.health.domain.share.enums.LocationForm#PHARMACY} 对应）。 */
    private static final String FORM_CODE_PHARMACY = "16";
    /** 下拉排序：仓库在前。 */
    private static final Long FORM_SORT_WAREHOUSE = 1L;
    /** 下拉排序：药房在后。 */
    private static final Long FORM_SORT_PHARMACY = 2L;

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
     * 维护页初始化：位置状态字典、机构树选项、仓库/药房形态固定选项。
     *
     * @return 初始化 DTO
     */
    public WarehouseInitDto init() {
        WarehouseInitDto dto = new WarehouseInitDto();
        dto.setLocationStatusOptions(codeSystemQueryService.getOptionListByEnum(LocationStatus.class));
        dto.setOrganizationOptions(loadOrganizationOptions());
        dto.setFormEnumOptions(List.of(
            new OptionDto(FORM_SORT_WAREHOUSE, FORM_CODE_WAREHOUSE, "仓库"),
            new OptionDto(FORM_SORT_PHARMACY, FORM_CODE_PHARMACY, "药房")
        ));
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
        List<Location> allLocations = locationQueryService.listAllWarehouseAndPharmacyLocations();
        if (allLocations == null || allLocations.isEmpty()) {
            return new ArrayList<>();
        }
        String keyword = normalizeKeyword(request);
        return allLocations.stream()
            .filter(item -> isWarehouseOrPharmacy(item.getFormEnum()))
            .map(warehouseMapper::toDto)
            .filter(dto -> matchesKeyword(dto, keyword))
            .collect(Collectors.toList());
    }

    /**
     * 按主键查询单条库房/药房。
     *
     * @param request 含 id 的查询请求
     * @return 库房 DTO
     * @throws ValidationException 请求或主键为空，或该位置非库房/药房形态时
     * @throws EntityNotFoundException 位置不存在时
     */
    public WarehouseDto getWarehouse(QueryWarehouseRequest request) {
        if (request == null || request.getId() == null) {
            throw new ValidationException("库房ID不能为空");
        }
        Optional<Location> entityOptional = locationQueryService.findOptionalById(request.getId());
        if (entityOptional.isEmpty()) {
            throw new EntityNotFoundException("库房", request.getId());
        }
        Location entity = entityOptional.get();
        if (!isWarehouseOrPharmacy(entity.getFormEnum())) {
            throw new ValidationException("该位置不是库房/药房");
        }
        return warehouseMapper.toDto(entity);
    }

    /**
     * 新建库房：默认挂靠机构树中第一个有效机构，注册位置后回查详情。
     *
     * @param request 创建请求（含形态、名称等必填字段）
     * @return 新建后的 DTO
     * @throws ValidationException 请求体为空或无可用的默认机构时
     * @throws RuntimeException 领域注册失败时
     */
    public WarehouseDto createWarehouse(CreateWarehouseRequest request) {
        if (request == null) {
            throw new ValidationException("创建请求不能为空");
        }
        Long organizationId = resolveDefaultOrganizationId();
        // 步骤一：领域注册位置；步骤二：按新 id 回查详情 DTO
        Long locationId = locationDomainService.registerLocation(warehouseMapper.toCreateCommand(request, organizationId));
        QueryWarehouseRequest query = new QueryWarehouseRequest();
        query.setId(locationId);
        return getWarehouse(query);
    }

    /**
     * 编辑库房基础信息与形态。
     *
     * @param request 更新请求（须含主键 id）
     * @return 更新后 DTO
     * @throws ValidationException 请求体或主键为空时
     * @throws RuntimeException 领域更新失败时
     */
    public WarehouseDto editWarehouse(UpdateWarehouseRequest request) {
        if (request == null || request.getId() == null) {
            throw new ValidationException("库房ID不能为空");
        }
        locationDomainService.editLocationInfo(warehouseMapper.toUpdateCommand(request));
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
        List<Long> ids;
        if (request == null) {
            ids = List.of();
        } else {
            ids = request.resolveIds();
        }
        if (ids.isEmpty()) {
            throw new ValidationException("库房ID列表不能为空");
        }
        // Loop：逐条校验形态后下发启用（停用态可重新启用，无草稿/树形门禁）
        for (Long id : ids) {
            if (id != null) {
                this.requireWarehouseOrPharmacyLocation(id);
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
        List<Long> ids;
        if (request == null) {
            ids = List.of();
        } else {
            ids = request.resolveIds();
        }
        if (ids.isEmpty()) {
            throw new ValidationException("库房ID列表不能为空");
        }
        // Loop：逐条校验形态后下发停用（扁平节点，无子节点门禁）
        for (Long id : ids) {
            if (id != null) {
                this.requireWarehouseOrPharmacyLocation(id);
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
     * @throws RuntimeException 领域废止失败时
     */
    public void deleteWarehouse(QueryWarehouseRequest request) {
        if (request == null || request.getId() == null) {
            throw new ValidationException("库房ID不能为空");
        }
        this.requireWarehouseOrPharmacyLocation(request.getId());
        locationDomainService.abolishLocation(warehouseMapper.toAbolishCommand(request.getId()));
    }

    /**
     * 判断形态编码是否为仓库或药房（领域枚举 code）。
     *
     * @param formCode 形态编码
     * @return 是否属于库房/药房范畴
     */
    private boolean isWarehouseOrPharmacy(String formCode) {
        return LocationForm.WAREHOUSE.getCode().equals(formCode)
            || LocationForm.PHARMACY.getCode().equals(formCode);
    }

    /**
     * 启停前加载位置并断言为库房/药房形态。
     *
     * @param locationId 位置主键
     * @return 位置实体
     * @throws EntityNotFoundException 位置不存在时
     * @throws ValidationException     非库房/药房形态时
     */
    private Location requireWarehouseOrPharmacyLocation(Long locationId) {
        Optional<Location> entityOptional = locationQueryService.findOptionalById(locationId);
        if (entityOptional.isEmpty()) {
            throw new EntityNotFoundException("库房", locationId);
        }
        Location entity = entityOptional.get();
        if (!isWarehouseOrPharmacy(entity.getFormEnum())) {
            throw new ValidationException("该位置不是库房/药房");
        }
        return entity;
    }

    /**
     * 归一化列表检索关键词：trim 后转小写；空串视为无关键词。
     *
     * @param request 列表请求，可为 null
     * @return 小写关键词；无有效关键词时返回 null
     */
    private String normalizeKeyword(ListWarehouseRequest request) {
        if (request == null || StringUtils.isBlank(request.getKeyword())) {
            return null;
        }
        return request.getKeyword().trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 判断 DTO 是否命中关键词（名称、编号、拼音、五笔、类型与状态展示文案）。
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
            || containsIgnoreCase(dto.getStatusEnumText(), keyword);
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
     * 将机构树根拍平为下拉选项（value 为机构 id 字符串）。
     *
     * <p>当前实现仅处理一层拍平，不递归处理子机构；如需树形结构需扩展。</p>
     *
     * @return 选项列表；机构树为空时返回空列表
     */
    private List<OptionDto> loadOrganizationOptions() {
        List<Organization> organizations = organizationQueryService.getOrganizationTree();
        List<OptionDto> options = new ArrayList<>();
        if (organizations == null || organizations.isEmpty()) {
            return options;
        }
        long sort = 0L;
        // Loop：遍历机构列表，过滤无效节点后生成下拉选项
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
     * 创建库房时解析默认所属机构：取机构树中第一个有效主键；无则抛校验异常。
     *
     * <p>业务说明：库房必须挂靠到某一机构下；当前默认策略为取机构树首个节点，后续可扩展为按当前登录用户上下文选择。</p>
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
}
