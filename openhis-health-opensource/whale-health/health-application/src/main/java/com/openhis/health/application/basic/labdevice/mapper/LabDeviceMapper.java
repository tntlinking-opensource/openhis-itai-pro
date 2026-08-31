/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.labdevice.mapper;

import com.openhis.health.application.basic.labdevice.dto.LabDeviceDto;
import com.openhis.health.application.basic.labdevice.request.CreateLabDeviceRequest;
import com.openhis.health.application.basic.labdevice.request.UpdateLabDeviceRequest;
import com.openhis.health.domain.administration.labdevice.command.CreateLabDeviceCommand;
import com.openhis.health.domain.administration.labdevice.command.UpdateLabDeviceCommand;
import com.openhis.health.domain.administration.labdevice.queries.model.LabDeviceDetailQueryModel;
import com.openhis.health.domain.administration.labdevice.queries.model.LabDevicePageItemQueryModel;
import com.openhis.health.domain.administration.labdevice.queries.model.LabDeviceSearchItemQueryModel;

/**
 * 检验设备应用层 DTO 与领域读模型、写命令之间的装配契约。
 */
public interface LabDeviceMapper {

    /**
     * 分页读模型 → DTO。
     *
     * @param model 分页行读模型
     * @return DTO
     */
    LabDeviceDto toDto(LabDevicePageItemQueryModel model);

    /**
     * 详情读模型 → DTO。
     *
     * @param model 详情读模型
     * @return DTO
     */
    LabDeviceDto toDto(LabDeviceDetailQueryModel model);

    /**
     * 检索读模型 → DTO。
     *
     * @param model 检索行读模型
     * @return DTO
     */
    LabDeviceDto toDto(LabDeviceSearchItemQueryModel model);

    /**
     * 创建请求 → 领域命令。
     *
     * @param request 创建请求
     * @return 新建命令
     */
    CreateLabDeviceCommand toCreateCommand(CreateLabDeviceRequest request);

    /**
     * 维护请求 → 领域命令。
     *
     * @param request 更新请求
     * @return 更新命令
     */
    UpdateLabDeviceCommand toUpdateCommand(UpdateLabDeviceRequest request);
}
