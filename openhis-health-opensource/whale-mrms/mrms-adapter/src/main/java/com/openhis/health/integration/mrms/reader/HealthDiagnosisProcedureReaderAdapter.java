/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.mrms.reader;

import com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.clinical.condition.queries.model.ConditionBatchItemQueryModel;
import com.openhis.health.domain.clinical.condition.service.ConditionQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.share.constants.ServiceRequestCategoryConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.IptPatientDiseaseDiagType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import com.openhis.health.integration.mrms.emr.EmrAdmissionConditionResolver;
import com.openhis.whale.mrms.contracts.reader.HealthDiagnosisProcedureReader;
import com.openhis.whale.mrms.contracts.snapshot.DiagnosisLineSnapshot;
import com.openhis.whale.mrms.contracts.snapshot.ProcedureLineSnapshot;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * {@link HealthDiagnosisProcedureReader} 实现：对接 health 就诊诊断关联 + 病情/目录读模型。
 */
@Component
@RequiredArgsConstructor
public class HealthDiagnosisProcedureReaderAdapter implements HealthDiagnosisProcedureReader {

    private final EncounterQueryService encounterQueryService;
    private final ConditionQueryService conditionQueryService;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final ProcedureQueryService procedureQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final EmrAdmissionConditionResolver emrAdmissionConditionResolver;

    @Override
    public List<DiagnosisLineSnapshot> listDischargeDiagnoses(Long encounterId) {
        return listDiagnosesByScope(encounterId, Scope.DISCHARGE);
    }

    @Override
    public List<DiagnosisLineSnapshot> listAdmissionDiagnoses(Long encounterId) {
        return listDiagnosesByScope(encounterId, Scope.ADMISSION);
    }

    @Override
    public List<ProcedureLineSnapshot> listProcedures(Long encounterId) {
        if (encounterId == null) {
            return Collections.emptyList();
        }
        List<ProcedureRecordQueryModel> completedRows =
                procedureQueryService.listNetRemainingCompletedProcedureRecords(encounterId, null, null);
        if (completedRows.isEmpty()) {
            return Collections.emptyList();
        }
        completedRows.sort(Comparator
                .comparing(ProcedureRecordQueryModel::getOccurrenceTime, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ProcedureRecordQueryModel::getId, Comparator.nullsLast(Comparator.naturalOrder())));

        List<Long> serviceRequestIds = new ArrayList<>();
        List<Long> procedureIds = new ArrayList<>();
        for (ProcedureRecordQueryModel row : completedRows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            procedureIds.add(row.getId());
            if (TableNameConstants.WKF_SERVICE_REQUEST.equals(row.getRequestTable()) && row.getRequestId() != null) {
                serviceRequestIds.add(row.getRequestId());
            }
        }
        Map<Long, Procedure> procedureById = indexProcedures(procedureQueryService.listByIds(procedureIds));
        Map<Long, ServiceRequestListModel> serviceRequestById =
                serviceRequestQueryService.mapListModelsByIds(serviceRequestIds);
        Map<Long, ActivityDefinition> activityById = loadActivities(serviceRequestById);

        List<ProcedureLineSnapshot> result = new ArrayList<>();
        Set<Long> seenServiceRequestIds = new LinkedHashSet<>();
        for (ProcedureRecordQueryModel row : completedRows) {
            if (row == null || !TableNameConstants.WKF_SERVICE_REQUEST.equals(row.getRequestTable())) {
                continue;
            }
            Long requestId = row.getRequestId();
            if (requestId == null || !seenServiceRequestIds.add(requestId)) {
                continue;
            }
            ServiceRequestListModel serviceRequest = serviceRequestById.get(requestId);
            if (serviceRequest == null
                    || !ServiceRequestCategoryConstants.SERVICE_CATEGORY_PROCEDURE.equals(serviceRequest.getCategoryEnum())) {
                continue;
            }
            ActivityDefinition activity = activityById.get(serviceRequest.getActivityId());
            Procedure procedure = procedureById.get(row.getId());
            ProcedureLineSnapshot snap = toProcedureSnapshot(row, activity, procedure);
            if (snap != null) {
                result.add(snap);
            }
        }
        if (!result.isEmpty()) {
            result.get(0).setMain(Boolean.TRUE);
            for (int i = 1; i < result.size(); i++) {
                result.get(i).setMain(Boolean.FALSE);
            }
        }
        return result;
    }

    private Map<Long, Procedure> indexProcedures(List<Procedure> procedures) {
        Map<Long, Procedure> map = new HashMap<>();
        if (procedures == null) {
            return map;
        }
        for (Procedure procedure : procedures) {
            if (procedure != null && procedure.getId() != null) {
                map.put(procedure.getId(), procedure);
            }
        }
        return map;
    }

