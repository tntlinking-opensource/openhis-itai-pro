/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.diagnosiscatalog.mapper;

import com.zxplat.application.basic.diagnosiscatalog.dto.DiagnosisCatalogDetailDto;
import com.zxplat.application.basic.diagnosiscatalog.dto.DiagnosisCatalogDto;
import com.zxplat.application.basic.diagnosiscatalog.request.CreateDiagnosisCatalogRequest;
import com.zxplat.application.basic.diagnosiscatalog.request.UpdateDiagnosisCatalogRequest;
import com.zxplat.domain.clinical.conditiondefinition.command.AddDiseaseCommand;
import com.zxplat.domain.clinical.conditiondefinition.command.EditDiseaseCommand;
import com.zxplat.domain.clinical.conditiondefinition.entity.ConditionDefinition;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.DiseaseDetailQueryModel;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.DiseasePageItemQueryModel;

/**
 * 疾病（病种）目录应用层与临床「条件定义」聚合读写模型之间的装配契约。
 */
public interface DiagnosisCatalogMapper {

    /**
     * 将条件定义实体投影为列表项 DTO（字段最全，用于少数直连实体场景）。
     *
     * @param entity 领域实体；{@code null} 时由实现返回 {@code null}
     * @return 列表项 DTO
     */
    DiagnosisCatalogDto toDto(ConditionDefinition entity);

    /**
     * 将疾病分页行读模型投影为列表项 DTO。
     *
     * @param model 分页行；{@code null} 时由实现返回 {@code null}
     * @return 列表项 DTO
     */
    DiagnosisCatalogDto toDto(DiseasePageItemQueryModel model);

    /**
     * 将疾病详情读模型投影为详情 DTO（含扩展展示字段）。
     *
     * @param model 详情读模型；{@code null} 时由实现返回 {@code null}
     * @return 详情 DTO
     */
    DiagnosisCatalogDetailDto toDetailDto(DiseaseDetailQueryModel model);

    /**
     * 组装领域「新增疾病」命令。
     *
     * @param request 前端入参；{@code null} 时由实现抛校验异常
     * @return 领域新增命令
     */
    AddDiseaseCommand toCreateCommand(CreateDiagnosisCatalogRequest request);

    /**
     * 组装领域「编辑疾病」命令。
     *
     * @param request 前端入参；{@code null} 时由实现抛校验异常
     * @return 领域编辑命令
     */
    EditDiseaseCommand toUpdateCommand(UpdateDiagnosisCatalogRequest request);
}
