/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.peis.config.diagnosis.mapper;

import com.openhis.health.application.peis.config.diagnosis.dto.PeisCommonResultDto;
import com.openhis.health.application.peis.config.diagnosis.dto.PeisDiagnosisTemplateDto;
import com.openhis.health.application.peis.config.diagnosis.request.SaveCommonResultRequest;
import com.openhis.health.application.peis.config.diagnosis.request.SaveDiagnosisTemplateRequest;
import com.openhis.health.domain.clinical.conditiondefinition.command.AddDiseaseCommand;
import com.openhis.health.domain.clinical.conditiondefinition.command.EditDiseaseCommand;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DiseaseDetailQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DiseasePageItemQueryModel;
import com.openhis.health.domain.peis.commonresult.command.SavePeisCommonResultCommand;
import com.openhis.health.domain.peis.commonresult.entity.PeisCommonResult;
import com.openhis.health.domain.peis.commonresult.queries.model.PeisCommonResultModel;
import com.openhis.health.domain.share.constants.ObservationDefinitionConstants;
import com.openhis.health.domain.share.enums.PublicationStatus;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.stereotype.Component;

/**
 * {@link PeisDiagnosisConfigMapper} 默认实现（AS-PEIS-CFG-002）。
 */
@Component
public class PeisDiagnosisConfigMapperImpl implements PeisDiagnosisConfigMapper {

    @Override
    public PeisDiagnosisTemplateDto toDiagnosisTemplateDto(DiseasePageItemQueryModel source) {
        if (source == null) {
            return null;
        }
        PeisDiagnosisTemplateDto dto = new PeisDiagnosisTemplateDto();
        dto.setDefinitionId(source.getId());
        dto.setCode(source.getConditionCode());
        dto.setDisplay(source.getName());
        dto.setCategoryCode(source.getSourceEnum());
        dto.setStatusEnum(toPeisLifecycleStatus(source.getStatusEnum()));
        return dto;
    }

    @Override
    public PeisDiagnosisTemplateDto toDiagnosisTemplateDto(DiseaseDetailQueryModel source) {
        if (source == null) {
            return null;
        }
        PeisDiagnosisTemplateDto dto = new PeisDiagnosisTemplateDto();
        dto.setDefinitionId(source.getId());
        dto.setCode(source.getConditionCode());
        dto.setDisplay(source.getName());
        dto.setCategoryCode(source.getSourceEnum());
        dto.setStatusEnum(toPeisLifecycleStatus(source.getStatusEnum()));
        return dto;
    }

    @Override
    public AddDiseaseCommand toAddDiseaseCommand(SaveDiagnosisTemplateRequest request, String sourceEnum) {
        AddDiseaseCommand command = new AddDiseaseCommand();
        command.setSourceEnum(sourceEnum);
        command.setConditionCode(request.getCode());
        command.setName(request.getDisplay());
        // categoryCode 在 PEIS 侧表示 source 展示，不写入 typeCode
        command.setStatusEnum(toPublicationStatusCode(request.getStatusEnum()));
        return command;
    }

    @Override
    public EditDiseaseCommand toEditDiseaseCommand(SaveDiagnosisTemplateRequest request, String sourceEnum) {
        EditDiseaseCommand command = new EditDiseaseCommand();
        command.setId(request.getDefinitionId());
        command.setSourceEnum(sourceEnum);
        command.setConditionCode(request.getCode());
        command.setName(request.getDisplay());
        command.setStatusEnum(toPublicationStatusCode(request.getStatusEnum()));
        return command;
    }

    @Override
    public PeisCommonResultDto toCommonResultDto(PeisCommonResultModel source) {
        if (source == null) {
            return null;
        }
        PeisCommonResultDto dto = new PeisCommonResultDto();
        dto.setId(source.getId());
        dto.setObservationDefinitionId(source.getObservationDefinitionId());
        dto.setDeptOrgId(source.getDeptOrgId());
        dto.setDisplay(source.getDisplay());
        dto.setValueString(source.getValueString());
        dto.setInterpretationCode(source.getInterpretationCode());
        dto.setSortNum(source.getSortNum());
        dto.setStatusEnum(source.getStatusEnum());
        return dto;
    }

    @Override
    public PeisCommonResultDto toCommonResultDto(PeisCommonResult entity) {
        if (entity == null) {
            return null;
        }
        PeisCommonResultDto dto = new PeisCommonResultDto();
        dto.setId(entity.getId());
        dto.setObservationDefinitionId(entity.getObservationDefinitionId());
        dto.setDeptOrgId(entity.getDeptOrgId());
        dto.setDisplay(entity.getDisplay());
        dto.setValueString(entity.getValueString());
        dto.setInterpretationCode(entity.getInterpretationCode());
        dto.setSortNum(entity.getSortNum());
        dto.setStatusEnum(entity.getStatusEnum());
        return dto;
    }

    @Override
    public SavePeisCommonResultCommand toSaveCommonResultCommand(
            SaveCommonResultRequest request, Long tenantId) {
        SavePeisCommonResultCommand command = new SavePeisCommonResultCommand();
        command.setId(request.getId());
        command.setTenantId(tenantId);
        command.setObservationDefinitionId(request.getObservationDefinitionId());
        command.setDeptOrgId(request.getDeptOrgId());
        command.setDisplay(request.getDisplay());
        command.setValueString(request.getValueString());
        command.setInterpretationCode(request.getInterpretationCode());
        command.setSortNum(request.getSortNum());
        command.setStatusEnum(request.getStatusEnum());
        return command;
    }

    private String toPeisLifecycleStatus(String publicationStatusCode) {
        if (PublicationStatus.ACTIVE.getCode().equals(publicationStatusCode)) {
            return ObservationDefinitionConstants.STATUS_ACTIVE;
        }
        if (PublicationStatus.INACTIVE.getCode().equals(publicationStatusCode)) {
            return ObservationDefinitionConstants.STATUS_INACTIVE;
        }
        return publicationStatusCode;
    }

    private String toPublicationStatusCode(String peisStatusEnum) {
        if (StringUtils.isEmpty(peisStatusEnum)) {
            return PublicationStatus.ACTIVE.getCode();
        }
        String normalized = peisStatusEnum.trim();
        if (ObservationDefinitionConstants.STATUS_ACTIVE.equalsIgnoreCase(normalized)
            || PublicationStatus.ACTIVE.getCode().equalsIgnoreCase(normalized)) {
            return PublicationStatus.ACTIVE.getCode();
        }
        if (ObservationDefinitionConstants.STATUS_INACTIVE.equalsIgnoreCase(normalized)
            || PublicationStatus.INACTIVE.getCode().equalsIgnoreCase(normalized)) {
            return PublicationStatus.INACTIVE.getCode();
        }
        return normalized.toUpperCase();
    }
}
