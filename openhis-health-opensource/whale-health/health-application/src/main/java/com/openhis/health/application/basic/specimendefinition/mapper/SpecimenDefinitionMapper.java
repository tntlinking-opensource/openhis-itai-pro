/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.specimendefinition.mapper;

import com.openhis.health.application.basic.specimendefinition.dto.SpecimenDefinitionDto;
import com.openhis.health.application.basic.specimendefinition.request.CreateSpecimenDefinitionRequest;
import com.openhis.health.application.basic.specimendefinition.request.UpdateSpecimenDefinitionRequest;
import com.openhis.health.domain.administration.specimendefinition.command.CreateSpecimenDefinitionCommand;
import com.openhis.health.domain.administration.specimendefinition.command.UpdateSpecimenDefinitionCommand;
import com.openhis.health.domain.administration.specimendefinition.queries.model.SpecimenDefinitionDetailQueryModel;
import com.openhis.health.domain.administration.specimendefinition.queries.model.SpecimenDefinitionPageItemQueryModel;
import com.openhis.health.domain.administration.specimendefinition.queries.model.SpecimenDefinitionSearchItemQueryModel;

/**
 * 样本定义应用层 DTO 与领域读模型、写命令之间的装配契约。
 */
public interface SpecimenDefinitionMapper {

    /**
     * 将分页行读模型投影为列表 DTO。
     *
     * @param model 分页行；{@code null} 时由实现返回 {@code null}
     * @return 列表行 DTO
     */
    SpecimenDefinitionDto toDto(SpecimenDefinitionPageItemQueryModel model);

    /**
     * 将详情读模型投影为 DTO。
     *
     * @param model 详情读模型；{@code null} 时由实现返回 {@code null}
     * @return 详情 DTO
     */
    SpecimenDefinitionDto toDto(SpecimenDefinitionDetailQueryModel model);

    /**
     * 将检索行读模型投影为 DTO。
     *
     * @param model 检索行；{@code null} 时由实现返回 {@code null}
     * @return 检索行 DTO
     */
    SpecimenDefinitionDto toDto(SpecimenDefinitionSearchItemQueryModel model);

    /**
     * 组装领域「新建样本定义」命令。
     *
     * @param request 前端入参
     * @return 领域创建命令
     */
    CreateSpecimenDefinitionCommand toCreateCommand(CreateSpecimenDefinitionRequest request);

    /**
     * 组装领域「维护样本定义」命令。
     *
     * @param request 前端入参
     * @return 领域更新命令
     */
    UpdateSpecimenDefinitionCommand toUpdateCommand(UpdateSpecimenDefinitionRequest request);
}
