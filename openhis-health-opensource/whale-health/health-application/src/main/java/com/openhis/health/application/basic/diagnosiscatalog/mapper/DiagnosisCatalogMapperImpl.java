/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.diagnosiscatalog.mapper;

import com.openhis.health.application.basic.diagnosiscatalog.service.DiagnosisCatalogAppService;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.application.basic.diagnosiscatalog.dto.DiagnosisCatalogDetailDto;
import com.openhis.health.application.basic.diagnosiscatalog.dto.DiagnosisCatalogDto;
import com.openhis.health.application.basic.diagnosiscatalog.request.CreateDiagnosisCatalogRequest;
import com.openhis.health.application.basic.diagnosiscatalog.request.UpdateDiagnosisCatalogRequest;
import com.openhis.health.domain.clinical.conditiondefinition.command.AddDiseaseCommand;
import com.openhis.health.domain.clinical.conditiondefinition.command.EditDiseaseCommand;
import com.openhis.health.domain.clinical.conditiondefinition.entity.ConditionDefinition;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DiseaseDetailQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DiseasePageItemQueryModel;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * {@link DiagnosisCatalogMapper} 默认实现：列表/详情装配及写命令转换，展示字段依赖术语服务解析。
 */
@Component
@RequiredArgsConstructor
public class DiagnosisCatalogMapperImpl implements DiagnosisCatalogMapper {

    /** 疾病来源术语集名，与 {@link DiagnosisCatalogAppService} 一致。 */
    private static final String CONDITION_SOURCE_CODE_SYSTEM = TerminologyCodeSystemNameConstants.CONDITION_DEFINITION_SOURCE;
    /** 医保诊断类型术语集名。 */
    private static final String DIAG_TYPE_CODE_SYSTEM = TerminologyCodeSystemNameConstants.DIAG_TYPE;

    private final CodeSystemQueryService codeSystemQueryService;

