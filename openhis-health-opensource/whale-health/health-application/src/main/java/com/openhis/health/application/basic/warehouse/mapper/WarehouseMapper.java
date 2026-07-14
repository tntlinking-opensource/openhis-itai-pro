/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

/**
 * 库房/药房应用层与位置领域命令、DTO 之间的映射契约。
 */
public interface WarehouseMapper {
    /**
     * 位置实体转库房展示 DTO。
     *
     * @param entity 领域位置
     * @return 库房 DTO
     */
    WarehouseDto toDto(Location entity);

    /**
     * 创建请求装配为位置注册命令（单条 item）。
     *
     * @param request        创建请求
     * @param organizationId 挂靠机构主键
     * @return 注册位置命令
     */
    RegisterLocationCommand toCreateCommand(CreateWarehouseRequest request, Long organizationId);

    /**
     * 更新请求装配为编辑位置信息命令。
     *
     * @param request        更新请求
     * @param organizationId 挂靠机构主键（科室药房等）
     * @return 编辑命令
     */
    EditLocationInfoCommand toUpdateCommand(UpdateWarehouseRequest request, Long organizationId);

    /**
     * 装配启用位置命令（单 id）。
     *
     * @param locationId 位置主键
     * @return 启用命令
     */
    ActivateLocationCommand toActivateCommand(Long locationId);

    /**
     * 装配停用位置命令（单 id，含应用层停用原因）。
     *
     * @param locationId 位置主键
     * @return 停用命令
     */
    DeactivateLocationCommand toDeactivateCommand(Long locationId);

    /**
     * 装配废止位置命令（单 id）。
     *
     * @param locationId 位置主键
     * @return 废止命令
     */
    AbolishLocationCommand toAbolishCommand(Long locationId);
}
