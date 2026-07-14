/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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
