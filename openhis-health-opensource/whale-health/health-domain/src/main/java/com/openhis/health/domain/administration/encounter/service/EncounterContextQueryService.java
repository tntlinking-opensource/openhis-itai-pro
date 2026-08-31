/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.service;

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.entity.EncounterLocation;
import com.openhis.health.domain.administration.encounter.entity.EncounterParticipant;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.custom.encounterhistory.entity.EncounterHistory;
import com.openhis.health.domain.custom.encounterhistory.service.EncounterHistoryQueryService;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.GeneralSharedConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterParticipantRole;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.utils.AgeCalculatorUtil;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.InpatientEncounterStatusDisplayUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.application.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** 就诊上下文 L1 batch hydrate（门诊/住院；bridge 与 application Loader 共用）。
 *
 * <p>参与者展示：{@code listDisplayParticipantEntitiesByEncounterIds}（活动优先，无则末次 COMPLETED）。
 * 位置展示：活动轨迹 + 无活动床时末次 COMPLETED 回落为「原{房}-{床}（未占床）」。</p>
 */
@Service
@RequiredArgsConstructor
public class EncounterContextQueryService {

    private final EncounterQueryService encounterQueryService;
    private final PatientQueryService patientQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final HealthcareServiceQueryService healthcareServiceQueryService;
    private final LocationQueryService locationQueryService;
    private final EncounterHistoryQueryService encounterHistoryQueryService;

