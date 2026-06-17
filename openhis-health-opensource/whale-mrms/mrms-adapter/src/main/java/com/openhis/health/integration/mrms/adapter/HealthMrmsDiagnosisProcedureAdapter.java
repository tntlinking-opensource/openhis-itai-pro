/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.mrms.adapter;

import com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.condition.queries.model.ConditionBatchItemQueryModel;
import com.openhis.health.domain.clinical.condition.service.ConditionQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.whale.mrms.port.DiagnosisProcedurePort;
import com.openhis.whale.mrms.port.model.DiagnosisLineSnapshot;
import com.openhis.whale.mrms.port.model.ProcedureLineSnapshot;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * health 对 MRMS {@link DiagnosisProcedurePort} 的实现：入出院诊断与手术操作行。
 */
@Component
@Primary
public class HealthMrmsDiagnosisProcedureAdapter implements DiagnosisProcedurePort {

    private final EncounterQueryService encounterQueryService;
    private final ConditionQueryService conditionQueryService;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;

    public HealthMrmsDiagnosisProcedureAdapter(
            EncounterQueryService encounterQueryService,
            ConditionQueryService conditionQueryService,
            ConditionDefinitionQueryService conditionDefinitionQueryService) {
        this.encounterQueryService = encounterQueryService;
        this.conditionQueryService = conditionQueryService;
        this.conditionDefinitionQueryService = conditionDefinitionQueryService;
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
        return Collections.emptyList();
    }

    private List<DiagnosisLineSnapshot> mapDiagnoses(List<EncounterDiagnosisQueryModel> rows, boolean dischargeView) {
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, ConditionBatchItemQueryModel> conditionMap = loadConditions(rows);
        Map<Long, ConditionDefinitionBatchItemQueryModel> definitionMap = loadDefinitions(conditionMap);
        List<DiagnosisLineSnapshot> result = new ArrayList<>();
        int sort = 0;
        for (EncounterDiagnosisQueryModel row : rows) {
            DiagnosisLineSnapshot snapshot = new DiagnosisLineSnapshot();
            if (row.getRank() != null) {
                snapshot.setSortNo(row.getRank());
            } else {
                snapshot.setSortNo(sort++);
            }
            snapshot.setPrimary(Boolean.TRUE.equals(row.getPrimaryFlag()));
            ConditionBatchItemQueryModel condition = conditionMap.get(row.getConditionId());
            if (condition != null) {
                ConditionDefinitionBatchItemQueryModel definition = definitionMap.get(condition.getDefinitionId());
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

    private Map<Long, ConditionBatchItemQueryModel> loadConditions(List<EncounterDiagnosisQueryModel> rows) {
        Set<Long> conditionIds = new HashSet<>();
        for (EncounterDiagnosisQueryModel row : rows) {
            if (row.getConditionId() != null) {
                conditionIds.add(row.getConditionId());
            }
        }
        if (conditionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, ConditionBatchItemQueryModel> map = new HashMap<>();
        for (ConditionBatchItemQueryModel item :
                conditionQueryService.getConditionListById(new ArrayList<>(conditionIds))) {
            map.put(item.getId(), item);
        }
        return map;
    }

    private Map<Long, ConditionDefinitionBatchItemQueryModel> loadDefinitions(
            Map<Long, ConditionBatchItemQueryModel> conditionMap) {
        Set<Long> definitionIds = new HashSet<>();
        for (ConditionBatchItemQueryModel condition : conditionMap.values()) {
            if (condition.getDefinitionId() != null) {
                definitionIds.add(condition.getDefinitionId());
            }
        }
        if (definitionIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, ConditionDefinitionBatchItemQueryModel> map = new HashMap<>();
        for (ConditionDefinitionBatchItemQueryModel item :
                conditionDefinitionQueryService.getConditionDefinitionListByIds(new ArrayList<>(definitionIds))) {
            map.put(item.getId(), item);
        }
        return map;
    }
}