    /**
     * 将条件定义实体投影为疾病目录列表项 DTO，并补齐分类/状态/诊断类型等展示文案。
     *
     * @param entity 领域实体；{@code null} 时返回 {@code null}
     * @return 列表项 DTO
     */
    @Override
    public DiagnosisCatalogDto toDto(ConditionDefinition entity) {
        if (entity == null) {
            return null;
        }
        DiagnosisCatalogDto dto = new DiagnosisCatalogDto();
        dto.setId(entity.getId());
        dto.setSourceEnum(entity.getSourceEnum());
        dto.setSourceEnum_enumText(resolveSourceText(entity.getSourceEnum()));
        dto.setConditionCode(entity.getConditionCode());
        dto.setName(entity.getName());
        dto.setTypeCode(entity.getTypeCode());
        dto.setTypeCode_dictText(resolveDiagTypeText(entity.getTypeCode()));
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

    /**
     * 将疾病分页行读模型投影为列表项 DTO；入参为 {@code null} 时返回 {@code null}。
     *
     * @param model 分页行读模型
     * @return 列表项 DTO
     */
    @Override
    public DiagnosisCatalogDto toDto(DiseasePageItemQueryModel model) {
        if (model == null) {
            return null;
        }
        DiagnosisCatalogDto dto = new DiagnosisCatalogDto();
        dto.setId(model.getId());
        dto.setSourceEnum(model.getSourceEnum());
        dto.setSourceEnum_enumText(resolveSourceText(model.getSourceEnum()));
        dto.setConditionCode(model.getConditionCode());
        dto.setName(model.getName());
        dto.setStatusEnum(model.getStatusEnum());
        dto.setStatusEnum_enumText(codeSystemQueryService.getDisplayByCode(PublicationStatus.class, model.getStatusEnum()));
        dto.setYbFlag(toIntFlag(model.getYbFlag()));
        dto.setYbNo(model.getYbNo());
        dto.setClinicCode(model.getClinicCode());
        dto.setDescription(model.getDescription());
        return dto;
    }

    /**
     * 将疾病详情读模型投影为详情 DTO；入参为 {@code null} 时返回 {@code null}。
     *
     * @param model 详情读模型
     * @return 详情 DTO
     */
    @Override
    public DiagnosisCatalogDetailDto toDetailDto(DiseaseDetailQueryModel model) {
        if (model == null) {
            return null;
        }
        DiagnosisCatalogDetailDto dto = new DiagnosisCatalogDetailDto();
        dto.setId(model.getId());
        dto.setSourceEnum(model.getSourceEnum());
        dto.setSourceEnum_enumText(resolveSourceText(model.getSourceEnum()));
        dto.setConditionCode(model.getConditionCode());
        dto.setName(model.getName());
        dto.setPyStr(model.getPyStr());
        dto.setWbStr(model.getWbStr());
        dto.setTypeCode(model.getTypeCode());
        dto.setTypeCode_dictText(resolveDiagTypeText(model.getTypeCode()));
        dto.setDescription(model.getDescription());
        dto.setYbFlag(toIntFlag(model.getYbFlag()));
        dto.setYbNo(model.getYbNo());
        dto.setClinicCode(model.getClinicCode());
        dto.setYbMatchFlag(toIntFlag(model.getYbMatchFlag()));
        dto.setStatusEnum(model.getStatusEnum());
        dto.setStatusEnum_enumText(codeSystemQueryService.getDisplayByCode(PublicationStatus.class, model.getStatusEnum()));
        return dto;
    }

    /**
     * 将新增疾病目录请求装配为领域「新增疾病」命令；请求体为空时抛出校验异常。
     *
     * @param request 创建请求
     * @return 领域新增命令
     * @throws ValidationException 请求体为空时
     */
    @Override
    public AddDiseaseCommand toCreateCommand(CreateDiagnosisCatalogRequest request) {
        if (request == null) {
            throw new ValidationException("新增疾病目录失败：请求体不能为空");
        }
        AddDiseaseCommand command = new AddDiseaseCommand();
        command.setSourceEnum(request.getSourceEnum());
        command.setConditionCode(request.getConditionCode());
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

    /**
     * 将更新疾病目录请求装配为领域「编辑疾病」命令；请求体为空时抛出校验异常。
     *
     * @param request 更新请求
     * @return 领域编辑命令
     * @throws ValidationException 请求体为空时
     */
    @Override
    public EditDiseaseCommand toUpdateCommand(UpdateDiagnosisCatalogRequest request) {
        if (request == null) {
            throw new ValidationException("更新疾病目录失败：请求体不能为空");
        }
        EditDiseaseCommand command = new EditDiseaseCommand();
        command.setId(request.getId());
        command.setSourceEnum(request.getSourceEnum());
        command.setConditionCode(request.getConditionCode());
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

    /**
     * 解析疾病来源码为术语 display；码不存在时返回 {@code null}，由前端决定是否回退展示 code。
     *
     * @param sourceCode 来源枚举码
     * @return 展示名或 {@code null}
     */
    private String resolveSourceText(String sourceCode) {
        var concept = codeSystemQueryService.findConceptByCodeSystemNameAndCode(CONDITION_SOURCE_CODE_SYSTEM, sourceCode);
        if (concept == null) {
            return null;
        }
        return concept.getDisplay();
    }

    /**
     * 解析医保诊断类型码为术语 display。
     *
     * @param diagTypeCode 诊断类型码
     * @return 展示名或 {@code null}
     */
    private String resolveDiagTypeText(String diagTypeCode) {
        var concept = codeSystemQueryService.findConceptByCodeSystemNameAndCode(DIAG_TYPE_CODE_SYSTEM, diagTypeCode);
        if (concept == null) {
            return null;
        }
        return concept.getDisplay();
    }

    /**
     * 领域布尔转前端 0/1 整型标志。
     *
     * @param value 领域布尔
     * @return 1、0 或 {@code null}
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
     * 前端 0/1 整型转领域布尔：仅 1 为 true。
     *
     * @param value 前端标志
     * @return 领域布尔或 {@code null}
     */
    private Boolean toBoolFlag(Integer value) {
        if (value == null) {
            return null;
        }
        return value == 1;
    }
}
