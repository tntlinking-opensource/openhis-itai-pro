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

package com.openhis.health.application.basic.warehouse.mapper;

import com.openhis.health.application.basic.warehouse.dto.WarehouseDto;
import com.openhis.health.application.basic.warehouse.request.CreateWarehouseRequest;
import com.openhis.health.application.basic.warehouse.request.UpdateWarehouseRequest;
import com.openhis.health.domain.administration.location.command.ActivateLocationCommand;
import com.openhis.health.domain.administration.location.command.AbolishLocationCommand;
import com.openhis.health.domain.administration.location.command.DeactivateLocationCommand;
import com.openhis.health.domain.administration.location.command.EditLocationInfoCommand;
import com.openhis.health.domain.administration.location.command.RegisterLocationCommand;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationMode;
import com.openhis.health.domain.share.enums.LocationOperational;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

/**
 * 库房映射实现：前端 16/17 与领域 {@link LocationForm} 编码互认，DTO 侧补齐枚举展示文案。
 */
@Component
public class WarehouseMapperImpl implements WarehouseMapper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final TypeReference<Map<String, Object>> TYPE_EXTENSIONS_REF = new TypeReference<>() {};
    private static final String FORM_CODE_PHARMACY = "16";
    private static final String FORM_CODE_WAREHOUSE = "17";
    private static final String REASON_DEACTIVATE_BY_APP = "应用层停用";
    private static final Map<String, String> FORM_CODE_MAPPING = new HashMap<>();

    /** 初始化形态别名到领域枚举 code 的映射表。 */
    static {
        FORM_CODE_MAPPING.put(FORM_CODE_PHARMACY, LocationForm.PHARMACY.getCode());
        FORM_CODE_MAPPING.put(LocationForm.PHARMACY.getCode(), LocationForm.PHARMACY.getCode());
        FORM_CODE_MAPPING.put(FORM_CODE_WAREHOUSE, LocationForm.WAREHOUSE.getCode());
        FORM_CODE_MAPPING.put(LocationForm.WAREHOUSE.getCode(), LocationForm.WAREHOUSE.getCode());
    }

    /**
     * 将位置实体映射为库房 DTO，并填充状态、运营、模式、形态的展示文本。
     *
     * @param entity 领域位置，可为 null
     * @return DTO；入参为 null 时返回 null
     */
    @Override
    public WarehouseDto toDto(Location entity) {
        if (entity == null) {
            return null;
        }
        WarehouseDto dto = new WarehouseDto();
        dto.setId(entity.getId());
        dto.setBusNo(entity.getBusNo());
        dto.setName(entity.getName());
        dto.setStatusEnum(entity.getStatusEnum());
        dto.setOperationalEnum(entity.getOperationalEnum());
        dto.setModeEnum(entity.getModeEnum());
        dto.setTypeExtensions(parseTypeExtensions(entity.getTypeJson()));
        dto.setPyStr(entity.getPyStr());
        dto.setWbStr(entity.getWbStr());
        dto.setFormEnum(entity.getFormEnum());
        dto.setOrganizationId(entity.getOrganizationId());
        dto.setDisplayOrder(entity.getDisplayOrder());

        LocationStatus status = EnumUtil.fromCode(LocationStatus.class, entity.getStatusEnum());
        if (status == null) {
            dto.setStatusEnumText(null);
        } else {
            dto.setStatusEnumText(status.getDisplay());
        }
        LocationOperational operational = EnumUtil.fromCode(LocationOperational.class, entity.getOperationalEnum());
        if (operational == null) {
            dto.setOperationalEnumText(null);
        } else {
            dto.setOperationalEnumText(operational.getDisplay());
        }
        LocationMode mode = EnumUtil.fromCode(LocationMode.class, entity.getModeEnum());
        if (mode == null) {
            dto.setModeEnumText(null);
        } else {
            dto.setModeEnumText(mode.getDisplay());
        }
        LocationForm form = EnumUtil.fromCode(LocationForm.class, entity.getFormEnum());
        if (form == null) {
            dto.setFormEnumText(null);
        } else {
            dto.setFormEnumText(form.getDisplay());
        }
        return dto;
    }

    /**
     * 装配注册命令：根节点、实例模式、空 typeJson、指定机构与展示顺序 0。
     *
     * @param request        创建请求
     * @param organizationId 机构主键
     * @return 注册命令
     */
    @Override
    public RegisterLocationCommand toCreateCommand(CreateWarehouseRequest request, Long organizationId) {
        RegisterLocationCommand command = new RegisterLocationCommand();
        RegisterLocationCommand.RegisterLocationItemCommand item = new RegisterLocationCommand.RegisterLocationItemCommand();
        item.setName(request.getName());
        item.setParentId(null);
        item.setFormEnum(resolveFormCode(request.getFormEnum()));
        item.setModeEnum(LocationMode.INSTANCE.getCode());
        item.setTypeJson("{}");
        item.setOrganizationId(organizationId);
        item.setDisplayOrder(0);
        // 库房/药房为扁平主数据，登记即启用，无草稿态与层级子节点。
        item.setRegisterAsActive(Boolean.TRUE);
        command.setItems(Collections.singletonList(item));
        return command;
    }

    /**
     * 装配编辑命令，并声明变更字段集合供领域增量应用。
     *
     * @param request 更新请求
     * @return 编辑命令
     */
    @Override
    public EditLocationInfoCommand toUpdateCommand(UpdateWarehouseRequest request) {
        EditLocationInfoCommand command = new EditLocationInfoCommand();
        EditLocationInfoCommand.EditLocationInfoItemCommand item = new EditLocationInfoCommand.EditLocationInfoItemCommand();
        item.setLocationId(request.getId());
        item.setName(request.getName());
        item.setFormEnum(resolveFormCode(request.getFormEnum()));
        item.setModeEnum(LocationMode.INSTANCE.getCode());
        item.setTypeJson("{}");
        item.setDisplayOrder(0);
        item.setChangedFields(List.of("name", "formEnum", "modeEnum", "typeJson", "displayOrder"));
        command.setItems(Collections.singletonList(item));
        return command;
    }

    /**
     * 单条位置启用命令。
     *
     * @param locationId 位置主键
     * @return 启用命令
     */
    @Override
    public ActivateLocationCommand toActivateCommand(Long locationId) {
        ActivateLocationCommand command = new ActivateLocationCommand();
        ActivateLocationCommand.ActivateLocationItemCommand item = new ActivateLocationCommand.ActivateLocationItemCommand();
        item.setLocationId(locationId);
        command.setItems(Collections.singletonList(item));
        return command;
    }

    /**
     * 单条位置停用命令（附带应用层停用原因常量）。
     *
     * @param locationId 位置主键
     * @return 停用命令
     */
    @Override
    public DeactivateLocationCommand toDeactivateCommand(Long locationId) {
        DeactivateLocationCommand command = new DeactivateLocationCommand();
        DeactivateLocationCommand.DeactivateLocationItemCommand item = new DeactivateLocationCommand.DeactivateLocationItemCommand();
        item.setLocationId(locationId);
        item.setReason(REASON_DEACTIVATE_BY_APP);
        command.setItems(Collections.singletonList(item));
        return command;
    }

    /**
     * 单条位置废止命令。
     *
     * @param locationId 位置主键
     * @return 废止命令
     */
    @Override
    public AbolishLocationCommand toAbolishCommand(Long locationId) {
        AbolishLocationCommand command = new AbolishLocationCommand();
        AbolishLocationCommand.AbolishLocationItemCommand item = new AbolishLocationCommand.AbolishLocationItemCommand();
        item.setLocationId(locationId);
        command.setItems(Collections.singletonList(item));
        return command;
    }

    /**
     * 将前端形态（16/17 或领域 code）规范为 {@link LocationForm} 的 code；空则默认仓库。
     *
     * @param formEnum 原始形态
     * @return 领域形态 code
     */
    private String resolveFormCode(String formEnum) {
        if (formEnum == null || formEnum.isBlank()) {
            return LocationForm.WAREHOUSE.getCode();
        }
        return FORM_CODE_MAPPING.getOrDefault(formEnum, formEnum);
    }

    /**
     * 将库表 {@code type_json} 解析为库房类型扩展键值（B.6.2 出参 {@code typeExtensions}）。
     *
     * @param typeJson 位置扩展 JSON 原文
     * @return 解析成功的 Map；空串或解析失败时返回空 Map（不抛错，避免列表整页失败）
     */
    private static Map<String, Object> parseTypeExtensions(String typeJson) {
        if (StringUtils.isBlank(typeJson)) {
            return Collections.emptyMap();
        }
        try {
            return OBJECT_MAPPER.readValue(typeJson, TYPE_EXTENSIONS_REF);
        } catch (Exception ignored) {
            // 历史脏 JSON：降级为空扩展，由前端按无扩展展示
            return Collections.emptyMap();
        }
    }
}
