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

package com.openhis.health.application.querysupport.encounter;

import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.entity.EncounterDiagnosis;
import com.openhis.health.domain.administration.encounter.entity.EncounterParticipant;
import com.openhis.health.domain.administration.encounter.queries.ClinicRegisterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.RegistrationSlipPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.ClinicRegisterQueryModel;
import com.openhis.health.domain.administration.encounter.queries.model.RegistrationSlipQueryModel;
import com.openhis.health.domain.administration.encounter.repository.EncounterParticipantRepository;
import com.openhis.health.domain.administration.encounter.repository.EncounterRepository;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.repository.HealthcareServiceRepository;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.repository.PatientRepository;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.RegistrationSlipConstants;
import com.openhis.health.domain.share.enums.AdministrativeGender;
import com.openhis.health.domain.share.enums.EncounterActivityStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterParticipantRole;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
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
 * 机构收窄由 {@link ICurrentUser#getCurrentOrganizationUnitId()} 写入 Criteria，禁止 PageQuery 传 organizationId。</p>
 *
 * <p>DOC-CONFLICT：就诊主档 Criteria 暂经 {@link EncounterRepository}；长期应收口 {@code EncounterQueryService}（迁移文档 10）。</p>
 */
@Component
@RequiredArgsConstructor
public class ClinicRegistrationLoader {

    private final EncounterRepository encounterRepository;
    private final EncounterParticipantRepository participantRepository;
    private final PatientRepository patientRepository;
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final HealthcareServiceRepository healthcareServiceRepository;
    private final ICurrentUser currentUser;

    /**
     * 门诊挂号列表分页：按机构/关键字/状态 Criteria 查询并批量装配患者、科室、医生展示字段。
     *
     * @param query 分页与筛选（pageNo 1-based）
     * @return 挂号列表读模型页
     */
    public Page<ClinicRegisterQueryModel> pageClinicRegister(ClinicRegisterPageQuery query) {
        ClinicRegisterPageQuery effective = query;
        if (effective == null) {
            effective = new ClinicRegisterPageQuery();
        }
        Pageable pageable = PageRequest.of(
            effective.springPageIndex(),
            effective.getBoundedPageSize(),
            Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT)));
        Specification<Encounter> specification = this.buildClinicRegisterSpecification(query);
        Page<Encounter> page = encounterRepository.findAll(specification, pageable);
        List<ClinicRegisterQueryModel> content = this.toClinicRegisterModels(page.getContent());
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    /**
     * 挂号单读模型分页：支持按挂号单号解析 encounterId 等筛选并装配富字段行。
     *
     * @param query 分页与筛选
     * @return 挂号单读模型页
     */
    public Page<RegistrationSlipQueryModel> pageRegistrationSlip(RegistrationSlipPageQuery query) {
        RegistrationSlipPageQuery effective = query;
        if (effective == null) {
            effective = new RegistrationSlipPageQuery();
        }
        Pageable pageable = PageRequest.of(
            effective.springPageIndex(),
            effective.getBoundedPageSize(),
            Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT)));
        Specification<Encounter> specification = this.buildRegistrationSlipSpecification(query);
        Page<Encounter> page = encounterRepository.findAll(specification, pageable);
        List<RegistrationSlipQueryModel> content = this.toRegistrationSlipModels(page.getContent());
        return new PageImpl<>(content, pageable, page.getTotalElements());
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
        return encounterRepository.findById(encounterId)
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

    /**
     * 挂号列表 Criteria：AMB + 会话机构 + 关键字（诊断描述 EXISTS）/状态/时段。
     */
    private Specification<Encounter> buildClinicRegisterSpecification(ClinicRegisterPageQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.AMB.getCode()));
            Long sessionOrganizationUnitId = this.currentUser.getCurrentOrganizationUnitId();
            if (sessionOrganizationUnitId != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), sessionOrganizationUnitId));
            }
            if (query != null) {
                if (StringUtils.isNotBlank(query.getKeyword())) {
                    String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
                    predicates.add(this.existsDiagnosisDescriptionLike(root, cb, criteriaQuery, likeIc));
                }
                if (StringUtils.isNotBlank(query.getStatusEnum())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum().trim()));
                }
                Expression<LocalDateTime> occurredAt = cb.coalesce(
                    root.get(FieldNameConstants.START_TIME),
                    root.get(FieldNameConstants.CREATED_AT));
                if (query.getStartTime() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(occurredAt, query.getStartTime()));
                }
                if (query.getEndTime() != null) {
                    predicates.add(cb.lessThanOrEqualTo(occurredAt, query.getEndTime()));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 挂号单查询 Criteria：按 slipNo 解析 encounterId 或显式 encounterId/patientId。
     */
    private Specification<Encounter> buildRegistrationSlipSpecification(RegistrationSlipPageQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.AMB.getCode()));
            if (query != null) {
                Long encounterId = query.getEncounterId();
                if (encounterId == null && StringUtils.isNotBlank(query.getSlipNo())) {
                    encounterId = this.parseEncounterIdFromSlipNo(query.getSlipNo().trim());
                }
                if (encounterId != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.ID), encounterId));
                }
                if (query.getPatientId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.PATIENT_ID), query.getPatientId()));
                }
                if (StringUtils.isNotBlank(query.getStatusEnum())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum().trim()));
                }
                if (query.getStartTime() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.START_TIME), query.getStartTime()));
                }
                if (query.getEndTime() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.END_TIME), query.getEndTime()));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 从挂号单号后缀解析就诊主键（前缀 {@link RegistrationSlipConstants#SLIP_NO_PREFIX}）。
     */
    private Long parseEncounterIdFromSlipNo(String slipNo) {
        if (StringUtils.isBlank(slipNo)) {
            return null;
        }
        String prefix = RegistrationSlipConstants.SLIP_NO_PREFIX;
        if (!slipNo.startsWith(prefix)) {
            return null;
        }
        String suffix = slipNo.substring(prefix.length());
        if (suffix.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(suffix.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * 就诊诊断子表描述模糊匹配（挂号列表关键字 OR 分支之一）。
     */
    private Predicate existsDiagnosisDescriptionLike(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        String pattern) {
        Subquery<Long> sq = criteriaQuery.subquery(Long.class);
        Root<EncounterDiagnosis> diagRoot = sq.from(EncounterDiagnosis.class);
        sq.select(cb.literal(1L));
        sq.where(cb.and(
            cb.equal(diagRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.isFalse(diagRoot.get(FieldNameConstants.IS_DELETED)),
            cb.like(cb.lower(diagRoot.get(FieldNameConstants.DESCRIPTION)), pattern)));
        return cb.exists(sq);
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
        if (!patientIds.isEmpty()) {
            for (Patient patient : patientRepository.findAllByIdIn(patientIds)) {
                if (patient.getId() != null) {
                    bundle.patientById.put(patient.getId(), patient);
                }
            }
        }
        bundle.organizationNameById.putAll(organizationQueryService.mapNamesByIds(organizationIds));
        if (!serviceTypeIds.isEmpty()) {
            for (HealthcareService svc : healthcareServiceRepository.findAllById(serviceTypeIds)) {
                if (svc.getId() != null) {
                    bundle.healthcareServiceById.put(svc.getId(), svc);
                }
            }
        }
        String activeStatus = EncounterActivityStatus.ACTIVE.getCode();
        if (!encounterIds.isEmpty()) {
            for (EncounterParticipant participant :
                participantRepository.findAllByEncounterIdInAndStatusEnum(encounterIds, activeStatus)) {
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
                if (practitioner.getId() != null && StringUtils.isNotBlank(practitioner.getName())) {
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
