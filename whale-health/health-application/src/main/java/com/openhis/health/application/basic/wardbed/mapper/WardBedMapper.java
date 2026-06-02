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

package com.openhis.health.application.basic.wardbed.mapper;

import com.openhis.health.application.basic.wardbed.dto.WardBedDto;
import com.openhis.health.application.basic.wardbed.request.CreateWardBedRequest;
import com.openhis.health.application.basic.wardbed.request.UpdateWardBedRequest;
import com.openhis.health.domain.administration.location.command.ActivateLocationCommand;
import com.openhis.health.domain.administration.location.command.AbolishLocationCommand;
import com.openhis.health.domain.administration.location.command.DeactivateLocationCommand;
import com.openhis.health.domain.administration.location.command.EditLocationInfoCommand;
import com.openhis.health.domain.administration.location.command.RegisterLocationCommand;
import com.openhis.health.domain.administration.location.entity.Location;

/**
 * 病区/病房位置与领域位置命令、展示 DTO 之间的映射契约。
 */
public interface WardBedMapper {
    /**
     * 位置实体转展示 DTO。
     *
     * @param entity 领域位置
     * @return DTO
     */
    WardBedDto toDto(Location entity);

    /**
     * 创建请求转位置注册命令。
     *
     * @param request 创建请求
     * @return 注册命令
     */
    RegisterLocationCommand toCreateCommand(CreateWardBedRequest request);

    /**
     * 更新请求转编辑位置信息命令。
     *
     * @param request 更新请求
     * @return 编辑命令
     */
    EditLocationInfoCommand toUpdateCommand(UpdateWardBedRequest request);

    /**
     * 装配启用命令。
     *
     * @param locationId 位置主键
     * @return 启用命令
     */
    ActivateLocationCommand toActivateCommand(Long locationId);

    /**
     * 装配停用命令。
     *
     * @param locationId 位置主键
     * @return 停用命令
     */
    DeactivateLocationCommand toDeactivateCommand(Long locationId);

    /**
     * 装配废止命令。
     *
     * @param locationId 位置主键
     * @return 废止命令
     */
    AbolishLocationCommand toAbolishCommand(Long locationId);
}
