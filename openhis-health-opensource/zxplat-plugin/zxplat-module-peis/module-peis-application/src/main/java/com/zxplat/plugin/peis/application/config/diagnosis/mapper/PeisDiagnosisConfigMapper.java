/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.peis.application.config.diagnosis.mapper;

import com.zxplat.plugin.peis.application.config.diagnosis.dto.PeisCommonResultDto;
import com.zxplat.plugin.peis.application.config.diagnosis.dto.PeisDiagnosisTemplateDto;
import com.zxplat.plugin.peis.application.config.diagnosis.request.SaveCommonResultRequest;
import com.zxplat.plugin.peis.application.config.diagnosis.request.SaveDiagnosisTemplateRequest;
import com.zxplat.domain.clinical.conditiondefinition.command.AddDiseaseCommand;
import com.zxplat.domain.clinical.conditiondefinition.command.EditDiseaseCommand;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.DiseaseDetailQueryModel;
import com.zxplat.domain.clinical.conditiondefinition.queries.model.DiseasePageItemQueryModel;
import com.zxplat.plugin.peis.domain.commonresult.command.SavePeisCommonResultCommand;
import com.zxplat.plugin.peis.domain.commonresult.entity.PeisCommonResult;
import com.zxplat.plugin.peis.domain.commonresult.queries.model.PeisCommonResultModel;

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
