/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.body.mapper;

import com.openhis.health.application.basic.body.dto.BodyDto;
import com.openhis.health.application.basic.body.request.CreateBodyRequest;
import com.openhis.health.application.basic.body.request.UpdateBodyRequest;
import com.openhis.health.domain.diagnosis.bodystructure.command.AddOrEditBodyStructureCommand;
import com.openhis.health.domain.diagnosis.bodystructure.entity.BodyStructure;
import com.openhis.health.domain.diagnosis.bodystructure.queries.model.BodyStructureInfoQueryModel;
import com.openhis.health.domain.diagnosis.bodystructure.queries.model.BodyStructureListItemQueryModel;
import com.openhis.health.domain.diagnosis.bodystructure.queries.model.BodyStructureTreeQueryModel;
import com.openhis.whale.core.application.dto.OptionDto;

import java.util.List;

/**
 * 身体部位主数据的应用层映射：领域实体/查询模型 ↔ 接口 DTO；请求体 ↔ 统一写命令。
 *
 * <p>禁止承载业务流程判断，仅做可信字段拷贝与结构转换（由 MapStruct 或手写实现保证）。</p>
 *
 */
public interface BodyMapper {

    /**
     * 领域实体 → 维护端 DTO。
     *
     * @param entity 部位聚合实体；{@code null} 时实现应返回 {@code null}
     * @return DTO 或 {@code null}
     */
    BodyDto toDto(BodyStructure entity);

    /**
     * 列表项查询模型 → DTO。
     *
     * @param model 列表行模型
     * @return DTO 或 {@code null}
     */
    BodyDto toDto(BodyStructureListItemQueryModel model);

    /**
     * 详情查询模型 → DTO。
     *
     * @param model 详情模型
     * @return DTO 或 {@code null}
     */
    BodyDto toDto(BodyStructureInfoQueryModel model);

    /**
     * 树形查询结果 → 前端 {@link OptionDto} 树根列表（子节点挂 {@link OptionDto#getChildren()}）。
     *
     * @param roots 树根模型列表
     * @return 选项树根列表，不为 {@code null}
     */
    List<OptionDto> toOptionTree(List<BodyStructureTreeQueryModel> roots);

    /**
     * 新建请求 → 领域新增/编辑命令（新增语义由命令体内 id 是否为空区分，以领域为准）。
     *
     * @param request 创建请求
     * @return 写命令
     */
    AddOrEditBodyStructureCommand toCreateCommand(CreateBodyRequest request);

    /**
     * 更新请求 → 领域命令。
     *
     * @param request 更新请求（须含主键）
     * @return 写命令
     */
    AddOrEditBodyStructureCommand toUpdateCommand(UpdateBodyRequest request);
}
