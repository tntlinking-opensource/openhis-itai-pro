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

/**
 * PEIS 诊断与常见结果 DTO/命令映射（AS-PEIS-CFG-002）。
 */
public interface PeisDiagnosisConfigMapper {

    PeisDiagnosisTemplateDto toDiagnosisTemplateDto(DiseasePageItemQueryModel source);

    PeisDiagnosisTemplateDto toDiagnosisTemplateDto(DiseaseDetailQueryModel source);

    AddDiseaseCommand toAddDiseaseCommand(SaveDiagnosisTemplateRequest request, String sourceEnum);

    EditDiseaseCommand toEditDiseaseCommand(SaveDiagnosisTemplateRequest request, String sourceEnum);

    PeisCommonResultDto toCommonResultDto(PeisCommonResultModel source);

    PeisCommonResultDto toCommonResultDto(PeisCommonResult entity);

    SavePeisCommonResultCommand toSaveCommonResultCommand(SaveCommonResultRequest request, Long tenantId);
}
