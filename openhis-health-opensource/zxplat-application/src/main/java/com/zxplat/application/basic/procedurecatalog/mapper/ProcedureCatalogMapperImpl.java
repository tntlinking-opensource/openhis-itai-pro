/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.procedurecatalog.mapper;

import com.zxplat.application.basic.procedurecatalog.dto.ProcedureCatalogDetailDto;
import com.zxplat.application.basic.procedurecatalog.dto.ProcedureCatalogDto;
import com.zxplat.application.basic.procedurecatalog.request.CreateProcedureCatalogRequest;
import com.zxplat.application.basic.procedurecatalog.request.UpdateProcedureCatalogRequest;
import com.zxplat.domain.clinical.proceduredefinition.command.AddProcedureDefinitionCommand;
import com.zxplat.domain.clinical.proceduredefinition.command.EditProcedureDefinitionCommand;
import com.zxplat.domain.clinical.proceduredefinition.entity.ProcedureDefinition;
import com.zxplat.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionDetailQueryModel;
import com.zxplat.domain.clinical.proceduredefinition.queries.model.ProcedureDefinitionPageItemQueryModel;
import com.zxplat.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.zxplat.domain.share.enums.PublicationStatus;
import com.zxplat.domain.terminology.service.CodeSystemQueryService;
import com.zxplat.framework.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link ProcedureCatalogMapper} 默认实现：列表/详情装配及写命令转换，展示字段依赖术语服务解析。
 */
@Component
@RequiredArgsConstructor
public class ProcedureCatalogMapperImpl implements ProcedureCatalogMapper {

    /** 手术目录来源码系名（术语下拉与展示文案解析）。 */
    private static final String PROCEDURE_SOURCE_CODE_SYSTEM =
        TerminologyCodeSystemNameConstants.PROCEDURE_DEFINITION_SOURCE;

    /** 术语读侧：解析来源/发布状态展示文案。 */
    private final CodeSystemQueryService codeSystemQueryService;

    /**
     * 将手术定义实体装配为列表行 DTO（含来源/状态展示文案与挂靠 AD）。
     *
     * @param entity 调用方保证可为 null；null 时返回 null
     * @return 列表行 DTO；entity 为 null 时返回 null
     */
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
        dto.setStatusEnum_enumText(
            codeSystemQueryService.getDisplayByCode(PublicationStatus.class, entity.getStatusEnum()));
        // 挂靠 AD：国临选品计费映射回显（BR-POC-010）
        dto.setActivityDefinitionId(entity.getActivityDefinitionId());
        return dto;
    }

    /**
     * 将分页行 QueryModel 装配为列表行 DTO（含挂靠 AD 回显）。
     *
     * @param model 调用方保证可为 null；null 时返回 null
     * @return 列表行 DTO；model 为 null 时返回 null
     */
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
        dto.setStatusEnum_enumText(
            codeSystemQueryService.getDisplayByCode(PublicationStatus.class, model.getStatusEnum()));
        dto.setYbFlag(toIntFlag(model.getYbFlag()));
        dto.setYbNo(model.getYbNo());
        dto.setClinicCode(model.getClinicCode());
        dto.setDescription(model.getDescription());
        dto.setPyStr(model.getPyStr());
        dto.setWbStr(model.getWbStr());
        dto.setYbMatchFlag(toIntFlag(model.getYbMatchFlag()));
        // 挂靠 AD：分页列表回显（BR-POC-010）
        dto.setActivityDefinitionId(model.getActivityDefinitionId());
        return dto;
    }

    /**
     * 将详情 QueryModel 装配为详情 DTO（字段同列表行，含挂靠 AD）。
     *
     * @param model 调用方保证可为 null；null 时返回 null
     * @return 详情 DTO；model 为 null 时返回 null
     */
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
        dto.setStatusEnum_enumText(
            codeSystemQueryService.getDisplayByCode(PublicationStatus.class, model.getStatusEnum()));
        // 挂靠 AD：详情回显（BR-POC-010）
        dto.setActivityDefinitionId(model.getActivityDefinitionId());
        return dto;
    }

    /**
     * 将新增请求映射为领域新增命令（含可选挂靠 AD；医保标记 Integer→Boolean）。
     *
     * @param request 调用方保证非空；空则抛校验异常
     * @return 新增命令
     */
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
        // 可选挂靠 AD：写入领域新增命令（BR-POC-010）
        command.setActivityDefinitionId(request.getActivityDefinitionId());
        return command;
    }

    /**
     * 将编辑请求映射为领域编辑命令（含可选挂靠 AD；null 表示不改存量挂靠）。
     *
     * @param request 调用方保证非空；空则抛校验异常
     * @return 编辑命令
     */
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
        // 非空改绑；null 本轮不改存量挂靠（与领域 Edit 口径一致）
        command.setActivityDefinitionId(request.getActivityDefinitionId());
        return command;
    }

    /**
     * 按来源码解析术语展示文案；概念不存在时返回 null。
     *
     * @param sourceCode 来源码
     * @return 展示文案；未配置时 null
     */
    private String resolveSourceText(String sourceCode) {
        var concept =
            codeSystemQueryService.findConceptByCodeSystemNameAndCode(PROCEDURE_SOURCE_CODE_SYSTEM, sourceCode);
        if (concept == null) {
            return null;
        }
        return concept.getDisplay();
    }

    /**
     * Boolean 医保/对码标记转为出参 0/1；null 保持 null。
     *
     * @param value 领域 Boolean 标记
     * @return 0/1 或 null
     */
    private Integer toIntFlag(Boolean value) {
        if (Boolean.TRUE.equals(value)) {
            return 1;
        }
        if (Boolean.FALSE.equals(value)) {
            return 0;
        }
        return null;
    }

    /**
     * 请求侧 0/1 标记转为领域 Boolean；null 保持 null；非 1 视为 false。
     *
     * @param value 请求 Integer 标记
     * @return Boolean 或 null
     */
    private Boolean toBoolFlag(Integer value) {
        if (value == null) {
            return null;
        }
        return value == 1;
    }
}
