/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.procedurecatalog.mapper;

import com.openhis.health.application.basic.procedurecatalog.dto.ProcedureCatalogDetailDto;
import com.openhis.health.application.basic.procedurecatalog.dto.ProcedureCatalogDto;
import com.openhis.health.application.basic.procedurecatalog.request.CreateProcedureCatalogRequest;
import com.openhis.health.application.basic.procedurecatalog.request.UpdateProcedureCatalogRequest;
import com.openhis.health.domain.clinical.proceduredefinition.command.AddProcedureDefinitionCommand;
import com.openhis.health.domain.clinical.proceduredefinition.command.EditProcedureDefinitionCommand;
import com.openhis.health.domain.clinical.proceduredefinition.entity.ProcedureDefinition;
import com.openhis.health.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionDetailQueryModel;
import com.openhis.health.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionPageItemQueryModel;

/**
 * 手术操作目录应用层与临床「手术定义」聚合读写模型之间的装配契约。
 *
 * <p>仅做 Request↔Command、QueryModel/Entity↔DTO 字段搬运；禁止承载业务流程判断。</p>
 */
public interface ProcedureCatalogMapper {

    /**
     * 将手术定义实体装配为列表行 DTO（含来源/状态展示文案与挂靠 AD）。
     *
     * @param entity 调用方保证可为 null；null 时返回 null
     * @return 列表行 DTO；entity 为 null 时返回 null
     */
    ProcedureCatalogDto toDto(ProcedureDefinition entity);

    /**
     * 将分页行 QueryModel 装配为列表行 DTO（含挂靠 AD 回显）。
     *
     * @param model 调用方保证可为 null；null 时返回 null
     * @return 列表行 DTO；model 为 null 时返回 null
     */
    ProcedureCatalogDto toDto(ProcedureDefinitionPageItemQueryModel model);

    /**
     * 将详情 QueryModel 装配为详情 DTO（字段同列表行，含挂靠 AD）。
     *
     * @param model 调用方保证可为 null；null 时返回 null
     * @return 详情 DTO；model 为 null 时返回 null
     */
    ProcedureCatalogDetailDto toDetailDto(ProcedureDefinitionDetailQueryModel model);

    /**
     * 将新增请求映射为领域新增命令（含可选挂靠 AD；医保标记 Integer→Boolean）。
     *
     * @param request 调用方保证非空；空则抛校验异常
     * @return 新增命令
     */
    AddProcedureDefinitionCommand toCreateCommand(CreateProcedureCatalogRequest request);

    /**
     * 将编辑请求映射为领域编辑命令（含可选挂靠 AD；null 表示不改存量挂靠）。
     *
     * @param request 调用方保证非空；空则抛校验异常
     * @return 编辑命令
     */
    EditProcedureDefinitionCommand toUpdateCommand(UpdateProcedureCatalogRequest request);
}
