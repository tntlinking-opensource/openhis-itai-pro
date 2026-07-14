/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.warehouse.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhis.health.application.basic.warehouse.dto.WarehouseDto;
import com.openhis.health.application.basic.warehouse.request.CreateWarehouseRequest;
import com.openhis.health.application.basic.warehouse.request.UpdateWarehouseRequest;
import com.openhis.health.domain.administration.location.command.ActivateLocationCommand;
import com.openhis.health.domain.administration.location.command.AbolishLocationCommand;
import com.openhis.health.domain.administration.location.command.DeactivateLocationCommand;
import com.openhis.health.domain.administration.location.command.EditLocationInfoCommand;
import com.openhis.health.domain.administration.location.command.RegisterLocationCommand;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.share.constants.BasicWarehouseConstants;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.LocationMode;
import com.openhis.health.domain.share.enums.LocationOperational;
import com.openhis.health.domain.share.enums.LocationStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.LocationTypeJsonUtil;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * 库房/药房映射：请求与响应使用 {@link LocationForm} 枚举 {@code code} 字符串，经 {@link EnumUtil} 校验后写入领域命令。
 */
@Component
@RequiredArgsConstructor
public class WarehouseMapperImpl implements WarehouseMapper {

    private final ObjectMapper objectMapper;

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
        dto.setTypeExtensions(LocationTypeJsonUtil.parseToMap(this.objectMapper, entity.getTypeJson()));
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
     * 装配注册命令：扁平根节点、实例模式、空 typeJson、指定机构与展示顺序 0。
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
        item.setFormEnum(this.requireWarehouseFormCode(request.getFormEnum()));
        item.setModeEnum(LocationMode.INSTANCE.getCode());
        item.setTypeJson(BasicWarehouseConstants.EMPTY_LOCATION_TYPE_JSON);
        item.setOrganizationId(organizationId);
        item.setDisplayOrder(0);
        // 库房/药房为扁平主数据，登记即启用，无草稿态与层级子节点
        item.setRegisterAsActive(Boolean.TRUE);
        command.setItems(Collections.singletonList(item));
        return command;
    }

    /**
     * 装配编辑命令，并声明变更字段集合供领域增量应用。
     *
     * @param request        更新请求
     * @param organizationId 挂靠机构主键
     * @return 编辑命令
     */
    @Override
    public EditLocationInfoCommand toUpdateCommand(UpdateWarehouseRequest request, Long organizationId) {
        EditLocationInfoCommand command = new EditLocationInfoCommand();
        EditLocationInfoCommand.EditLocationInfoItemCommand item = new EditLocationInfoCommand.EditLocationInfoItemCommand();
        item.setLocationId(request.getId());
        item.setName(request.getName());
        item.setFormEnum(this.requireWarehouseFormCode(request.getFormEnum()));
        item.setModeEnum(LocationMode.INSTANCE.getCode());
        item.setTypeJson(BasicWarehouseConstants.EMPTY_LOCATION_TYPE_JSON);
        item.setDisplayOrder(0);
        item.setOrganizationId(organizationId);
        item.setChangedFields(List.of("name", "formEnum", "modeEnum", "typeJson", "displayOrder", "organizationId"));
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
     * 单条位置停用命令（附带应用层停用原因）。
     *
     * @param locationId 位置主键
     * @return 停用命令
     */
    @Override
    public DeactivateLocationCommand toDeactivateCommand(Long locationId) {
        DeactivateLocationCommand command = new DeactivateLocationCommand();
        DeactivateLocationCommand.DeactivateLocationItemCommand item = new DeactivateLocationCommand.DeactivateLocationItemCommand();
        item.setLocationId(locationId);
        item.setReason(BasicWarehouseConstants.REASON_DEACTIVATE_BY_APP);
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
     * 校验请求形态为库房维护页支持的 {@link LocationForm}，并返回领域 code。
     *
     * @param formCode 请求中的形态 code
     * @return 领域形态 code
     * @throws ValidationException 形态为空、无法解析或不在药库/药房/科室药房范围时
     */
    private String requireWarehouseFormCode(String formCode) {
        if (StringUtils.isEmpty(formCode)) {
            throw new ValidationException("物理形式不能为空");
        }
        LocationForm form = EnumUtil.fromCode(LocationForm.class, formCode.trim());
        if (form == null) {
            throw new ValidationException("物理形式不合法");
        }
        if (!this.isWarehouseMaintenanceForm(form.getCode())) {
            throw new ValidationException("物理形式不适用于库房维护");
        }
        return form.getCode();
    }

    /**
     * 判断形态是否属于库房维护页（药库、药房、科室药房）。
     *
     * @param formCode 领域形态 code
     * @return 是否库房维护形态
     */
    private boolean isWarehouseMaintenanceForm(String formCode) {
        return LocationForm.WAREHOUSE.getCode().equals(formCode)
            || LocationForm.PHARMACY.getCode().equals(formCode)
            || LocationForm.DEPARTMENT_PHARMACY.getCode().equals(formCode);
    }
}
