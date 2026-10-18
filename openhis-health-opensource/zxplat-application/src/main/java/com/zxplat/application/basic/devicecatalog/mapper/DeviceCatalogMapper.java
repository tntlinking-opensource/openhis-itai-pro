/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.devicecatalog.mapper;

import com.zxplat.application.basic.devicecatalog.dto.DeviceCatalogDto;
import com.zxplat.application.basic.devicecatalog.request.CreateDeviceCatalogRequest;
import com.zxplat.application.basic.devicecatalog.request.UpdateDeviceCatalogRequest;
import com.zxplat.domain.administration.devicedefinition.command.*;
import com.zxplat.domain.administration.devicedefinition.entity.DeviceDefinition;

import java.util.List;

/**
 * 耗材目录应用层映射：设备定义实体/读模型 ↔ 网关 DTO；请求 ↔ 领域批量写命令条目。
 *
 * <p>网关 id 为字符串，批量启停删条目组装应在实现类内完成解析（通常委托 {@code GatewayIdParseUtil}）。</p>
 */
public interface DeviceCatalogMapper {

    /**
     * 设备定义实体 → 目录 DTO（不含列表页批量回填的定价字典文本等，由 AppService enrich）。
     *
     * @param entity 领域实体
     * @return DTO 或 {@code null}
     */
    DeviceCatalogDto toDto(DeviceDefinition entity);

    /**
     * 产品读模型行 → 目录 DTO（读侧投影字段可能与实体略有差异）。
     *
     * @param model 读模型
     * @return DTO 或 {@code null}
     */
    /**
     * 创建请求 → 领域创建命令。
     *
     * @param request 请求体
     * @return 创建命令
     */
    CreateDeviceDefinitionCommand toCreateCommand(CreateDeviceCatalogRequest request);

    /**
     * 更新请求 → 领域更新命令（批量命令壳内通常仅一项）。
     *
     * @param request 请求体
     * @return 更新命令
     */
    UpdateDeviceDefinitionCommand toUpdateCommand(UpdateDeviceCatalogRequest request);

    /**
     * 字符串主键列表 → 批量启用命令子项。
     *
     * @param ids 设备 id 字符串列表
     * @return 命令子项列表
     */
    List<ActivateDeviceDefinitionsCommand.Item> toActivateItems(List<String> ids);

    /**
     * 字符串主键列表 → 批量停用命令子项。
     *
     * @param ids 设备 id 字符串列表
     * @return 命令子项列表
     */
    List<RetireDeviceDefinitionsCommand.Item> toRetireItems(List<String> ids);

    /**
     * 字符串主键列表 → 批量删除命令子项。
     *
     * @param ids 设备 id 字符串列表
     * @return 命令子项列表
     */
    List<DeleteDeviceDefinitionsCommand.Item> toDeleteItems(List<String> ids);
}
