/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.domain.mrms.caserecord.support;

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.EncounterParticipantQueryModel;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.entity.Organization;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.clinical.condition.queries.model.ConditionBatchItemQueryModel;
import com.openhis.health.domain.clinical.condition.service.ConditionQueryService;
import com.openhis.health.domain.clinical.conditiondefinition.queries.model.ConditionDefinitionBatchItemQueryModel;
import com.openhis.health.domain.clinical.conditiondefinition.service.ConditionDefinitionQueryService;
import com.openhis.health.domain.clinical.procedure.entity.Procedure;
import com.openhis.health.domain.clinical.procedure.queries.model.ProcedureRecordQueryModel;
import com.openhis.health.domain.clinical.procedure.service.ProcedureQueryService;
import com.openhis.health.domain.custom.encounterhistory.queries.model.EncounterHistoryMilestoneQueryModel;
import com.openhis.health.domain.custom.encounterhistory.service.EncounterHistoryQueryService;
import com.openhis.health.domain.mrms.caserecord.resolver.AdmissionConditionResolver;
import com.openhis.health.domain.mrms.caserecord.vo.DiagnosisLineSnapshot;
import com.openhis.health.domain.mrms.caserecord.vo.EncounterSnapshot;
import com.openhis.health.domain.mrms.caserecord.vo.PatientSnapshot;
import com.openhis.health.domain.mrms.caserecord.vo.ProcedureLineSnapshot;
import com.openhis.health.domain.mrms.caserecord.vo.TransferLineSnapshot;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.share.constants.ServiceRequestCategoryConstants;
import com.openhis.health.domain.share.constants.TableNameConstants;
import com.openhis.health.domain.share.enums.EncounterParticipantRole;
import com.openhis.health.domain.share.enums.IptPatientDiseaseDiagType;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.workflow.activitydefinition.entity.ActivityDefinition;
import com.openhis.health.domain.workflow.activitydefinition.service.ActivityDefinitionQueryService;
import com.openhis.health.domain.workflow.servicerequest.queries.model.ServiceRequestListModel;
import com.openhis.health.domain.workflow.servicerequest.service.ServiceRequestQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static com.openhis.whale.core.application.util.StringUtils.firstNonBlank;
import static com.openhis.whale.core.application.util.StringUtils.isNotEmpty;
import static com.openhis.whale.core.application.util.StringUtils.trimToNull;

/**
 * 病案首页自动取值只读查询服务（DM-MRMS-001 §4）。
 *
 * <p>跨聚合读：注入各 bounded context 的 {@code *QueryService} 组装快照，不写库（B.6.4）。</p>
 * <p>写侧合并由 {@link CaseRecordHomepageDomainService#mergeAutoFillFromHis} 负责。</p>
 */
@Service
@RequiredArgsConstructor
public class CaseRecordAutoFillSupport {

    private final PatientQueryService patientQueryService;
    private final EncounterQueryService encounterQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final LocationQueryService locationQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final EncounterHistoryQueryService encounterHistoryQueryService;
    private final ConditionQueryService conditionQueryService;
    private final ConditionDefinitionQueryService conditionDefinitionQueryService;
    private final ProcedureQueryService procedureQueryService;
    private final ServiceRequestQueryService serviceRequestQueryService;
    private final ActivityDefinitionQueryService activityDefinitionQueryService;
    private final Optional<AdmissionConditionResolver> admissionConditionResolver;

    /**
     * 加载患者基础信息快照（adm_patient）。
     *
     * @param patientId 患者主键
     * @return 快照；不存在或 id 为空时返回 null
     */
    public PatientSnapshot loadPatient(Long patientId) {
        if (patientId == null) {
            return null;
        }
        // 跨聚合读：adm_patient
        Patient patient = patientQueryService.findOptionalPatientEntityById(patientId).orElse(null);
        if (patient == null) {
            return null;
        }
        PatientSnapshot snap = new PatientSnapshot();
        snap.setPatientId(patient.getId());
        snap.setName(patient.getName());
        snap.setGenderCode(patient.getGenderEnum());
        snap.setBirthDate(patient.getBirthDate());
        snap.setCertno(patient.getIdCard());
        snap.setPhone(patient.getPhone());
        return snap;
    }

