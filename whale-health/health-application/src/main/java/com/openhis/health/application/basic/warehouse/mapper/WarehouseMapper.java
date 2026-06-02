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
     * @param request 更新请求
     * @return 编辑命令
     */
    EditLocationInfoCommand toUpdateCommand(UpdateWarehouseRequest request);

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
