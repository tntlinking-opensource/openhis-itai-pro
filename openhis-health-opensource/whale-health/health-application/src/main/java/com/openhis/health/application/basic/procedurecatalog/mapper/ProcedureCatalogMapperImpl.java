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
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link ProcedureCatalogMapper} 默认实现：列表/详情装配及写命令转换，展示字段依赖术语服务解析。
 */
@Component
@RequiredArgsConstructor
public class ProcedureCatalogMapperImpl implements ProcedureCatalogMapper {

    private static final String PROCEDURE_SOURCE_CODE_SYSTEM = TerminologyCodeSystemNameConstants.PROCEDURE_DEFINITION_SOURCE;

    private final CodeSystemQueryService codeSystemQueryService;

    @Override
    public ProcedureCatalogDto toDto(ProcedureDefinition entity) {
        if (entity == null) {
            return null;
        }
        ProcedureCatalogDto dto = new ProcedureCatalogDto();
        dto.setId(entity.getId());
        dto.setSourceEnum(entity.getSourceEnum());
        dto.setSourceEnum_enumText(resolveSourceText(entity.getSourceEnum()));
        dto.setProcedureCode(entity.getProcedureCode());
        dto.setName(entity.getName());
        dto.setTypeCode(entity.getTypeCode());
        dto.setTypeCode_dictText(entity.getTypeCode());
        dto.setDescription(entity.getDescription());
        dto.setPyStr(entity.getPyStr());
        dto.setWbStr(entity.getWbStr());
        dto.setYbFlag(toIntFlag(entity.getYbFlag()));
        dto.setYbNo(entity.getYbNo());
        dto.setClinicCode(entity.getClinicCode());
        dto.setYbMatchFlag(toIntFlag(entity.getYbMatchFlag()));
        dto.setStatusEnum(entity.getStatusEnum());
        dto.setStatusEnum_enumText(codeSystemQueryService.getDisplayByCode(PublicationStatus.class, entity.getStatusEnum()));
        return dto;
    }

    @Override
    public ProcedureCatalogDto toDto(ProcedureDefinitionPageItemQueryModel model) {
        if (model == null) {
            return null;
        }
        ProcedureCatalogDto dto = new ProcedureCatalogDto();
        dto.setId(model.getId());
        dto.setSourceEnum(model.getSourceEnum());
        dto.setSourceEnum_enumText(resolveSourceText(model.getSourceEnum()));
        dto.setProcedureCode(model.getProcedureCode());
        dto.setName(model.getName());
        dto.setTypeCode(model.getTypeCode());
        dto.setTypeCode_dictText(model.getTypeCode());
        dto.setStatusEnum(model.getStatusEnum());
        dto.setStatusEnum_enumText(codeSystemQueryService.getDisplayByCode(PublicationStatus.class, model.getStatusEnum()));
        dto.setYbFlag(toIntFlag(model.getYbFlag()));
        dto.setYbNo(model.getYbNo());
        dto.setClinicCode(model.getClinicCode());
        dto.setDescription(model.getDescription());
        return dto;
    }

    @Override
    public ProcedureCatalogDetailDto toDetailDto(ProcedureDefinitionDetailQueryModel model) {
        if (model == null) {
            return null;
        }
        ProcedureCatalogDetailDto dto = new ProcedureCatalogDetailDto();
        dto.setId(model.getId());
        dto.setSourceEnum(model.getSourceEnum());
        dto.setSourceEnum_enumText(resolveSourceText(model.getSourceEnum()));
        dto.setProcedureCode(model.getProcedureCode());
        dto.setName(model.getName());
        dto.setPyStr(model.getPyStr());
        dto.setWbStr(model.getWbStr());
        dto.setTypeCode(model.getTypeCode());
        dto.setTypeCode_dictText(model.getTypeCode());
        dto.setDescription(model.getDescription());
        dto.setYbFlag(toIntFlag(model.getYbFlag()));
        dto.setYbNo(model.getYbNo());
        dto.setClinicCode(model.getClinicCode());
        dto.setYbMatchFlag(toIntFlag(model.getYbMatchFlag()));
        dto.setStatusEnum(model.getStatusEnum());
        dto.setStatusEnum_enumText(codeSystemQueryService.getDisplayByCode(PublicationStatus.class, model.getStatusEnum()));
        return dto;
    }

    @Override
    public AddProcedureDefinitionCommand toCreateCommand(CreateProcedureCatalogRequest request) {
        if (request == null) {
            throw new ValidationException("新增手术目录失败：请求体不能为空");
        }
        AddProcedureDefinitionCommand command = new AddProcedureDefinitionCommand();
        command.setSourceEnum(request.getSourceEnum());
        command.setProcedureCode(request.getProcedureCode());
        command.setName(request.getName());
        command.setPyStr(request.getPyStr());
        command.setWbStr(request.getWbStr());
        command.setTypeCode(request.getTypeCode());
        command.setDescription(request.getDescription());
        command.setYbFlag(toBoolFlag(request.getYbFlag()));
        command.setYbNo(request.getYbNo());
        command.setClinicCode(request.getClinicCode());
        command.setYbMatchFlag(toBoolFlag(request.getYbMatchFlag()));
        command.setStatusEnum(request.getStatusEnum());
        return command;
    }

    @Override
    public EditProcedureDefinitionCommand toUpdateCommand(UpdateProcedureCatalogRequest request) {
        if (request == null) {
            throw new ValidationException("更新手术目录失败：请求体不能为空");
        }
        EditProcedureDefinitionCommand command = new EditProcedureDefinitionCommand();
        command.setId(request.getId());
        command.setSourceEnum(request.getSourceEnum());
        command.setProcedureCode(request.getProcedureCode());
        command.setName(request.getName());
        command.setPyStr(request.getPyStr());
        command.setWbStr(request.getWbStr());
        command.setTypeCode(request.getTypeCode());
        command.setDescription(request.getDescription());
        command.setYbFlag(toBoolFlag(request.getYbFlag()));
        command.setYbNo(request.getYbNo());
        command.setClinicCode(request.getClinicCode());
        command.setYbMatchFlag(toBoolFlag(request.getYbMatchFlag()));
        command.setStatusEnum(request.getStatusEnum());
        return command;
    }

    private String resolveSourceText(String sourceCode) {
        var concept = codeSystemQueryService.findConceptByCodeSystemNameAndCode(PROCEDURE_SOURCE_CODE_SYSTEM, sourceCode);
        if (concept == null) {
            return null;
        }
        return concept.getDisplay();
    }

    private Integer toIntFlag(Boolean value) {
        if (Boolean.TRUE.equals(value)) {
            return 1;
        }
        if (Boolean.FALSE.equals(value)) {
            return 0;
        }
        return null;
    }

    private Boolean toBoolFlag(Integer value) {
        if (value == null) {
            return null;
        }
        return value == 1;
    }
}
