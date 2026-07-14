/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.doctorstation.chinesemedicine.assembler;

import com.openhis.health.application.inpatient.doctorstation.chinesemedicine.dto.TcmDiagnosisGroupRowDto;
import com.openhis.health.application.outpatient.doctorstation.chinesemedicine.assembler.TcmDiagnosisAssembler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * IF-IP-TCM-30：住院中医病证组读侧聚合（委托门诊 {@link TcmDiagnosisAssembler}）。
 */
@Component
@RequiredArgsConstructor
public class InpatientTcmDiagnosisAssembler {

    private final TcmDiagnosisAssembler tcmDiagnosisAssembler;

    /**
     * 将就诊中医关联与诊断快照 JSON 聚合为病证组展示行。
     *
     * @param encounterId   就诊主键
     * @param diagnosisJson 就诊诊断快照 JSON
     * @return 病证组列表
     */
    public List<TcmDiagnosisGroupRowDto> groupTcmDiagnosisRows(Long encounterId, String diagnosisJson) {
        List<com.openhis.health.application.outpatient.doctorstation.chinesemedicine.dto.TcmDiagnosisGroupRowDto> rows =
            tcmDiagnosisAssembler.groupTcmDiagnosisRows(encounterId, diagnosisJson);
        List<TcmDiagnosisGroupRowDto> result = new java.util.ArrayList<>();
        for (com.openhis.health.application.outpatient.doctorstation.chinesemedicine.dto.TcmDiagnosisGroupRowDto row : rows) {
            TcmDiagnosisGroupRowDto dto = new TcmDiagnosisGroupRowDto();
            dto.setTcmSyndromeGroup(row.getTcmSyndromeGroup());
            dto.setDiseaseConditionId(row.getDiseaseConditionId());
            dto.setDiseaseDefinitionId(row.getDiseaseDefinitionId());
            dto.setDiseaseCode(row.getDiseaseCode());
            dto.setDiseaseName(row.getDiseaseName());
            dto.setSyndromeConditionId(row.getSyndromeConditionId());
            dto.setSyndromeDefinitionId(row.getSyndromeDefinitionId());
            dto.setSyndromeCode(row.getSyndromeCode());
            dto.setSyndromeName(row.getSyndromeName());
            dto.setPrimaryFlag(row.getPrimaryFlag());
            dto.setRank(row.getRank());
            dto.setMedicalTypeCode(row.getMedicalTypeCode());
            dto.setDiagnosisDesc(row.getDiagnosisDesc());
            result.add(dto);
        }
        return result;
    }
}