    /**
     * 加载就诊快照：出入院时间、科室、参与者等（adm_encounter + 参与者）。
     *
     * @param encounterId 就诊主键
     * @return 快照；不存在时返回 null
     */
    public EncounterSnapshot loadEncounter(Long encounterId) {
        if (encounterId == null) {
            return null;
        }
        // 跨聚合读：adm_encounter + 参与者
        Encounter encounter = encounterQueryService.findOptionalEncounterEntityById(encounterId).orElse(null);
        if (encounter == null) {
            return null;
        }
        EncounterSnapshot snap = new EncounterSnapshot();
        snap.setEncounterId(encounter.getId());
        snap.setPatientId(encounter.getPatientId());
        snap.setIptNo(encounter.getBusNo());
        snap.setAdmTime(encounter.getStartTime());
        snap.setDscgTime(encounter.getEndTime());
        applyDepartments(snap, encounter);
        applyParticipants(snap, encounterQueryService.listActiveEncounterParticipants(encounterId));
        return snap;
    }

    private void applyDepartments(EncounterSnapshot snap, Encounter encounter) {
        // 入院科别：就诊所属机构
        if (encounter.getOrganizationId() != null) {
            applyOrganization(snap, organizationQueryService.loadOrganizationByIdForDisplay(encounter.getOrganizationId()), true);
        }
        // 出院科别：末次完成病区对应机构
        encounterQueryService.findLastCompletedWardLocationIdByEncounterId(encounter.getId())
                .flatMap(locationQueryService::findOptionalById)
                .ifPresent(ward -> applyOrganization(
                        snap,
                        organizationQueryService.loadOrganizationByIdForDisplay(ward.getOrganizationId()),
                        false));
    }

    private void applyOrganization(EncounterSnapshot snap, Organization organization, boolean admission) {
        if (organization == null) {
            return;
        }
        String code = trimToNull(organization.getBusNo());
        String name = trimToNull(organization.getName());
        // 入院科别写入 adm_* 字段
        if (admission) {
            if (code != null) {
                snap.setAdmCatyCode(code);
            }
            if (name != null) {
                snap.setAdmCatyName(name);
            }
            return;
        }
        // 出院科别写入 dscg_* 字段
        if (code != null) {
            snap.setDscgCatyCode(code);
        }
        if (name != null) {
            snap.setDscgCatyName(name);
        }
    }

    private void applyParticipants(EncounterSnapshot snap, List<EncounterParticipantQueryModel> participants) {
        if (participants == null || participants.isEmpty()) {
            return;
        }
        Set<Long> practitionerIds = new HashSet<>();
        for (EncounterParticipantQueryModel participant : participants) {
            if (participant.getPractitionerId() != null) {
                practitionerIds.add(participant.getPractitionerId());
            }
        }
        Map<Long, String> nameById = new HashMap<>();
        if (!practitionerIds.isEmpty()) {
            for (Practitioner practitioner : practitionerQueryService.findAllByIds(practitionerIds)) {
                if (practitioner.getId() != null && isNotEmpty(practitioner.getName())) {
                    nameById.put(practitioner.getId(), practitioner.getName());
                }
            }
        }
        for (EncounterParticipantQueryModel participant : participants) {
            if (participant.getPractitionerId() == null) {
                continue;
            }
            String roleCode = participant.getRoleCode();
            Long practitionerId = participant.getPractitionerId();
            String name = nameById.get(practitionerId);
            // 按参与者角色映射首页人员字段
            if (EncounterParticipantRole.ATTENDING_DOCTOR.getCode().equals(roleCode)) {
                snap.setAtddrPractitionerId(practitionerId);
                snap.setAtddrName(name);
            } else if (EncounterParticipantRole.CHIEF_DOCTOR.getCode().equals(roleCode)) {
                snap.setChfdrName(name);
            } else if (EncounterParticipantRole.ADMITTER.getCode().equals(roleCode)) {
                snap.setIptDrName(name);
            } else if (EncounterParticipantRole.PRIMARY_NURSE.getCode().equals(roleCode)) {
                snap.setRespNursName(name);
            }
        }
    }

