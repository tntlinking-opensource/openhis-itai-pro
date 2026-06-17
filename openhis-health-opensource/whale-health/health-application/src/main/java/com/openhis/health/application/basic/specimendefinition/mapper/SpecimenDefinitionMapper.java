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
