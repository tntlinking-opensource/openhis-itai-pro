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
 */
public interface ProcedureCatalogMapper {

    ProcedureCatalogDto toDto(ProcedureDefinition entity);

    ProcedureCatalogDto toDto(ProcedureDefinitionPageItemQueryModel model);

    ProcedureCatalogDetailDto toDetailDto(ProcedureDefinitionDetailQueryModel model);

    AddProcedureDefinitionCommand toCreateCommand(CreateProcedureCatalogRequest request);

    EditProcedureDefinitionCommand toUpdateCommand(UpdateProcedureCatalogRequest request);
}
