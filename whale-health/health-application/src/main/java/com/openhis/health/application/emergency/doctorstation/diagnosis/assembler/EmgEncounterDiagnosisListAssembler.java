/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.application.emergency.doctorstation.diagnosis.assembler;

import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.CommonDiagnosisRowDto;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.dto.EncounterDiagnosisRowDto;
import com.openhis.health.application.outpatient.doctorstation.diagnosis.mapper.DoctorStationDiagnosisMapper;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.condition.entity.Condition;
import com.openhis.health.domain.clinical.condition.repository.ConditionRepository;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.DoctorStationConditionDefinitionRowModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.share.constants.TerminologyCodeSystemNameConstants;
import com.openhis.health.domain.terminology.service.CodeSystemQueryService;
import com.openhis.whale.core.application.dto.OptionDto;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 急诊就诊诊断列表装配：EncounterDiagnosis + Condition + ConditionDefinition（不读 OutpatientInfo 快照）。
 */
@Component
@RequiredArgsConstructor
public class EmgEncounterDiagnosisListAssembler {

    private final EncounterQueryService encounterQueryService;
    private final ConditionRepository conditionRepository;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final CodeSystemQueryService codeSystemQueryService;
    private final DoctorStationDiagnosisMapper doctorStationDiagnosisMapper;

    public List<EncounterDiagnosisRowDto> assembleEncounterDiagnosisRows(Long encounterId) {
        List<EncounterDiagnosisQueryModel> associations = encounterQueryService.listEncounterDiagnoses(encounterId);
        if (associations.isEmpty()) {
            return List.of();
        }
        List<Long> conditionIds = associations.stream()
            .map(EncounterDiagnosisQueryModel::getConditionId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, Condition> conditionById = new HashMap<>();
        if (!conditionIds.isEmpty()) {
            for (Condition c : conditionRepository.findByIdIn(conditionIds)) {
                if (c.getId() != null) {
                    conditionById.put(c.getId(), c);
                }
            }
        }
        List<Long> definitionIds = conditionById.values().stream()
            .map(Condition::getDefinitionId)
            .filter(Objects::nonNull)
            .distinct()
            .collect(Collectors.toList());
        Map<Long, DoctorStationConditionDefinitionRowModel> defById = new HashMap<>();
        if (!definitionIds.isEmpty()) {
            for (DoctorStationConditionDefinitionRowModel def :
                conditionDefinitionQueryService.listActiveDoctorStationRowsByIds(definitionIds)) {
                defById.put(def.getId(), def);
            }
        }
        Map<String, String> sourceLabels = buildSourceLabelMap();
        List<EncounterDiagnosisRowDto> rows = new ArrayList<>();
        List<EncounterDiagnosisQueryModel> sorted = new ArrayList<>(associations);
        sorted.sort(Comparator.comparing(
            a -> a.getRank() == null ? Integer.MAX_VALUE : a.getRank(),
            Comparator.naturalOrder()));
        int sortNo = 1;
        for (EncounterDiagnosisQueryModel assoc : sorted) {
            EncounterDiagnosisRowDto dto = new EncounterDiagnosisRowDto();
            dto.setEncounterDiagnosisId(assoc.getDiagnosisId());
            dto.setConditionId(assoc.getConditionId());
            dto.setMainDiagnosisFlag(Boolean.TRUE.equals(assoc.getPrimaryFlag()) ? 1 : 0);
            dto.setDiagnosisSortNo(assoc.getRank() != null ? assoc.getRank() : sortNo);
            dto.setDiagnosisDesc(assoc.getDescription());
            Condition condition = assoc.getConditionId() == null ? null : conditionById.get(assoc.getConditionId());
            if (condition != null) {
                dto.setConditionDefinitionId(condition.getDefinitionId());
                dto.setVerificationStatusEnum(parseVerificationUiCode(condition.getVerificationStatusEnum()));
                dto.setYbNo(condition.getYbNo());
                if (dto.getDiagnosisDesc() == null) {
                    dto.setDiagnosisDesc(condition.getDescription());
                }
            }
            if (condition != null && condition.getDefinitionId() != null) {
                DoctorStationConditionDefinitionRowModel def = defById.get(condition.getDefinitionId());
                if (def != null) {
                    CommonDiagnosisRowDto defRow =
                        doctorStationDiagnosisMapper.toCommonDiagnosisRowDto(def, sourceLabels);
                    if (defRow != null) {
                        dto.setConditionCode(defRow.getConditionCode());
                        dto.setConditionDefinitionName(defRow.getConditionDefinitionName());
                        dto.setDiagnosisKind(defRow.getDiagnosisKind());
                        dto.setDiagnosisKindText(defRow.getDiagnosisKindText());
                    }
                }
            }
            rows.add(dto);
            sortNo++;
        }
        return rows;
    }

    private Integer parseVerificationUiCode(String verificationStatusEnum) {
        if (verificationStatusEnum == null) {
            return null;
        }
        try {
            return Integer.parseInt(verificationStatusEnum.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private Map<String, String> buildSourceLabelMap() {
        Map<String, String> map = new HashMap<>();
        List<OptionDto> options =
            codeSystemQueryService.getOptionListByCodeSystemName(TerminologyCodeSystemNameConstants.CONDITION_DEFINITION_SOURCE);
        if (options == null) {
            return map;
        }
        for (OptionDto option : options) {
            if (option.getValue() != null) {
                map.put(option.getValue(), option.getLabel());
            }
        }
        return map;
    }
}