    /**
     * 解析离院方式字典码（custom encounter history）。
     *
     * @param encounterId 就诊主键
     * @return 离院方式 code，无法解析时 null
     */
    public String resolveDischargeWayCode(Long encounterId) {
        if (encounterId == null) {
            return null;
        }
        var way = encounterHistoryQueryService.resolveDischargeWay(encounterId);
        if (way == null) {
            return null;
        }
        return way.getOutWayCode();
    }

    /**
     * 列出就诊转科/入科里程碑，用于首页转科摘要。
     *
     * @param encounterId 就诊主键
     * @return 转科行列表，无数据时 empty
     */
    public List<TransferLineSnapshot> listTransfers(Long encounterId) {
        if (encounterId == null) {
            return List.of();
        }
        List<EncounterHistoryMilestoneQueryModel> milestones =
                encounterHistoryQueryService.listMilestonesByEncounter(encounterId);
        if (milestones.isEmpty()) {
            return List.of();
        }
        List<EncounterHistoryMilestoneQueryModel> transferMilestones = new ArrayList<>();
        Set<Long> orgIds = new LinkedHashSet<>();
        for (EncounterHistoryMilestoneQueryModel milestone : milestones) {
            // 仅转科/入科里程碑参与转科摘要
            if (!isTransferMilestone(milestone.getName())) {
                continue;
            }
            transferMilestones.add(milestone);
            if (milestone.getOriginalOrganizationId() != null) {
                orgIds.add(milestone.getOriginalOrganizationId());
            }
            if (milestone.getTargetOrganizationId() != null) {
                orgIds.add(milestone.getTargetOrganizationId());
            }
        }
        if (transferMilestones.isEmpty()) {
            return List.of();
        }
        Map<Long, String> orgNames = organizationQueryService.findOrganizationNamesByIds(orgIds);
        List<TransferLineSnapshot> lines = new ArrayList<>();
        for (EncounterHistoryMilestoneQueryModel milestone : transferMilestones) {
            TransferLineSnapshot line = new TransferLineSnapshot();
            line.setFromDeptName(resolveOrgName(orgNames, milestone.getOriginalOrganizationId()));
            line.setToDeptName(resolveOrgName(orgNames, milestone.getTargetOrganizationId()));
            lines.add(line);
        }
        return lines;
    }

    private boolean isTransferMilestone(String name) {
        if (name == null) {
            return false;
        }
        return CustomConstants.EncounterHistoryMilestoneName.TRANSFER.equals(name)
                || CustomConstants.EncounterHistoryMilestoneName.ADMISSION_TO_DEPT.equals(name);
    }

    private String resolveOrgName(Map<Long, String> orgNames, Long organizationId) {
        if (organizationId == null || orgNames == null) {
            return null;
        }
        String name = orgNames.get(organizationId);
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim();
    }

    /**
     * 列出出院诊断快照行（含主/其他，由 merge 侧按类型拆分）。
     *
     * @param encounterId 就诊主键
     * @return 诊断快照列表
     */
    public List<DiagnosisLineSnapshot> listDischargeDiagnoses(Long encounterId) {
        return listDiagnosesByScope(encounterId, DiagnosisScope.DISCHARGE);
    }

    /**
     * 列出入院诊断快照行。
     *
     * @param encounterId 就诊主键
     * @return 诊断快照列表
     */
    public List<DiagnosisLineSnapshot> listAdmissionDiagnoses(Long encounterId) {
        return listDiagnosesByScope(encounterId, DiagnosisScope.ADMISSION);
    }

    /**
     * 列出就诊已完成手术/操作快照（cln_procedure）。
     *
     * @param encounterId 就诊主键
     * @return 手术快照列表
     */
    public List<ProcedureLineSnapshot> listProcedures(Long encounterId) {
        if (encounterId == null) {
            return Collections.emptyList();
        }
        List<ProcedureRecordQueryModel> completedRows =
                procedureQueryService.listNetRemainingCompletedProcedureRecords(encounterId, null, null);
        // 无已完成手术记录时返回空列表
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
            // 仅 service_request 来源且按 requestId 去重
            if (row == null || !TableNameConstants.WKF_SERVICE_REQUEST.equals(row.getRequestTable())) {
                continue;
            }
            Long requestId = row.getRequestId();
            if (requestId == null || !seenServiceRequestIds.add(requestId)) {
                continue;
            }
            ServiceRequestListModel serviceRequest = serviceRequestById.get(requestId);
            // 非手术类医嘱跳过
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
        // 按时间排序后首条标记为主手术
        if (!result.isEmpty()) {
            result.get(0).setMain(Boolean.TRUE);
            for (int i = 1; i < result.size(); i++) {
                result.get(i).setMain(Boolean.FALSE);
            }
        }
        return result;
    }

