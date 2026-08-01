/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
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

    /**
     * 写入就诊快照入院/出院科别：入院取就诊机构，出院取末次完成病区所属机构。
     *
     * @param snap      待填充就诊快照
     * @param encounter 就诊实体
     */
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

    /**
     * 将机构 busNo/名称写入快照入院或出院科别字段。
     *
     * @param snap       就诊快照
     * @param organization 组织实体
     * @param admission  true 写 admCaty*；false 写 dscgCaty*
     */
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

    /**
     * 按参与者角色映射首页人员字段（主治/主任/住院医师/责任护士）。
     *
     * @param snap         就诊快照
     * @param participants 有效参与者读模型
     */
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

    /**
     * 里程碑名称是否为转科或入科（参与转科摘要）。
     *
     * @param name 里程碑 name
     * @return 转科/入科为 true
     */
    private boolean isTransferMilestone(String name) {
        if (name == null) {
            return false;
        }
        return CustomConstants.EncounterHistoryMilestoneName.TRANSFER.equals(name)
                || CustomConstants.EncounterHistoryMilestoneName.ADMISSION_TO_DEPT.equals(name);
    }

    /**
     * 从预加载 org 名称 Map 解析科室展示名。
     *
     * @param orgNames       orgId → 名称
     * @param organizationId 组织 id
     * @return trim 后名称；缺失时 null
     */
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

    /**
     * 按出院/入院范围加载诊断快照行（含 condition 回落合成与 EMR 入院病情）。
     *
     * @param encounterId 就诊主键
     * @param scope       诊断范围（出院/入院）
     * @return 诊断行快照
     */
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

    /**
     * 加载就诊有效诊断关联。
     *
     * @param encounterId 就诊主键
     * @return 诊断关联列表
     */
    private List<EncounterDiagnosisQueryModel> resolveActiveAssociations(Long encounterId) {
        return new ArrayList<>(encounterQueryService.listEncounterDiagnoses(encounterId));
    }

    /**
     * 诊断关联按 rank、diagnosisId 稳定排序。
     *
     * @param associations 待排序关联（原地排序）
     */
    private void sortAssociations(List<EncounterDiagnosisQueryModel> associations) {
        associations.sort(Comparator
                .comparing(EncounterDiagnosisQueryModel::getRank, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(EncounterDiagnosisQueryModel::getDiagnosisId, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    /**
     * 关联列表中是否存在显式出院类型诊断（影响无类型码行的默认归属）。
     *
     * @param associations 诊断关联
     * @return 含出院类型为 true
     */
    private boolean hasTypedDischarge(List<EncounterDiagnosisQueryModel> associations) {
        for (EncounterDiagnosisQueryModel row : associations) {
            IptPatientDiseaseDiagType type = parseDiagType(row.getDiagTypeCode());
            if (type != null && isDischargeDiagType(type)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 诊断类型码是否匹配出院/入院范围（无类型码时出院范围默认归入）。
     *
     * @param diagTypeCode        诊断类型 code
     * @param scope               目标范围
     * @param hasTypedDischarge   是否存在显式出院诊断
     * @return 匹配为 true
     */
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

    /**
     * 解析住院诊断类型枚举 code。
     *
     * @param diagTypeCode 类型 code 字符串
     * @return 枚举；空或非法时 null
     */
    private IptPatientDiseaseDiagType parseDiagType(String diagTypeCode) {
        String code = trimToNull(diagTypeCode);
        if (code == null) {
            return null;
        }
        return EnumUtil.fromCode(IptPatientDiseaseDiagType.class, code);
    }

    /**
     * 是否为出院侧诊断类型（含中医出院类）。
     *
     * @param type 诊断类型枚举
     * @return 出院类型为 true
     */
    private boolean isDischargeDiagType(IptPatientDiseaseDiagType type) {
        return type == IptPatientDiseaseDiagType.DISCHARGE
                || type == IptPatientDiseaseDiagType.TCM_DISCHARGE_NAME
                || type == IptPatientDiseaseDiagType.TCM_DISCHARGE_MAIN_DISEASE
                || type == IptPatientDiseaseDiagType.TCM_DISCHARGE_MAIN_PATTERN;
    }

    /**
     * 是否为入院侧诊断类型（入院/初步）。
     *
     * @param type 诊断类型枚举
     * @return 入院类型为 true
     */
    private boolean isAdmissionDiagType(IptPatientDiseaseDiagType type) {
        return type == IptPatientDiseaseDiagType.ADMISSION
                || type == IptPatientDiseaseDiagType.ADMISSION_PRELIMINARY;
    }

    /**
     * 批量加载 conditionDefinition 上下文（诊断编码/名称装配用，不经 Condition）。
     *
     * @param scoped 范围内诊断关联
     * @return definition 索引上下文
     */
    private DiagnosisContext loadDiagnosisContext(List<EncounterDiagnosisQueryModel> scoped) {
        Set<Long> defIdSet = new LinkedHashSet<>();
        for (EncounterDiagnosisQueryModel row : scoped) {
            if (row.getConditionDefinitionId() != null) {
                defIdSet.add(row.getConditionDefinitionId());
            }
        }
        Map<Long, ConditionDefinitionBatchItemQueryModel> defById = new HashMap<>();
        if (!defIdSet.isEmpty()) {
            for (ConditionDefinitionBatchItemQueryModel def
                    : conditionDefinitionQueryService.getConditionDefinitionListByIds(new ArrayList<>(defIdSet))) {
                defById.put(def.getId(), def);
            }
        }
        return new DiagnosisContext(defById);
    }

    /**
     * 单条诊断关联 → 首页诊断行快照（编码/名称/主诊断标记）。
     *
     * @param row 诊断关联
     * @param ctx condition/definition 上下文
     * @return 诊断行；编码名称均缺失时 null
     */
    private DiagnosisLineSnapshot toDiagnosisSnapshot(EncounterDiagnosisQueryModel row, DiagnosisContext ctx) {
        ConditionDefinitionBatchItemQueryModel definition = null;
        if (row.getConditionDefinitionId() != null) {
            definition = ctx.defById.get(row.getConditionDefinitionId());
        }
        // 目录定义缺失时无法组装编码/名称
        if (definition == null) {
            String fallbackName = trimToNull(row.getDescription());
            if (fallbackName == null) {
                return null;
            }
            DiagnosisLineSnapshot snap = new DiagnosisLineSnapshot();
            snap.setDiagName(fallbackName);
            snap.setMain(Boolean.TRUE.equals(row.getPrimaryFlag()));
            return snap;
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

    /**
     * 从 EMR 扩展解析入院病情 adm_cond 并按诊断编码回填（不覆盖已有值）。
     *
     * @param encounterId 就诊主键
     * @param snapshots   待回填诊断行
     */
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

    /**
     * Procedure 实体列表 → id 索引 Map。
     *
     * @param procedures 手术实体列表
     * @return procedureId → 实体
     */
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

    /**
     * 由 serviceRequest 引用的 activityId 批量加载 ActivityDefinition。
     *
     * @param serviceRequestById 诊疗请求索引
     * @return activityId → 活动定义
     */
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

    /**
     * 已完成手术记录 → 首页手术行快照（编码优先 ybNo/ procedureCode/ busNo）。
     *
     * @param row       手术记录读模型
     * @param activity  关联活动定义
     * @param procedure 手术实体
     * @return 手术行；编码名称均空时 null
     */
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

    /**
     * 诊断编码归一化为大写 Map 键（EMR 入院病情匹配用）。
     *
     * @param diagCode 原始诊断编码
     * @return 大写 trim 键；空时 null
     */
    private String normalizeDiagCodeKey(String diagCode) {
        String code = trimToNull(diagCode);
        if (code == null) {
            return null;
        }
        return code.toUpperCase(Locale.ROOT);
    }

    /** 诊断回填范围：出院诊断 / 入院诊断。 */
    private enum DiagnosisScope {
        /** 出院诊断。 */
        DISCHARGE,
        /** 入院诊断。 */
        ADMISSION
    }

    /** 诊断批量读模型上下文（definition 索引，不经 Condition）。 */
    private static final class DiagnosisContext {
        /** definitionId → 批量 definition 行。 */
        private final Map<Long, ConditionDefinitionBatchItemQueryModel> defById;

        /**
         * @param defById definition 索引
         */
        private DiagnosisContext(Map<Long, ConditionDefinitionBatchItemQueryModel> defById) {
            this.defById = defById;
        }
    }
}
