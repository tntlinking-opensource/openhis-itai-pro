/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.registration.support;

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.entity.EncounterParticipant;
import com.openhis.health.domain.administration.encounter.queries.ClinicRegisterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.RegistrationSlipPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.ClinicRegisterQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.RegistrationSlipQueryModel;
import com.openhis.health.application.shared.encounter.EncounterContextReader;
import com.openhis.health.domain.administration.encounter.service.EncounterQueryService;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.service.PatientQueryService;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.share.constants.RegistrationSlipConstants;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterParticipantRole;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.utils.AgeCalculatorUtil;
import com.openhis.health.domain.share.utils.EnumUtil;
import lombok.RequiredArgsConstructor;
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 门诊挂号列表与挂号单读侧组装（替代 ClinicRegister / RegistrationSlip 读模型 RM）。
 *
 * <p>分页经 {@link ClinicRegisterPageQuery#springPageIndex()} / {@link ClinicRegisterPageQuery#getBoundedPageSize()}（B.10.11）；
 * 就诊记录列表不按当前登录科室收窄，仅按日期窗、关键字、状态等条件筛选。</p>
 */
@Component
@RequiredArgsConstructor
public class ClinicRegistrationLoader {

    private final EncounterContextReader encounterContextReader;
    private final EncounterQueryService encounterQueryService;
    private final PatientQueryService patientQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final HealthcareServiceQueryService healthcareServiceQueryService;

    /**
     * 门诊挂号列表分页：按关键字/状态/时段 Criteria 查询并批量装配患者、科室、医生展示字段。
     *
     * @param query 分页与筛选（pageNo 1-based）
     * @return 挂号列表读模型页
     */
    public Page<ClinicRegisterQueryModel> pageClinicRegister(ClinicRegisterPageQuery query) {
        Page<Encounter> page = encounterContextReader.pageClinicRegisterEncounters(query);
        List<ClinicRegisterQueryModel> content = this.toClinicRegisterModels(page.getContent());
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    /**
     * 门诊挂号列表计数（与 {@link #pageClinicRegister} 共用筛选谓词）。
     *
     * @param query 挂号筛选条件
     * @return 命中记录总数
     */
    public long countClinicRegister(ClinicRegisterPageQuery query) {
        return encounterContextReader.countClinicRegisterEncounters(query);
    }

    /**
     * 挂号单读模型分页：支持按挂号单号解析 encounterId 等筛选并装配富字段行。
     *
     * @param query 分页与筛选
     * @return 挂号单读模型页
     */
    public Page<RegistrationSlipQueryModel> pageRegistrationSlip(RegistrationSlipPageQuery query) {
        Page<Encounter> page = encounterContextReader.pageRegistrationSlipEncounters(query);
        List<RegistrationSlipQueryModel> content = this.toRegistrationSlipModels(page.getContent());
        return new PageImpl<>(content, page.getPageable(), page.getTotalElements());
    }

    /**
     * 按就诊主键取单条挂号单读模型（收费/退号等场景复用）。
     *
     * @param encounterId 就诊主键
     * @return 挂号单读模型；不存在时 empty
     */
    public Optional<RegistrationSlipQueryModel> getRegistrationSlipByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return Optional.empty();
        }
        return encounterQueryService.findOptionalEncounterEntityById(encounterId)
            .filter(this::isAmbEncounter)
            .map(encounter -> this.toRegistrationSlipModels(List.of(encounter)).get(0));
    }

    /** 门诊挂号口径：未删且 class 为 AMB。 */
    private boolean isAmbEncounter(Encounter encounter) {
        if (encounter == null || Boolean.TRUE.equals(encounter.getIsDeleted())) {
            return false;
        }
        return EncounterClass.AMB.getCode().equals(encounter.getClassEnum());
    }

    /** 挂号列表行装配（批量预热患者/科室/医生）。 */
    private List<ClinicRegisterQueryModel> toClinicRegisterModels(List<Encounter> encounters) {
        EnrichmentBundle bundle = this.loadEnrichment(encounters);
        List<ClinicRegisterQueryModel> models = new ArrayList<>(encounters.size());
        for (Encounter encounter : encounters) {
            if (!this.isAmbEncounter(encounter)) {
                continue;
            }
            ClinicRegisterQueryModel model = new ClinicRegisterQueryModel();
            model.setEncounterId(encounter.getId());
            model.setEncounterBusNo(encounter.getBusNo());
            model.setStatusEnum(encounter.getStatusEnum());
            model.setStartTime(encounter.getStartTime());
            model.setServiceTypeId(encounter.getServiceTypeId());
            EncounterStatus status = EnumUtil.fromCode(EncounterStatus.class, encounter.getStatusEnum());
            if (status != null) {
                model.setStatusEnumText(status.getDisplay());
            }
            if (encounter.getOrganizationId() != null) {
                model.setOrganizationName(bundle.organizationNameById.get(encounter.getOrganizationId()));
            }
            if (encounter.getServiceTypeId() != null) {
                HealthcareService svc = bundle.healthcareServiceById.get(encounter.getServiceTypeId());
                if (svc != null) {
                    model.setServiceTypeName(svc.getName());
                }
            }
            Patient patient = bundle.patientById.get(encounter.getPatientId());
            if (patient != null) {
                model.setPatientName(patient.getName());
                model.setPatientGenderCode(patient.getGenderEnum());
                model.setContactPhone(patient.getPhone());
                AdministrativeGender gender = EnumUtil.fromCode(AdministrativeGender.class, patient.getGenderEnum());
                if (gender != null) {
                    model.setPatientGenderText(gender.getDisplay());
                }
                if (patient.getBirthDate() != null) {
                    Date birthDate = Date.from(
                        patient.getBirthDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
                    String ageText = AgeCalculatorUtil.getAge(birthDate);
                    if (StringUtils.isNotEmpty(ageText)) {
                        model.setPatientAgeText(ageText);
                    }
                }
            }
            this.applyDoctor(model, bundle, encounter.getId());
            models.add(model);
        }
        return models;
    }

    /** 挂号列表医生列：优先接诊/挂号医生，其次主治。 */
    private void applyDoctor(ClinicRegisterQueryModel model, EnrichmentBundle bundle, Long encounterId) {
        List<EncounterParticipant> participants = bundle.participantsByEncounterId.get(encounterId);
        if (participants == null || participants.isEmpty()) {
            return;
        }
        EncounterParticipant picked = null;
        for (EncounterParticipant participant : participants) {
            if (participant == null || participant.getPractitionerId() == null) {
                continue;
            }
            String roleCode = participant.getRoleCode();
            if (EncounterParticipantRole.ADMITTER.getCode().equals(roleCode)
                || EncounterParticipantRole.REGISTRATION_DOCTOR.getCode().equals(roleCode)) {
                picked = participant;
                break;
            }
            if (picked == null && EncounterParticipantRole.ATTENDING_DOCTOR.getCode().equals(roleCode)) {
                picked = participant;
            }
        }
        if (picked == null) {
            picked = participants.get(0);
        }
        if (picked != null && picked.getPractitionerId() != null) {
            model.setDoctorName(bundle.practitionerNameById.get(picked.getPractitionerId()));
        }
    }

    /** 挂号单读模型行装配。 */
    private List<RegistrationSlipQueryModel> toRegistrationSlipModels(List<Encounter> encounters) {
        EnrichmentBundle bundle = this.loadEnrichment(encounters);
        List<RegistrationSlipQueryModel> models = new ArrayList<>(encounters.size());
        for (Encounter encounter : encounters) {
            if (!this.isAmbEncounter(encounter)) {
                continue;
            }
            RegistrationSlipQueryModel model = new RegistrationSlipQueryModel();
            model.setEncounterId(encounter.getId());
            model.setSlipNo(RegistrationSlipConstants.SLIP_NO_PREFIX + encounter.getId());
            EncounterStatus status = EnumUtil.fromCode(EncounterStatus.class, encounter.getStatusEnum());
            if (status != null) {
                model.setStatusEnumText(status.getDisplay());
            }
            if (encounter.getOrganizationId() != null) {
                model.setOrganizationName(bundle.organizationNameById.get(encounter.getOrganizationId()));
            }
            if (encounter.getServiceTypeId() != null) {
                HealthcareService svc = bundle.healthcareServiceById.get(encounter.getServiceTypeId());
                if (svc != null) {
                    model.setServiceTypeName(svc.getName());
                }
            }
            Patient patient = bundle.patientById.get(encounter.getPatientId());
            if (patient != null) {
                model.setPatientName(patient.getName());
            }
            ClinicRegisterQueryModel row = new ClinicRegisterQueryModel();
            row.setEncounterId(encounter.getId());
            this.applyDoctor(row, bundle, encounter.getId());
            model.setDoctorName(row.getDoctorName());
            models.add(model);
        }
        return models;
    }

    /** 批量加载患者、科室、号别、参与者、医生姓名（A.9.1 批量 IN）。 */
    private EnrichmentBundle loadEnrichment(List<Encounter> encounters) {
        EnrichmentBundle bundle = new EnrichmentBundle();
        if (encounters == null || encounters.isEmpty()) {
            return bundle;
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
        bundle.patientById.putAll(patientQueryService.loadPatientEntityMapByIds(patientIds));
        bundle.organizationNameById.putAll(organizationQueryService.mapNamesByIds(organizationIds));
        bundle.healthcareServiceById.putAll(healthcareServiceQueryService.loadHealthcareServiceMapByIds(serviceTypeIds));
        if (!encounterIds.isEmpty()) {
            // 展示口径：活动参与者优先；退号收口后回落末次 COMPLETED，避免医生列空白。
            for (EncounterParticipant participant :
                encounterQueryService.listDisplayParticipantEntitiesByEncounterIds(encounterIds)) {
                if (participant.getEncounterId() == null) {
                    continue;
                }
                bundle.participantsByEncounterId
                    .computeIfAbsent(participant.getEncounterId(), ignored -> new ArrayList<>())
                    .add(participant);
            }
        }
        Set<Long> practitionerIds = new LinkedHashSet<>();
        for (List<EncounterParticipant> rows : bundle.participantsByEncounterId.values()) {
            for (EncounterParticipant row : rows) {
                if (row.getPractitionerId() != null) {
                    practitionerIds.add(row.getPractitionerId());
                }
            }
        }
        if (!practitionerIds.isEmpty()) {
            for (Practitioner practitioner : practitionerQueryService.findAllByIds(practitionerIds)) {
                if (practitioner.getId() != null && StringUtils.isNotEmpty(practitioner.getName())) {
                    bundle.practitionerNameById.put(practitioner.getId(), practitioner.getName());
                }
            }
        }
        return bundle;
    }

    /** 挂号列表批量预热结果（仅本 Loader 内使用）。 */
    private static final class EnrichmentBundle {
        private final Map<Long, Patient> patientById = new HashMap<>();
        private final Map<Long, String> organizationNameById = new HashMap<>();
        private final Map<Long, HealthcareService> healthcareServiceById = new HashMap<>();
        private final Map<Long, List<EncounterParticipant>> participantsByEncounterId = new HashMap<>();
        private final Map<Long, String> practitionerNameById = new HashMap<>();
    }
}
