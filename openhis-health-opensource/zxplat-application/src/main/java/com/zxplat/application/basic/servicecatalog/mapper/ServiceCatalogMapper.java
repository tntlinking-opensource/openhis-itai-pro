/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.servicecatalog.mapper;

import com.zxplat.application.basic.servicecatalog.dto.ServiceCatalogDetailDto;
import com.zxplat.application.basic.servicecatalog.dto.ServiceCatalogInitDto;
import com.zxplat.application.basic.servicecatalog.request.CreateServiceCatalogRequest;
import com.zxplat.application.basic.servicecatalog.request.UpdateServiceCatalogRequest;
import com.zxplat.domain.workflow.activitydefinition.command.CreateActivityDefinitionCommand;
import com.zxplat.domain.workflow.activitydefinition.command.UpdateActivityDefinitionCommand;
import com.zxplat.domain.workflow.activitydefinition.entity.ActivityDefinition;

/**
 * 诊疗目录（活动定义）与产品定义读模型、领域写命令及初始化 DTO 之间的装配契约。
 */
public interface ServiceCatalogMapper {

    /**
     * 将产品定义读模型行投影为目录列表 DTO（含启用状态与展示文案）。
     *
     * @param productDefinitionModel 读模型行；{@code null} 时由实现返回 {@code null}
     * @return 列表行 DTO
     */
    /**
     * 将活动定义聚合根投影为详情 DTO（含子项、医保与划价等标志位整型化）。
     *
     * @param activityDefinition 领域实体；{@code null} 时由实现返回 {@code null}
     * @return 详情 DTO
     */
    ServiceCatalogDetailDto converterToServiceCatalogDetailDto(ActivityDefinition activityDefinition);

    /**
     * 将创建请求装配为领域「创建活动定义」命令。
     *
     * @param request 前端入参；{@code null} 时由实现返回 {@code null}
     * @return 领域创建命令
     */
    CreateActivityDefinitionCommand converterToCreateParam(CreateServiceCatalogRequest request);

    /**
     * 将更新请求装配为领域「更新活动定义」命令。
     *
     * @param request 前端入参；{@code null} 时由实现返回 {@code null}
     * @return 领域更新命令
     */
    UpdateActivityDefinitionCommand converterToUpdateParam(UpdateServiceCatalogRequest request);

    /**
     * 装配初始化 DTO 的静态部分（如收费等级下拉）；其余选项由应用服务从枚举与术语服务填充。
     *
     * @return 初始化 DTO 骨架
     */
    ServiceCatalogInitDto getInit();
}
