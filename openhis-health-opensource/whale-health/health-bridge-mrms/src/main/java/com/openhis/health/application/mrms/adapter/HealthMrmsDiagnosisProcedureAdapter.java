package com.openhis.health.application.mrms.adapter;

import com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.condition.entity.Condition;
import com.openhis.health.domain.clinical.condition.repository.ConditionRepository;
import com.openhis.health.domain.clinical.conditiondefinition.entity.ConditionDefinition;
import com.openhis.health.domain.clinical.conditiondefinition.repository.ConditionDefinitionRepository;
import com.openhis.whale.mrms.domain.port.DiagnosisProcedurePort;
import com.openhis.whale.mrms.domain.port.model.DiagnosisLineSnapshot;
import com.openhis.whale.mrms.domain.port.model.ProcedureLineSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * health 对 MRMS {@link DiagnosisProcedurePort} 的实现。
 */
@Component
public class HealthMrmsDiagnosisProcedureAdapter implements DiagnosisProcedurePort {

    private final EncounterQueryService encounterQueryService;
    private final ConditionRepository conditionRepository;
    private final ConditionDefinitionRepository conditionDefinitionRepository;

    public HealthMrmsDiagnosisProcedureAdapter(EncounterQueryService encounterQueryService,
            ConditionRepository conditionRepository,
            ConditionDefinitionRepository conditionDefinitionRepository) {
        this.encounterQueryService = encounterQueryService;
        this.conditionRepository = conditionRepository;
        this.conditionDefinitionRepository = conditionDefinitionRepository;
    }

    @Override
    public List<DiagnosisLineSnapshot> listAdmissionDiagnoses(long encounterId) {
        return mapDiagnoses(encounterQueryService.listEncounterDiagnoses(encounterId), false);
    }

    @Override
    public List<DiagnosisLineSnapshot> listDischargeDiagnoses(long encounterId) {
        return mapDiagnoses(encounterQueryService.listEncounterDiagnoses(encounterId), true);
    }

    @Override
    public List<ProcedureLineSnapshot> listProcedures(long encounterId) {
        // Wave 1：手术操作事实表尚未统一对接，返回空列表
        return Collections.emptyList();
    }

    private List<DiagnosisLineSnapshot> mapDiagnoses(List<EncounterDiagnosisQueryModel> rows, boolean dischargeView) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Condition> conditionMap = loadConditions(rows);
        Map<Long, ConditionDefinition> definitionMap = loadDefinitions(conditionMap);
        List<DiagnosisLineSnapshot> result = new ArrayList<>();
        int sort = 0;
        for (EncounterDiagnosisQueryModel row : rows) {
            DiagnosisLineSnapshot snapshot = new DiagnosisLineSnapshot();
            snapshot.setSortNo(row.getRank() != null ? row.getRank() : sort++);
            snapshot.setPrimary(Boolean.TRUE.equals(row.getPrimaryFlag()));
            Condition condition = conditionMap.get(row.getConditionId());
            if (condition != null) {
                ConditionDefinition definition = definitionMap.get(condition.getDefinitionId());
                if (definition != null) {
                    snapshot.setDiagCode(definition.getConditionCode());
                    snapshot.setDiagName(definition.getName());
                } else if (row.getDescription() != null) {
                    snapshot.setDiagName(row.getDescription());
                }
            } else if (row.getDescription() != null) {
                snapshot.setDiagName(row.getDescription());
            }
            if (dischargeView || snapshot.isPrimary()) {
                result.add(snapshot);
            }
        }
        return result;
    }

    private Map<Long, Condition> loadConditions(List<EncounterDiagnosisQueryModel> rows) {
        List<Long> conditionIds = rows.stream()
                .map(EncounterDiagnosisQueryModel::getConditionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (conditionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Condition> map = new HashMap<>();
        for (Condition condition : conditionRepository.findAllById(conditionIds)) {
            if (!Boolean.TRUE.equals(condition.getIsDeleted())) {
                map.put(condition.getId(), condition);
            }
        }
        return map;
    }

    private Map<Long, ConditionDefinition> loadDefinitions(Map<Long, Condition> conditionMap) {
        List<Long> definitionIds = conditionMap.values().stream()
                .map(Condition::getDefinitionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (definitionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, ConditionDefinition> map = new HashMap<>();
        for (ConditionDefinition definition : conditionDefinitionRepository.findAllById(definitionIds)) {
            if (!Boolean.TRUE.equals(definition.getIsDeleted())) {
                map.put(definition.getId(), definition);
            }
        }
        return map;
    }
}