    private List<DiagnosisLineSnapshot> listDiagnosesByScope(Long encounterId, DiagnosisScope scope) {
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
            // 按出院/入院范围过滤诊断关联
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
            DiagnosisLineSnapshot snap = toDiagnosisSnapshot(row, ctx);
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
        // 就诊诊断关联为空时回落 condition 事实表合成（B.6.4：经 Condition QS）
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
            model.setPrimaryFlag(rank == 1);
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

    private boolean matchesScope(String diagTypeCode, DiagnosisScope scope, boolean hasTypedDischarge) {
        IptPatientDiseaseDiagType type = parseDiagType(diagTypeCode);
        if (scope == DiagnosisScope.DISCHARGE) {
            // 无类型码且无显式出院诊断时，未分型关联默认归入出院
            if (type == null) {
                return !hasTypedDischarge;
            }
            return isDischargeDiagType(type);
        }
        // 入院范围：无类型码不匹配
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
        return type == IptPatientDiseaseDiagType.DISCHARGE
                || type == IptPatientDiseaseDiagType.TCM_DISCHARGE_NAME
                || type == IptPatientDiseaseDiagType.TCM_DISCHARGE_MAIN_DISEASE
                || type == IptPatientDiseaseDiagType.TCM_DISCHARGE_MAIN_PATTERN;
    }

    private boolean isAdmissionDiagType(IptPatientDiseaseDiagType type) {
        return type == IptPatientDiseaseDiagType.ADMISSION
                || type == IptPatientDiseaseDiagType.ADMISSION_PRELIMINARY;
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
            for (ConditionDefinitionBatchItemQueryModel def
                    : conditionDefinitionQueryService.getConditionDefinitionListByIds(new ArrayList<>(defIdSet))) {
                defById.put(def.getId(), def);
            }
        }
        return new DiagnosisContext(condById, defById);
    }

    private DiagnosisLineSnapshot toDiagnosisSnapshot(EncounterDiagnosisQueryModel row, DiagnosisContext ctx) {
        ConditionBatchItemQueryModel condition = ctx.condById.get(row.getConditionId());
        // 条件实例缺失时无法组装诊断行
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
        // 编码与名称均为空时丢弃该行
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
        Map<String, String> admCondByDiagCode = admissionConditionResolver
                .map(r -> r.resolveByEncounterId(encounterId)).orElse(Map.of());
        // EMR 未提供入院病情扩展时跳过
        if (admCondByDiagCode.isEmpty()) {
            return;
        }
        for (DiagnosisLineSnapshot snap : snapshots) {
            // 已有 adm_cond 的行不覆盖
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
            String procedureCode = null;
            if (procedure != null) {
                procedureCode = trimToNull(procedure.getProcedureCode());
            }
            oprnCode = firstNonBlank(
                    trimToNull(activity.getYbNo()),
                    procedureCode,
                    trimToNull(activity.getBusNo()));
        } else if (procedure != null) {
            oprnCode = trimToNull(procedure.getProcedureCode());
        }
        // 编码与名称均为空时丢弃该行
        if (oprnCode == null && oprnName == null) {
            return null;
        }
        ProcedureLineSnapshot snap = new ProcedureLineSnapshot();
        snap.setOprnCode(oprnCode);
        snap.setOprnName(oprnName);
        if (row.getOccurrenceTime() != null) {
            snap.setOprnDate(row.getOccurrenceTime().toLocalDate());
        }
        snap.setMain(Boolean.FALSE);
        return snap;
    }

    private String normalizeDiagCodeKey(String diagCode) {
        String code = trimToNull(diagCode);
        if (code == null) {
            return null;
        }
        return code.toUpperCase(Locale.ROOT);
    }

    private enum DiagnosisScope {
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