    private Map<Long, ActivityDefinition> loadActivities(Map<Long, ServiceRequestListModel> serviceRequestById) {
        Set<Long> activityIds = new LinkedHashSet<>();
        for (ServiceRequestListModel row : serviceRequestById.values()) {
            if (row != null && row.getActivityId() != null) {
                activityIds.add(row.getActivityId());
            }
        }
        if (activityIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, ActivityDefinition> map = new HashMap<>();
        for (ActivityDefinition activity : activityDefinitionQueryService.findAllByIds(activityIds)) {
            if (activity != null && activity.getId() != null) {
                map.put(activity.getId(), activity);
            }
        }
        return map;
    }

    private ProcedureLineSnapshot toProcedureSnapshot(
            ProcedureRecordQueryModel row,
            ActivityDefinition activity,
            Procedure procedure) {
        String oprnName = null;
        String oprnCode = null;
        if (activity != null) {
            oprnName = trimToNull(activity.getName());
            oprnCode = firstNonBlank(
                    trimToNull(activity.getYbNo()),
                    procedure != null ? trimToNull(procedure.getProcedureCode()) : null,
                    trimToNull(activity.getBusNo()));
        } else if (procedure != null) {
            oprnCode = trimToNull(procedure.getProcedureCode());
        }
        if (oprnCode == null && oprnName == null) {
            return null;
        }
        ProcedureLineSnapshot snap = new ProcedureLineSnapshot();
        snap.setOprnCode(oprnCode);
        snap.setOprnName(oprnName);
        snap.setOprnDate(resolveOprnDate(row.getOccurrenceTime()));
        snap.setMain(Boolean.FALSE);
        return snap;
    }

    private LocalDate resolveOprnDate(LocalDateTime occurrenceTime) {
        if (occurrenceTime == null) {
            return null;
        }
        return occurrenceTime.toLocalDate();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private List<DiagnosisLineSnapshot> listDiagnosesByScope(Long encounterId, Scope scope) {
        if (encounterId == null) {
            return Collections.emptyList();
        }
        List<EncounterDiagnosisQueryModel> associations = resolveActiveAssociations(encounterId);
        if (associations.isEmpty()) {
            return Collections.emptyList();
        }
        sortAssociations(associations);
        boolean hasTypedDischarge = hasTypedDischarge(associations);
        List<EncounterDiagnosisQueryModel> scoped = new ArrayList<>();
        for (EncounterDiagnosisQueryModel row : associations) {
            if (matchesScope(row.getDiagTypeCode(), scope, hasTypedDischarge)) {
                scoped.add(row);
            }
        }
        if (scoped.isEmpty()) {
            return Collections.emptyList();
        }
        DiagnosisContext ctx = loadDiagnosisContext(scoped);
        List<DiagnosisLineSnapshot> result = new ArrayList<>();
        for (EncounterDiagnosisQueryModel row : scoped) {
            DiagnosisLineSnapshot snap = toSnapshot(row, ctx);
            if (snap != null) {
                result.add(snap);
            }
        }
        applyAdmissionConditions(encounterId, result);
        return result;
    }

    private List<EncounterDiagnosisQueryModel> resolveActiveAssociations(Long encounterId) {
        List<EncounterDiagnosisQueryModel> active = new ArrayList<>(
                encounterQueryService.listEncounterDiagnoses(encounterId));
        if (!active.isEmpty()) {
            return active;
        }
        List<com.openhis.health.domain.clinical.condition.queries.model.EncounterDiagnosisQueryModel> facts =
                conditionQueryService.getEncounterDiagnosis(encounterId);
        if (facts.isEmpty()) {
            return active;
        }
        List<EncounterDiagnosisQueryModel> synthesized = new ArrayList<>(facts.size());
        int rank = 1;
        for (com.openhis.health.domain.clinical.condition.queries.model.EncounterDiagnosisQueryModel fact : facts) {
            if (fact.getConditionId() == null) {
                continue;
            }
            EncounterDiagnosisQueryModel model = new EncounterDiagnosisQueryModel();
            model.setEncounterId(encounterId);
            model.setConditionId(fact.getConditionId());
            model.setDescription(fact.getDescription());
            if (rank == 1) {
                model.setPrimaryFlag(Boolean.TRUE);
            } else {
                model.setPrimaryFlag(Boolean.FALSE);
            }
            model.setRank(rank);
            synthesized.add(model);
            rank++;
        }
        return synthesized;
    }

    private void sortAssociations(List<EncounterDiagnosisQueryModel> associations) {
        associations.sort(Comparator
                .comparing(EncounterDiagnosisQueryModel::getRank, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(EncounterDiagnosisQueryModel::getDiagnosisId, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    private boolean hasTypedDischarge(List<EncounterDiagnosisQueryModel> associations) {
        for (EncounterDiagnosisQueryModel row : associations) {
            IptPatientDiseaseDiagType type = parseDiagType(row.getDiagTypeCode());
            if (type != null && isDischargeDiagType(type)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesScope(String diagTypeCode, Scope scope, boolean hasTypedDischarge) {
        IptPatientDiseaseDiagType type = parseDiagType(diagTypeCode);
        if (scope == Scope.DISCHARGE) {
            if (type == null) {
                if (hasTypedDischarge) {
                    return false;
                }
                return true;
            }
            return isDischargeDiagType(type);
        }
        if (type == null) {
            return false;
        }
        return isAdmissionDiagType(type);
    }

    private IptPatientDiseaseDiagType parseDiagType(String diagTypeCode) {
        String code = trimToNull(diagTypeCode);
        if (code == null) {
            return null;
        }
        return EnumUtil.fromCode(IptPatientDiseaseDiagType.class, code);
    }

    private boolean isDischargeDiagType(IptPatientDiseaseDiagType type) {
        if (type == IptPatientDiseaseDiagType.DISCHARGE) {
            return true;
        }
        if (type == IptPatientDiseaseDiagType.TCM_DISCHARGE_NAME) {
            return true;
        }
        if (type == IptPatientDiseaseDiagType.TCM_DISCHARGE_MAIN_DISEASE) {
            return true;
        }
        return type == IptPatientDiseaseDiagType.TCM_DISCHARGE_MAIN_PATTERN;
    }

    private boolean isAdmissionDiagType(IptPatientDiseaseDiagType type) {
        if (type == IptPatientDiseaseDiagType.ADMISSION) {
            return true;
        }
        return type == IptPatientDiseaseDiagType.ADMISSION_PRELIMINARY;
    }

    private DiagnosisContext loadDiagnosisContext(List<EncounterDiagnosisQueryModel> scoped) {
        List<Long> conditionIds = new ArrayList<>();
        for (EncounterDiagnosisQueryModel row : scoped) {
            if (row.getConditionId() != null) {
                conditionIds.add(row.getConditionId());
            }
        }
        Map<Long, ConditionBatchItemQueryModel> condById = new HashMap<>();
        for (ConditionBatchItemQueryModel item : conditionQueryService.getConditionListById(conditionIds)) {
            condById.put(item.getId(), item);
        }
        Set<Long> defIdSet = new LinkedHashSet<>();
        for (ConditionBatchItemQueryModel item : condById.values()) {
            if (item.getDefinitionId() != null) {
                defIdSet.add(item.getDefinitionId());
            }
        }
        Map<Long, ConditionDefinitionBatchItemQueryModel> defById = new HashMap<>();
        if (!defIdSet.isEmpty()) {
            List<Long> defIds = new ArrayList<>(defIdSet);
            for (ConditionDefinitionBatchItemQueryModel def
                    : conditionDefinitionQueryService.getConditionDefinitionListByIds(defIds)) {
                defById.put(def.getId(), def);
            }
        }
        return new DiagnosisContext(condById, defById);
    }

    private DiagnosisLineSnapshot toSnapshot(EncounterDiagnosisQueryModel row, DiagnosisContext ctx) {
        ConditionBatchItemQueryModel condition = ctx.condById.get(row.getConditionId());
        if (condition == null) {
            return null;
        }
        ConditionDefinitionBatchItemQueryModel definition = null;
        if (condition.getDefinitionId() != null) {
            definition = ctx.defById.get(condition.getDefinitionId());
        }
        String diagCode = null;
        String diagName = null;
        if (definition != null) {
            diagCode = trimToNull(definition.getConditionCode());
            diagName = trimToNull(definition.getName());
        }
        if (diagName == null) {
            diagName = trimToNull(row.getDescription());
        }
        if (diagName == null && condition.getDescription() != null) {
            diagName = trimToNull(condition.getDescription());
        }
        if (diagCode == null && diagName == null) {
            return null;
        }
        DiagnosisLineSnapshot snap = new DiagnosisLineSnapshot();
        snap.setDiagCode(diagCode);
        snap.setDiagName(diagName);
        snap.setMain(Boolean.TRUE.equals(row.getPrimaryFlag()));
        snap.setAdmCondCode(null);
        return snap;
    }

    private void applyAdmissionConditions(Long encounterId, List<DiagnosisLineSnapshot> snapshots) {
        if (encounterId == null || snapshots == null || snapshots.isEmpty()) {
            return;
        }
        Map<String, String> admCondByDiagCode = emrAdmissionConditionResolver.resolveByEncounterId(encounterId);
        if (admCondByDiagCode.isEmpty()) {
            return;
        }
        for (DiagnosisLineSnapshot snap : snapshots) {
            if (trimToNull(snap.getAdmCondCode()) != null) {
                continue;
            }
            String key = normalizeDiagCodeKey(snap.getDiagCode());
            if (key == null) {
                continue;
            }
            String admCond = admCondByDiagCode.get(key);
            if (admCond != null) {
                snap.setAdmCondCode(admCond);
            }
        }
    }

    private String normalizeDiagCodeKey(String diagCode) {
        String code = trimToNull(diagCode);
        if (code == null) {
            return null;
        }
        return code.toUpperCase(Locale.ROOT);
    }

    private enum Scope {
        DISCHARGE,
        ADMISSION
    }

    private static final class DiagnosisContext {
        private final Map<Long, ConditionBatchItemQueryModel> condById;
        private final Map<Long, ConditionDefinitionBatchItemQueryModel> defById;

        private DiagnosisContext(
                Map<Long, ConditionBatchItemQueryModel> condById,
                Map<Long, ConditionDefinitionBatchItemQueryModel> defById) {
            this.condById = condById;
            this.defById = defById;
        }
    }
}