    public OutpatientEncounterContextQueryModel mustGetOutpatientContextByEncounterId(Long encounterId) {
        return getOutpatientContextByEncounterId(encounterId)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.ENCOUNTER_NOT_FOUND,
                "门诊就诊上下文不存在"));
    }

    public InpatientEncounterContextQueryModel mustGetInpatientContextByEncounterId(Long encounterId) {
        return getInpatientContextByEncounterId(encounterId)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.ENCOUNTER_NOT_FOUND,
                "住院就诊上下文不存在"));
    }

    public Optional<OutpatientEncounterContextQueryModel> getOutpatientContextByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return Optional.empty();
        }
        return encounterQueryService.findOptionalEncounterEntityById(encounterId)
            .filter(this::isOutpatientEncounter)
            .map(encounter -> batchLoadOutpatientContexts(List.of(encounter)).get(0));
    }

    public Optional<InpatientEncounterContextQueryModel> getInpatientContextByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return Optional.empty();
        }
        return encounterQueryService.findInpatientEncounterEntityById(encounterId)
            .map(encounter -> batchLoadInpatientContexts(List.of(encounter)).get(0));
    }

    /**
     * L1 batch hydrate：门诊就诊上下文（不含 diagnosisJson，由 application L2 叠加）。
     *
     * @param encounters 就诊实体列表
     * @return 上下文列表
     */
    public List<OutpatientEncounterContextQueryModel> batchLoadOutpatientContexts(List<Encounter> encounters) {
        return toOutpatientContextModels(encounters);
    }

    /**
     * L1 batch hydrate：住院就诊上下文（不含 diagnosisJson，由 application L2 叠加）。
     *
     * @param encounters 就诊实体列表
     * @return 上下文列表
     */
    public List<InpatientEncounterContextQueryModel> batchLoadInpatientContexts(List<Encounter> encounters) {
        return toInpatientContextModels(encounters);
    }

    /**
     * 按调用方给定顺序批量加载门诊上下文（过滤非门诊行，缺失 id 跳过）。
     *
     * @param encounterIdsOrdered 就诊主键有序列表
     * @return 与输入顺序一致的上下文列表
     */
    public List<OutpatientEncounterContextQueryModel> listOutpatientContextsByEncounterIdsOrdered(
        List<Long> encounterIdsOrdered) {
        if (encounterIdsOrdered == null || encounterIdsOrdered.isEmpty()) {
            return new ArrayList<>();
        }
        List<Encounter> loaded = encounterQueryService.listEncounterEntitiesByIds(encounterIdsOrdered);
        List<Encounter> outpatientRows = new ArrayList<>();
        for (Encounter row : loaded) {
            if (isOutpatientEncounter(row)) {
                outpatientRows.add(row);
            }
        }
        Map<Long, OutpatientEncounterContextQueryModel> byEncounterId = new HashMap<>(outpatientRows.size() * 2);
        for (OutpatientEncounterContextQueryModel model : batchLoadOutpatientContexts(outpatientRows)) {
            if (model.getEncounterId() != null) {
                byEncounterId.put(model.getEncounterId(), model);
            }
        }
        List<OutpatientEncounterContextQueryModel> ordered = new ArrayList<>(encounterIdsOrdered.size());
        for (Long encounterId : encounterIdsOrdered) {
            OutpatientEncounterContextQueryModel hit = byEncounterId.get(encounterId);
            if (hit != null) {
                ordered.add(hit);
            }
        }
        return ordered;
    }

    /**
     * 按调用方给定顺序批量加载住院上下文（缺失 id 跳过，不抛错）。
     *
     * @param encounterIdsOrdered 就诊主键有序列表
     * @return 与输入顺序一致的上下文列表
     */
    public List<InpatientEncounterContextQueryModel> listInpatientContextsByEncounterIdsOrdered(
        List<Long> encounterIdsOrdered) {
        if (encounterIdsOrdered == null || encounterIdsOrdered.isEmpty()) {
            return new ArrayList<>();
        }
        List<Encounter> loaded =
            encounterQueryService.listInpatientEncounterEntitiesByIds(encounterIdsOrdered);
        Map<Long, InpatientEncounterContextQueryModel> byEncounterId = new HashMap<>(loaded.size() * 2);
        for (InpatientEncounterContextQueryModel model : batchLoadInpatientContexts(loaded)) {
            if (model.getEncounterId() != null) {
                byEncounterId.put(model.getEncounterId(), model);
            }
        }
        List<InpatientEncounterContextQueryModel> ordered = new ArrayList<>(encounterIdsOrdered.size());
        for (Long encounterId : encounterIdsOrdered) {
            InpatientEncounterContextQueryModel hit = byEncounterId.get(encounterId);
            if (hit != null) {
                ordered.add(hit);
            }
        }
        return ordered;
    }

    private boolean isOutpatientEncounter(Encounter encounter) {
        if (encounter == null || Boolean.TRUE.equals(encounter.getIsDeleted())) {
            return false;
        }
        return EncounterClass.AMB.getCode().equals(encounter.getClassEnum());
    }

    private List<OutpatientEncounterContextQueryModel> toOutpatientContextModels(List<Encounter> encounters) {
        if (encounters == null || encounters.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> patientIds = new LinkedHashSet<>();
        LinkedHashSet<Long> organizationIds = new LinkedHashSet<>();
        LinkedHashSet<Long> serviceTypeIds = new LinkedHashSet<>();
        for (Encounter encounter : encounters) {
            if (encounter.getId() != null) {
                encounterIds.add(encounter.getId());
            }
            if (encounter.getPatientId() != null) {
                patientIds.add(encounter.getPatientId());
            }
            if (encounter.getOrganizationId() != null) {
                organizationIds.add(encounter.getOrganizationId());
            }
            if (encounter.getServiceTypeId() != null) {
                serviceTypeIds.add(encounter.getServiceTypeId());
            }
        }
        Map<Long, Patient> patientById = patientQueryService.loadPatientEntityMapByIds(patientIds);
        Map<Long, String> organizationNameById = organizationQueryService.mapNamesByIds(organizationIds);
        Map<Long, HealthcareService> healthcareServiceById =
            healthcareServiceQueryService.loadHealthcareServiceMapByIds(serviceTypeIds);

        Map<Long, List<EncounterParticipant>> participantsByEncounterId = new HashMap<>();
        if (!encounterIds.isEmpty()) {
            for (EncounterParticipant participant :
                encounterQueryService.listDisplayParticipantEntitiesByEncounterIds(encounterIds)) {
                if (participant.getEncounterId() == null) {
                    continue;
                }
                participantsByEncounterId
                    .computeIfAbsent(participant.getEncounterId(), ignored -> new ArrayList<>())
                    .add(participant);
            }
        }
        Set<Long> practitionerIds = new LinkedHashSet<>();
        for (List<EncounterParticipant> rows : participantsByEncounterId.values()) {
            for (EncounterParticipant row : rows) {
                if (row.getPractitionerId() != null) {
                    practitionerIds.add(row.getPractitionerId());
                }
            }
        }
        Map<Long, String> practitionerNameById = new HashMap<>();
        if (!practitionerIds.isEmpty()) {
            for (Practitioner practitioner : practitionerQueryService.findAllByIds(practitionerIds)) {
                if (practitioner.getId() != null && StringUtils.isNotEmpty(practitioner.getName())) {
                    practitionerNameById.put(practitioner.getId(), practitioner.getName());
                }
            }
        }

        List<OutpatientEncounterContextQueryModel> models = new ArrayList<>(encounters.size());
        for (Encounter encounter : encounters) {
            OutpatientEncounterContextQueryModel model = new OutpatientEncounterContextQueryModel();
            model.setEncounterId(encounter.getId());
            model.setEncounterBusNo(encounter.getBusNo());
            model.setClassEnum(encounter.getClassEnum());
            model.setStatusEnum(encounter.getStatusEnum());
            model.setPatientId(encounter.getPatientId());
            model.setOrganizationId(encounter.getOrganizationId());
            model.setServiceTypeId(encounter.getServiceTypeId());
            if (encounter.getServiceTypeId() != null) {
                HealthcareService healthcareService = healthcareServiceById.get(encounter.getServiceTypeId());
                if (healthcareService != null) {
                    model.setServiceTypeName(healthcareService.getName());
                }
            }
            model.setStartTime(encounter.getStartTime());
            model.setReceptionTime(encounter.getReceptionTime());
            model.setEndTime(encounter.getEndTime());
            model.setDisplayOrder(encounter.getDisplayOrder());
            if (encounter.getOrganizationId() != null) {
                model.setOrganizationName(organizationNameById.get(encounter.getOrganizationId()));
            }
            EncounterStatus status = EnumUtil.fromCode(EncounterStatus.class, encounter.getStatusEnum());
            if (status != null) {
                model.setStatusEnumText(status.getDisplay());
            }
            Patient patient = patientById.get(encounter.getPatientId());
            if (patient != null) {
                model.setPatientName(patient.getName());
                model.setPyStr(patient.getPyStr());
                model.setWbStr(patient.getWbStr());
                model.setGenderEnum(patient.getGenderEnum());
                model.setIdCard(patient.getIdCard());
                model.setPhone(patient.getPhone());
                // 地址全文：仅透传主档 text；空则保持 null，供医生站卡片回显
                if (patient.getAddress() != null && StringUtils.isNotEmpty(patient.getAddress().getText())) {
                    model.setAddress(patient.getAddress().getText());
                }
                if (patient.getBirthDate() != null) {
                    model.setBirthDate(patient.getBirthDate().atStartOfDay());
                    Date birthDate = Date.from(
                        patient.getBirthDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
                    String ageText = AgeCalculatorUtil.getAge(birthDate);
                    if (StringUtils.isNotEmpty(ageText)) {
                        model.setAgeText(ageText);
                    }
                }
            }
            applyOutpatientDoctor(
                model,
                participantsByEncounterId.get(encounter.getId()),
                practitionerNameById);
            models.add(model);
        }
        return models;
    }

    private void applyOutpatientDoctor(
            OutpatientEncounterContextQueryModel model,
            List<EncounterParticipant> participants,
            Map<Long, String> practitionerNameById) {
        if (participants == null || participants.isEmpty()) {
            return;
        }
        EncounterParticipant picked = null;
        for (EncounterParticipant participant : participants) {
            if (participant == null || participant.getPractitionerId() == null) {
                continue;
            }
            String roleCode = participant.getRoleCode();
            if (EncounterParticipantRole.ADMITTER.getCode().equals(roleCode)) {
                picked = participant;
                break;
            }
            if (picked == null && EncounterParticipantRole.ATTENDING_DOCTOR.getCode().equals(roleCode)) {
                picked = participant;
            }
            if (picked == null && EncounterParticipantRole.REGISTRATION_DOCTOR.getCode().equals(roleCode)) {
                picked = participant;
            }
        }
        if (picked == null) {
            picked = participants.get(0);
        }
        model.setDoctorId(picked.getPractitionerId());
        model.setDoctorName(practitionerNameById.get(picked.getPractitionerId()));
    }

    private List<InpatientEncounterContextQueryModel> toInpatientContextModels(List<Encounter> encounters) {
        if (encounters == null || encounters.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        LinkedHashSet<Long> patientIds = new LinkedHashSet<>();
        LinkedHashSet<Long> organizationIds = new LinkedHashSet<>();
        for (Encounter encounter : encounters) {
            if (encounter.getId() != null) {
                encounterIds.add(encounter.getId());
            }
            if (encounter.getPatientId() != null) {
                patientIds.add(encounter.getPatientId());
            }
            if (encounter.getOrganizationId() != null) {
                organizationIds.add(encounter.getOrganizationId());
            }
        }
        Map<Long, Patient> patientById = patientQueryService.loadPatientEntityMapByIds(patientIds);
        Map<Long, String> organizationNameById = organizationQueryService.mapNamesByIds(organizationIds);

        Map<Long, List<EncounterLocation>> locationsByEncounterId = new HashMap<>();
        if (!encounterIds.isEmpty()) {
            for (EncounterLocation row :
                encounterQueryService.listActiveEncounterLocationEntitiesByEncounterIds(encounterIds)) {
                if (row.getEncounterId() == null) {
                    continue;
                }
                locationsByEncounterId
                    .computeIfAbsent(row.getEncounterId(), ignored -> new ArrayList<>())
                    .add(row);
            }
        }
        Set<Long> locationIds = new LinkedHashSet<>();
        for (List<EncounterLocation> rows : locationsByEncounterId.values()) {
            for (EncounterLocation row : rows) {
                if (row.getLocationId() != null) {
                    locationIds.add(row.getLocationId());
                }
            }
        }
        Map<Long, Location> locationById = new HashMap<>(locationQueryService.loadLocationEntityMapByIds(locationIds));
        Set<Long> wardParentIds = new LinkedHashSet<>();
        for (Location location : locationById.values()) {
            if (location.getParentId() != null
                && LocationForm.BED.getCode().equals(location.getFormEnum())) {
                wardParentIds.add(location.getParentId());
            }
        }
        if (!wardParentIds.isEmpty()) {
            locationById.putAll(locationQueryService.loadLocationEntityMapByIds(wardParentIds));
        }

        Map<Long, List<EncounterParticipant>> participantsByEncounterId = new HashMap<>();
        if (!encounterIds.isEmpty()) {
            for (EncounterParticipant participant :
                encounterQueryService.listDisplayParticipantEntitiesByEncounterIds(encounterIds)) {
                if (participant.getEncounterId() == null) {
                    continue;
                }
                participantsByEncounterId
                    .computeIfAbsent(participant.getEncounterId(), ignored -> new ArrayList<>())
                    .add(participant);
            }
        }
        Set<Long> practitionerIds = new LinkedHashSet<>();
        for (List<EncounterParticipant> rows : participantsByEncounterId.values()) {
            for (EncounterParticipant row : rows) {
                if (row.getPractitionerId() != null) {
                    practitionerIds.add(row.getPractitionerId());
                }
            }
        }
        Map<Long, String> practitionerNameById = new HashMap<>();
        if (!practitionerIds.isEmpty()) {
            for (Practitioner practitioner : practitionerQueryService.findAllByIds(practitionerIds)) {
                if (practitioner.getId() != null && StringUtils.isNotEmpty(practitioner.getName())) {
                    practitionerNameById.put(practitioner.getId(), practitioner.getName());
                }
            }
        }

        List<InpatientEncounterContextQueryModel> models = new ArrayList<>(encounters.size());
        for (Encounter encounter : encounters) {
            InpatientEncounterContextQueryModel model = new InpatientEncounterContextQueryModel();
            model.setEncounterId(encounter.getId());
            model.setEncounterBusNo(encounter.getBusNo());
            model.setStatusEnum(encounter.getStatusEnum());
            model.setPatientId(encounter.getPatientId());
            model.setOrganizationId(encounter.getOrganizationId());
            model.setStartTime(encounter.getStartTime());
            model.setEndTime(encounter.getEndTime());
            model.setInWayCode(encounter.getInWayCode());
            model.setOutWayCode(encounter.getOutWayCode());
            if (encounter.getOrganizationId() != null) {
                model.setOrganizationName(organizationNameById.get(encounter.getOrganizationId()));
            }
            Patient patient = patientById.get(encounter.getPatientId());
            if (patient != null) {
                model.setPatientName(patient.getName());
                model.setGenderEnum(patient.getGenderEnum());
                if (patient.getBirthDate() != null) {
                    model.setBirthDate(patient.getBirthDate().atStartOfDay());
                    Date birthDate = Date.from(
                        patient.getBirthDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
                    String ageText = AgeCalculatorUtil.getAge(birthDate);
                    if (StringUtils.isNotEmpty(ageText)) {
                        model.setAgeText(ageText);
                    }
                }
            }
            applyActiveLocation(model, locationsByEncounterId.get(encounter.getId()), locationById);
            // Asm：主治/责任护士优先活动参与者；清床或召回后无活动行时已含末次 COMPLETED。
            applyActiveParticipants(
                model,
                participantsByEncounterId.get(encounter.getId()),
                practitionerNameById);
            models.add(model);
        }
        applyLastCompletedWardForDischarged(models);
        // Asm：无活动床时用末次已完成房-床回填「原{房}-{床}（未占床）」（召回/清床未占床轨）。
        applyLastCompletedBedWhenNoActiveBed(models);
        applyInpatientHoldRoutingFields(models, encounters);
        for (InpatientEncounterContextQueryModel model : models) {
            model.setStatusEnumText(InpatientEncounterStatusDisplayUtil.resolveDisplayText(
                model.getStatusEnum(),
                model.getTransferOrgId(),
                model.getDischargeTypeEnum()));
        }
        return models;
    }

    private void applyInpatientHoldRoutingFields(
        List<InpatientEncounterContextQueryModel> models,
        List<Encounter> encounters) {
        if (models == null || models.isEmpty() || encounters == null || encounters.isEmpty()) {
            return;
        }
        Map<Long, Encounter> encounterById = new HashMap<>(encounters.size() * 2);
        LinkedHashSet<Long> onHoldEncounterIds = new LinkedHashSet<>();
        for (Encounter encounter : encounters) {
            if (encounter == null || encounter.getId() == null) {
                continue;
            }
            encounterById.put(encounter.getId(), encounter);
            if (EncounterStatus.ON_HOLD.getCode().equals(encounter.getStatusEnum())) {
                onHoldEncounterIds.add(encounter.getId());
            }
        }
        if (onHoldEncounterIds.isEmpty()) {
            return;
        }
        Map<Long, List<EncounterHistory>> historiesByEncounterId = new HashMap<>();
        for (EncounterHistory row : encounterHistoryQueryService.listEncounterHistoryEntitiesByEncounterIds(
            onHoldEncounterIds)) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            historiesByEncounterId
                .computeIfAbsent(row.getEncounterId(), ignored -> new ArrayList<>())
                .add(row);
        }
        for (InpatientEncounterContextQueryModel model : models) {
            if (model == null || model.getEncounterId() == null) {
                continue;
            }
            if (!EncounterStatus.ON_HOLD.getCode().equals(model.getStatusEnum())) {
                continue;
            }
            Long encounterId = model.getEncounterId();
            List<EncounterHistory> histories = historiesByEncounterId.get(encounterId);
            Encounter encounter = encounterById.get(encounterId);
            if (encounter != null && StringUtils.isNotEmpty(encounter.getOutWayCode())) {
                model.setDischargeTypeEnum(CustomConstants.EncounterHistoryMilestoneName.DISCHARGE);
                continue;
            }
            Long transferTargetOrgId = resolveLatestTransferTargetOrganizationId(histories);
            if (transferTargetOrgId != null) {
                model.setTransferOrgId(transferTargetOrgId);
                continue;
            }
            if (hasDischargeMilestone(histories)) {
                model.setDischargeTypeEnum(CustomConstants.EncounterHistoryMilestoneName.DISCHARGE);
            }
        }
    }

    private Long resolveLatestTransferTargetOrganizationId(List<EncounterHistory> histories) {
        if (histories == null || histories.isEmpty()) {
            return null;
        }
        List<EncounterHistory> sorted = new ArrayList<>(histories);
        sorted.sort(Comparator.comparing(
            EncounterHistory::getCreatedAt,
            Comparator.nullsLast(Comparator.reverseOrder())));
        for (EncounterHistory row : sorted) {
            if (row == null) {
                continue;
            }
            if (!CustomConstants.EncounterHistoryMilestoneName.TRANSFER.equals(row.getName())) {
                continue;
            }
            if (row.getTargetOrganizationId() != null) {
                return row.getTargetOrganizationId();
            }
        }
        return null;
    }

    private boolean hasDischargeMilestone(List<EncounterHistory> histories) {
        if (histories == null || histories.isEmpty()) {
            return false;
        }
        for (EncounterHistory row : histories) {
            if (row == null) {
                continue;
            }
            if (CustomConstants.EncounterHistoryMilestoneName.DISCHARGE.equals(row.getName())) {
                return true;
            }
        }
        return false;
    }

    private void applyLastCompletedWardForDischarged(List<InpatientEncounterContextQueryModel> models) {
        if (models == null || models.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> dischargedEncounterIds = new LinkedHashSet<>();
        for (InpatientEncounterContextQueryModel model : models) {
            if (model == null || model.getEncounterId() == null || model.getWardId() != null) {
                continue;
            }
            if (!EncounterStatus.DISCHARGED.getCode().equals(model.getStatusEnum())) {
                continue;
            }
            dischargedEncounterIds.add(model.getEncounterId());
        }
        if (dischargedEncounterIds.isEmpty()) {
            return;
        }
        Map<Long, Long> wardIdByEncounterId =
            encounterQueryService.mapLastCompletedWardLocationIdByEncounterIds(dischargedEncounterIds);
        if (wardIdByEncounterId.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> wardLocationIds = new LinkedHashSet<>(wardIdByEncounterId.values());
        Map<Long, Location> wardById = locationQueryService.loadLocationEntityMapByIds(wardLocationIds);
        for (InpatientEncounterContextQueryModel model : models) {
            if (model == null || model.getEncounterId() == null || model.getWardId() != null) {
                continue;
            }
            Long wardId = wardIdByEncounterId.get(model.getEncounterId());
            if (wardId == null) {
                continue;
            }
            model.setWardId(wardId);
            Location ward = wardById.get(wardId);
            if (ward != null && StringUtils.isNotEmpty(ward.getName())) {
                model.setWardName(ward.getName());
            }
        }
    }

    /**
     * 无活动床时回填末次已完成床，展示名格式「原{房名}-{床名}（未占床）」。
     *
     * <p>召回后仅有活动病区、不占物理床；清床离院后亦无活动床。列表/结算/医生站/病案侧栏
     * 共用本投影。病房取床位父级 HOUSE；无房名时退化为「原{床名}（未占床）」。</p>
     *
     * @param models 住院上下文行
     */
    private void applyLastCompletedBedWhenNoActiveBed(List<InpatientEncounterContextQueryModel> models) {
        if (models == null || models.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> encounterIdsWithoutActiveBed = new LinkedHashSet<>();
        for (InpatientEncounterContextQueryModel model : models) {
            if (model == null || model.getEncounterId() == null) {
                continue;
            }
            // 已有活动床则保持原展示，不覆盖。
            if (model.getBedId() != null) {
                continue;
            }
            encounterIdsWithoutActiveBed.add(model.getEncounterId());
        }
        if (encounterIdsWithoutActiveBed.isEmpty()) {
            return;
        }
        Map<Long, Long> bedIdByEncounterId =
            encounterQueryService.mapLastCompletedBedLocationIdByEncounterIds(encounterIdsWithoutActiveBed);
        if (bedIdByEncounterId.isEmpty()) {
            return;
        }
        LinkedHashSet<Long> bedLocationIds = new LinkedHashSet<>(bedIdByEncounterId.values());
        Map<Long, Location> bedById = locationQueryService.loadLocationEntityMapByIds(bedLocationIds);
        LinkedHashSet<Long> houseLocationIds = new LinkedHashSet<>();
        for (Location bed : bedById.values()) {
            if (bed == null || bed.getParentId() == null) {
                continue;
            }
            // 床位父级一般为 HOUSE，批量预取供房-床拼装。
            houseLocationIds.add(bed.getParentId());
        }
        Map<Long, Location> houseById = Map.of();
        if (!houseLocationIds.isEmpty()) {
            houseById = locationQueryService.loadLocationEntityMapByIds(houseLocationIds);
        }
        for (InpatientEncounterContextQueryModel model : models) {
            if (model == null || model.getEncounterId() == null || model.getBedId() != null) {
                continue;
            }
            Long bedId = bedIdByEncounterId.get(model.getEncounterId());
            if (bedId == null) {
                continue;
            }
            model.setBedId(bedId);
            Location bed = bedById.get(bedId);
            String bedName = null;
            String houseName = null;
            if (bed != null) {
                bedName = bed.getName();
                if (bed.getParentId() != null) {
                    Location house = houseById.get(bed.getParentId());
                    // 仅 HOUSE 形态计入房名，避免误把病区当病房。
                    if (house != null
                        && LocationForm.HOUSE.getCode().equals(house.getFormEnum())
                        && StringUtils.isNotEmpty(house.getName())) {
                        houseName = house.getName();
                    }
                }
            }
            if (StringUtils.isEmpty(bedName)) {
                bedName = String.valueOf(bedId);
            }
            // 展示：原201-3（未占床）——房-床归属信息，非物理占床。
            model.setBedName(this.formatUnoccupiedLastBedDisplay(houseName, bedName));
        }
    }

    /**
     * 拼装无活动床展示文案：有房名则「原{房}-{床}（未占床）」，否则「原{床}（未占床）」。
     *
     * @param houseName 病房名称；可空
     * @param bedName   床位名称；须非空（调用方已兜底）
     * @return 未占床展示文案
     */
    private String formatUnoccupiedLastBedDisplay(String houseName, String bedName) {
        StringBuilder middle = new StringBuilder();
        // 有病房名时按「房-床」拼中间段。
        if (StringUtils.isNotEmpty(houseName)) {
            middle.append(houseName.trim());
            middle.append(GeneralSharedConstants.DASH);
        }
        middle.append(bedName);
        return InpatientNurseStationConstants.UNOCCUPIED_LAST_BED_DISPLAY_PREFIX
            + middle
            + InpatientNurseStationConstants.UNOCCUPIED_LAST_BED_DISPLAY_SUFFIX;
    }

    private void applyActiveLocation(
        InpatientEncounterContextQueryModel model,
        List<EncounterLocation> activeLocations,
        Map<Long, Location> locationById) {
        if (activeLocations == null || activeLocations.isEmpty()) {
            return;
        }
        Long bedId = null;
        String bedName = null;
        Long wardId = null;
        String wardName = null;
        for (EncounterLocation row : activeLocations) {
            if (row == null || row.getLocationId() == null) {
                continue;
            }
            Location location = locationById.get(row.getLocationId());
            if (location == null) {
                continue;
            }
            if (LocationForm.BED.getCode().equals(row.getFormEnum())
                || LocationForm.BED.getCode().equals(location.getFormEnum())) {
                bedId = location.getId();
                bedName = location.getName();
                if (location.getParentId() != null) {
                    Location parent = locationById.get(location.getParentId());
                    if (parent != null && LocationForm.WARD.getCode().equals(parent.getFormEnum())) {
                        wardId = parent.getId();
                        wardName = parent.getName();
                    } else if (parent != null && LocationForm.HOUSE.getCode().equals(parent.getFormEnum())
                        && parent.getParentId() != null) {
                        Location ward = locationById.get(parent.getParentId());
                        if (ward != null) {
                            wardId = ward.getId();
                            wardName = ward.getName();
                        }
                    }
                }
            } else if (LocationForm.WARD.getCode().equals(row.getFormEnum())
                || LocationForm.WARD.getCode().equals(location.getFormEnum())) {
                wardId = location.getId();
                wardName = location.getName();
            }
        }
        model.setBedId(bedId);
        model.setBedName(bedName);
        model.setWardId(wardId);
        model.setWardName(wardName);
    }

    /**
     * 回填主治医生与责任护士（展示口径：活动优先，无则末次已完成）。
     *
     * @param model                 住院上下文行
     * @param participants          展示参与者（见 {@code listDisplayParticipantEntitiesByEncounterIds}）
     * @param practitionerNameById  医务人员姓名索引
     */
    private void applyActiveParticipants(
        InpatientEncounterContextQueryModel model,
        List<EncounterParticipant> participants,
        Map<Long, String> practitionerNameById) {
        if (participants == null) {
            return;
        }
        for (EncounterParticipant participant : participants) {
            if (participant == null || participant.getPractitionerId() == null) {
                continue;
            }
            String roleCode = participant.getRoleCode();
            Long practitionerId = participant.getPractitionerId();
            String name = practitionerNameById.get(practitionerId);
            if (EncounterParticipantRole.ATTENDING_DOCTOR.getCode().equals(roleCode)) {
                model.setAttendingDoctorId(practitionerId);
                model.setAttendingDoctorName(name);
            } else if (EncounterParticipantRole.PRIMARY_NURSE.getCode().equals(roleCode)) {
                model.setPrimaryNurseId(practitionerId);
                model.setPrimaryNurseName(name);
            }
        }
    }
}
