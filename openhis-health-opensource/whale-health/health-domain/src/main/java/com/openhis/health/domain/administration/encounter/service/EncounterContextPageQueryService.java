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

package com.openhis.health.domain.administration.encounter.service;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.entity.EncounterDiagnosis;
import com.openhis.health.domain.administration.encounter.entity.EncounterLocation;
import com.openhis.health.domain.administration.encounter.entity.EncounterParticipant;
import com.openhis.health.domain.administration.encounter.queries.ClinicRegisterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.EncounterSessionScope;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.OutpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.RegistrationSlipPageQuery;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.InpatientRegistrationConstants;
import com.openhis.health.domain.share.constants.InpatientSettleQueueTab;
import com.openhis.health.domain.share.constants.RegistrationSlipConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.EncounterActivityStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterParticipantRole;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.workflow.devicerequest.entity.DeviceRequest;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 就诊上下文分页/列表 Criteria 组装（P2：自 module-support Loader 下沉至 domain）。
 *
 * <p>Loader 负责会话收窄解析与读模型 enrichment；本服务仅产出 {@link Encounter} 分页/列表。</p>
 */
@Service
@RequiredArgsConstructor
public class EncounterContextPageQueryService {

    private final EncounterQueryService encounterQueryService;
    private final LocationQueryService locationQueryService;

    /**
     * 门诊挂号列表分页实体查询。
     */
    public Page<Encounter> pageClinicRegisterEncounters(ClinicRegisterPageQuery query) {
        ClinicRegisterPageQuery effective = query != null ? query : new ClinicRegisterPageQuery();
        Pageable pageable = PageRequest.of(
            effective.springPageIndex(),
            effective.getBoundedPageSize(),
            Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT)));
        return encounterQueryService.pageEncounterEntities(
            this.buildClinicRegisterSpecification(effective), pageable);
    }

    /**
     * 门诊挂号列表计数（与 {@link #pageClinicRegisterEncounters} 共用筛选谓词）。
     */
    public long countClinicRegisterEncounters(ClinicRegisterPageQuery query) {
        ClinicRegisterPageQuery effective = query != null ? query : new ClinicRegisterPageQuery();
        return encounterQueryService.countEncounterEntities(this.buildClinicRegisterSpecification(effective));
    }

    /**
     * 挂号单分页实体查询。
     */
    public Page<Encounter> pageRegistrationSlipEncounters(RegistrationSlipPageQuery query) {
        RegistrationSlipPageQuery effective = query != null ? query : new RegistrationSlipPageQuery();
        Pageable pageable = PageRequest.of(
            effective.springPageIndex(),
            effective.getBoundedPageSize(),
            Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT)));
        return encounterQueryService.pageEncounterEntities(
            this.buildRegistrationSlipSpecification(effective), pageable);
    }

    /**
     * 门诊就诊上下文分页（AMB，机构会话收窄）。
     */
    public Page<Encounter> pageOutpatientEncounterEntities(
        OutpatientEncounterPageQuery query,
        EncounterSessionScope scope) {
        OutpatientEncounterPageQuery effective = query != null ? query : new OutpatientEncounterPageQuery();
        EncounterSessionScope effectiveScope = scope != null ? scope : EncounterSessionScope.empty();
        Sort sort = this.resolveOutpatientListSort(effective);
        Pageable pageable = PageRequest.of(
            effective.springPageIndex(),
            effective.getBoundedPageSize(),
            sort);
        return encounterQueryService.pageEncounterEntities(
            this.buildOutpatientSpecification(effective, effectiveScope), pageable);
    }

    /**
     * 门诊护士站执行队列分页。
     */
    public Page<Encounter> pageNurseStationOutpatientEncounters(AbstractPageQuery query) {
        AbstractPageQuery effective = query != null ? query : new AbstractPageQuery();
        int pageIndex = effective.springPageIndex();
        int pageSize = 20;
        if (effective.getPageSize() != null && effective.getPageSize() > 0) {
            pageSize = effective.getPageSize();
        }
        if (pageSize > 100) {
            pageSize = 100;
        }
        Sort sort = Sort.by(
            Sort.Order.desc(FieldNameConstants.START_TIME),
            Sort.Order.desc(FieldNameConstants.ID));
        Pageable pageable = PageRequest.of(pageIndex, pageSize, sort);
        return encounterQueryService.pageEncounterEntities(
            this.buildNurseStationOutpatientSpecification(effective), pageable);
    }

    /**
     * 住院就诊上下文分页。
     */
    public Page<Encounter> pageInpatientEncounterEntities(
        InpatientEncounterPageQuery query,
        EncounterSessionScope scope) {
        InpatientEncounterPageQuery effective = query != null ? query : new InpatientEncounterPageQuery();
        EncounterSessionScope effectiveScope = scope != null ? scope : EncounterSessionScope.empty();
        Pageable pageable = this.resolveInpatientPageable(effective);
        return encounterQueryService.pageEncounterEntities(
            this.buildInpatientSpecification(effective, effectiveScope), pageable);
    }

    /**
     * 住院就诊上下文列表（无分页，按创建时间倒序）。
     */
    public List<Encounter> listInpatientEncounterEntities(
        InpatientEncounterPageQuery query,
        EncounterSessionScope scope) {
        InpatientEncounterPageQuery effective = query != null ? query : new InpatientEncounterPageQuery();
        EncounterSessionScope effectiveScope = scope != null ? scope : EncounterSessionScope.empty();
        Sort sort = Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT));
        return encounterQueryService.listEncounterEntities(
            this.buildInpatientSpecification(effective, effectiveScope), sort);
    }

    private Specification<Encounter> buildClinicRegisterSpecification(ClinicRegisterPageQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.AMB.getCode()));
            if (query != null) {
                if (StringUtils.isNotBlank(query.getKeyword())) {
                    String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
                    predicates.add(this.existsDiagnosisDescriptionLike(root, cb, criteriaQuery, likeIc));
                }
                if (StringUtils.isNotBlank(query.getStatusEnum())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum().trim()));
                }
                if (query.getExcludeStatusEnums() != null && !query.getExcludeStatusEnums().isEmpty()) {
                    List<Predicate> excludePredicates = new ArrayList<>();
                    for (String excluded : query.getExcludeStatusEnums()) {
                        if (StringUtils.isNotBlank(excluded)) {
                            excludePredicates.add(
                                cb.notEqual(root.get(FieldNameConstants.STATUS_ENUM), excluded.trim()));
                        }
                    }
                    if (!excludePredicates.isEmpty()) {
                        predicates.add(cb.and(excludePredicates.toArray(new Predicate[0])));
                    }
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
                if (query.getPatientId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.PATIENT_ID), query.getPatientId()));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

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

    private Specification<Encounter> buildOutpatientSpecification(
        OutpatientEncounterPageQuery query,
        EncounterSessionScope scope) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(this.outpatientClassEnumPredicate(root, cb));
            Long sessionOrganizationUnitId = scope.sessionOrganizationUnitId();
            if (sessionOrganizationUnitId != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), sessionOrganizationUnitId));
            } else if (scope.organizationScopeEnforcementActive()) {
                predicates.add(cb.disjunction());
            }
            if (query != null) {
                if (query.getStatusEnumIn() != null && !query.getStatusEnumIn().isEmpty()) {
                    predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(query.getStatusEnumIn()));
                } else if (StringUtils.isNotBlank(query.getStatusEnum())) {
                    predicates.add(cb.equal(
                        root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum().trim()));
                }
                if (query.getDoctorStationViewerPractitionerId() != null) {
                    predicates.add(this.buildDoctorStationViewerVisibilityPredicate(
                        root, cb, criteriaQuery, query));
                } else if (query.getDoctorId() != null) {
                    predicates.add(this.existsActiveDoctorParticipant(
                        root, cb, criteriaQuery, query.getDoctorId()));
                }
                if (StringUtils.isNotBlank(query.getKeyword())) {
                    String pattern = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
                    Predicate busNoLike = cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), pattern);
                    predicates.add(cb.or(busNoLike, this.existsPatientKeyword(root, cb, criteriaQuery, pattern)));
                }
                if (query.getStartDate() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(
                        root.get(FieldNameConstants.START_TIME), query.getStartDate()));
                }
                if (query.getEndDate() != null) {
                    predicates.add(cb.lessThanOrEqualTo(
                        root.get(FieldNameConstants.START_TIME), query.getEndDate()));
                }
                if (query.getPatientId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.PATIENT_ID), query.getPatientId()));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Specification<Encounter> buildNurseStationOutpatientSpecification(AbstractPageQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(this.outpatientClassEnumPredicate(root, cb));
            if (StringUtils.isNotBlank(query.getKeyword())) {
                String pattern = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
                predicates.add(this.existsPatientKeyword(root, cb, criteriaQuery, pattern));
            }
            if (query.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get(FieldNameConstants.START_TIME), query.getStartDate()));
            }
            if (query.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    root.get(FieldNameConstants.START_TIME), query.getEndDate()));
            }
            predicates.add(this.buildNurseStationRequestExistsAny(root, cb, criteriaQuery));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Predicate outpatientClassEnumPredicate(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb) {
        return cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.AMB.getCode());
    }

    private Predicate buildDoctorStationViewerVisibilityPredicate(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        OutpatientEncounterPageQuery query) {
        Long viewerPractitionerId = query.getDoctorStationViewerPractitionerId();
        List<String> statusScope = this.resolveDoctorStationStatusScope(query);
        List<Predicate> visibilityOr = new ArrayList<>();
        if (this.statusScopeIncludes(statusScope, EncounterStatus.DISCHARGED.getCode())) {
            visibilityOr.add(cb.equal(
                root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.DISCHARGED.getCode()));
        }
        List<String> waitingStatuses = this.filterStatusScope(
            statusScope,
            EncounterStatus.REGISTERED.getCode(),
            EncounterStatus.TRIAGED.getCode());
        if (!waitingStatuses.isEmpty()) {
            Predicate waitingStatusIn = root.get(FieldNameConstants.STATUS_ENUM).in(waitingStatuses);
            Predicate noRegistrationAssignment = cb.and(
                this.notExistsActiveParticipantWithRole(
                    root, cb, criteriaQuery, EncounterParticipantRole.REGISTRATION_DOCTOR.getCode()),
                this.notExistsActiveParticipantWithRole(
                    root, cb, criteriaQuery, EncounterParticipantRole.ADMITTER.getCode()));
            Predicate registrationDoctorIsViewer = this.existsActiveParticipantWithRoleAndPractitioner(
                root, cb, criteriaQuery,
                EncounterParticipantRole.REGISTRATION_DOCTOR.getCode(),
                viewerPractitionerId);
            Predicate legacyRegistrationAdmitterIsViewer = this.existsActiveParticipantWithRoleAndPractitioner(
                root, cb, criteriaQuery,
                EncounterParticipantRole.ADMITTER.getCode(),
                viewerPractitionerId);
            Predicate waitingDoctorVisible = cb.or(
                noRegistrationAssignment,
                registrationDoctorIsViewer,
                legacyRegistrationAdmitterIsViewer);
            visibilityOr.add(cb.and(waitingStatusIn, waitingDoctorVisible));
        }
        List<String> inConsultStatuses = this.filterStatusScope(
            statusScope,
            EncounterStatus.IN_PROGRESS.getCode(),
            EncounterStatus.ON_HOLD.getCode());
        if (!inConsultStatuses.isEmpty()) {
            Predicate inConsultStatusIn = root.get(FieldNameConstants.STATUS_ENUM).in(inConsultStatuses);
            Predicate admitterIsViewer = this.existsActiveParticipantWithRoleAndPractitioner(
                root, cb, criteriaQuery,
                EncounterParticipantRole.ADMITTER.getCode(),
                viewerPractitionerId);
            visibilityOr.add(cb.and(inConsultStatusIn, admitterIsViewer));
        }
        if (visibilityOr.isEmpty()) {
            return cb.disjunction();
        }
        if (visibilityOr.size() == 1) {
            return visibilityOr.get(0);
        }
        Predicate[] branches = visibilityOr.toArray(new Predicate[0]);
        return cb.or(branches);
    }

    private List<String> resolveDoctorStationStatusScope(OutpatientEncounterPageQuery query) {
        if (query.getStatusEnumIn() != null && !query.getStatusEnumIn().isEmpty()) {
            return query.getStatusEnumIn();
        }
        if (StringUtils.isNotBlank(query.getStatusEnum())) {
            return List.of(query.getStatusEnum().trim());
        }
        return List.of();
    }

    private boolean statusScopeIncludes(List<String> statusScope, String statusCode) {
        for (String scoped : statusScope) {
            if (statusCode.equals(scoped)) {
                return true;
            }
        }
        return false;
    }

    private List<String> filterStatusScope(List<String> statusScope, String... allowedCodes) {
        List<String> filtered = new ArrayList<>();
        for (String allowed : allowedCodes) {
            if (this.statusScopeIncludes(statusScope, allowed)) {
                filtered.add(allowed);
            }
        }
        return filtered;
    }

    private Predicate existsActiveParticipantWithRoleAndPractitioner(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        String roleCode,
        Long practitionerId) {
        Subquery<Long> sq = criteriaQuery.subquery(Long.class);
        Root<EncounterParticipant> participantRoot = sq.from(EncounterParticipant.class);
        sq.select(participantRoot.get(FieldNameConstants.ENCOUNTER_ID));
        sq.where(cb.and(
            cb.equal(participantRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.isFalse(participantRoot.get(FieldNameConstants.IS_DELETED)),
            cb.equal(participantRoot.get(FieldNameConstants.STATUS_ENUM),
                EncounterActivityStatus.ACTIVE.getCode()),
            cb.equal(participantRoot.get(FieldNameConstants.ROLE_CODE), roleCode),
            cb.equal(participantRoot.get(FieldNameConstants.PRACTITIONER_ID), practitionerId)));
        return cb.exists(sq);
    }

    private Predicate notExistsActiveParticipantWithRole(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        String roleCode) {
        Subquery<Long> sq = criteriaQuery.subquery(Long.class);
        Root<EncounterParticipant> participantRoot = sq.from(EncounterParticipant.class);
        sq.select(participantRoot.get(FieldNameConstants.ENCOUNTER_ID));
        sq.where(cb.and(
            cb.equal(participantRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.isFalse(participantRoot.get(FieldNameConstants.IS_DELETED)),
            cb.equal(participantRoot.get(FieldNameConstants.STATUS_ENUM),
                EncounterActivityStatus.ACTIVE.getCode()),
            cb.equal(participantRoot.get(FieldNameConstants.ROLE_CODE), roleCode)));
        return cb.not(cb.exists(sq));
    }

    private Predicate existsActiveDoctorParticipant(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        Long doctorId) {
        Subquery<Long> sq = criteriaQuery.subquery(Long.class);
        Root<EncounterParticipant> participantRoot = sq.from(EncounterParticipant.class);
        sq.select(participantRoot.get(FieldNameConstants.ENCOUNTER_ID));
        sq.where(cb.and(
            cb.equal(participantRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.equal(participantRoot.get(FieldNameConstants.STATUS_ENUM),
                EncounterActivityStatus.ACTIVE.getCode()),
            cb.equal(participantRoot.get(FieldNameConstants.PRACTITIONER_ID), doctorId),
            participantRoot.get(FieldNameConstants.ROLE_CODE).in(
                EncounterParticipantRole.ADMITTER.getCode(),
                EncounterParticipantRole.ATTENDING_DOCTOR.getCode(),
                EncounterParticipantRole.REGISTRATION_DOCTOR.getCode())));
        return cb.exists(sq);
    }

    private Predicate existsPatientKeyword(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        String pattern) {
        Subquery<Long> sq = criteriaQuery.subquery(Long.class);
        Root<Patient> patientRoot = sq.from(Patient.class);
        sq.select(patientRoot.get(FieldNameConstants.ID));
        sq.where(cb.and(
            cb.equal(patientRoot.get(FieldNameConstants.ID), root.get(FieldNameConstants.PATIENT_ID)),
            cb.isFalse(patientRoot.get(FieldNameConstants.IS_DELETED)),
            cb.or(
                cb.like(cb.lower(patientRoot.get(FieldNameConstants.NAME)), pattern),
                cb.like(cb.lower(patientRoot.get(FieldNameConstants.PY_STR)), pattern),
                cb.like(cb.lower(patientRoot.get(FieldNameConstants.WB_STR)), pattern))));
        return cb.exists(sq);
    }

    private Predicate buildNurseStationRequestExistsAny(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> owningQuery) {
        Subquery<Long> serviceSq = owningQuery.subquery(Long.class);
        Root<ServiceRequest> sr = serviceSq.from(ServiceRequest.class);
        serviceSq.select(sr.get(FieldNameConstants.ID));
        serviceSq.where(cb.and(
            cb.equal(sr.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.equal(sr.get(FieldNameConstants.STATUS_ENUM), RequestStatus.COMPLETED.getCode()),
            cb.isNull(sr.get(FieldNameConstants.REFUND_SERVICE_ID)),
            cb.isFalse(sr.get(FieldNameConstants.IS_DELETED))));

        Subquery<Long> deviceSq = owningQuery.subquery(Long.class);
        Root<DeviceRequest> dr = deviceSq.from(DeviceRequest.class);
        deviceSq.select(dr.get(FieldNameConstants.ID));
        deviceSq.where(cb.and(
            cb.equal(dr.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.equal(dr.get(FieldNameConstants.STATUS_ENUM), RequestStatus.COMPLETED.getCode()),
            cb.isNull(dr.get(FieldNameConstants.REFUND_DEVICE_ID)),
            cb.isFalse(dr.get(FieldNameConstants.IS_DELETED))));

        Subquery<Long> medicationSq = owningQuery.subquery(Long.class);
        Root<MedicationRequest> mr = medicationSq.from(MedicationRequest.class);
        Root<MedicationDefinition> md = medicationSq.from(MedicationDefinition.class);
        medicationSq.select(mr.get(FieldNameConstants.ID));
        medicationSq.where(cb.and(
            cb.equal(mr.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.equal(mr.get(FieldNameConstants.STATUS_ENUM), RequestStatus.COMPLETED.getCode()),
            cb.isNull(mr.get(FieldNameConstants.REFUND_MEDICINE_ID)),
            cb.isFalse(mr.get(FieldNameConstants.IS_DELETED)),
            cb.equal(mr.get(FieldNameConstants.MEDICATION_ID), md.get(FieldNameConstants.ID)),
            cb.isFalse(md.get(FieldNameConstants.IS_DELETED)),
            cb.or(
                cb.isTrue(md.get(FieldNameConstants.INJECT_FLAG)),
                cb.isTrue(md.get(FieldNameConstants.SKIN_TEST_FLAG)))));

        return cb.or(
            cb.exists(serviceSq),
            cb.exists(deviceSq),
            cb.exists(medicationSq),
            this.existsNurseStationChargeRefundForEncounter(root, cb, owningQuery));
    }

    private Predicate existsNurseStationChargeRefundForEncounter(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> owningQuery) {
        Subquery<Long> chargeSq = owningQuery.subquery(Long.class);
        Root<ChargeItem> chargeRoot = chargeSq.from(ChargeItem.class);
        chargeSq.select(chargeRoot.get(FieldNameConstants.ID));
        chargeSq.where(cb.and(
            cb.equal(chargeRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.isFalse(chargeRoot.get(FieldNameConstants.IS_DELETED)),
            chargeRoot.get(FieldNameConstants.STATUS_ENUM).in(
                ChargeItemStatus.REFUNDING.getCode(),
                ChargeItemStatus.REFUNDED.getCode())));
        return cb.exists(chargeSq);
    }

    private Sort resolveOutpatientListSort(OutpatientEncounterPageQuery query) {
        if (query == null) {
            return Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT));
        }
        if (query.getStatusEnumIn() != null && query.getStatusEnumIn().size() > 1) {
            return Sort.by(
                Sort.Order.desc(FieldNameConstants.START_TIME),
                Sort.Order.desc(FieldNameConstants.ID));
        }
        if (query.getStatusEnumIn() != null && query.getStatusEnumIn().size() == 1) {
            String onlyStatus = query.getStatusEnumIn().get(0);
            if (EncounterStatus.IN_PROGRESS.getCode().equals(onlyStatus)) {
                return Sort.by(
                    Sort.Order.desc(FieldNameConstants.RECEPTION_TIME),
                    Sort.Order.desc(FieldNameConstants.ID));
            }
            if (EncounterStatus.PLANNED.getCode().equals(onlyStatus)
                || EncounterStatus.REGISTERED.getCode().equals(onlyStatus)) {
                return Sort.by(
                    Sort.Order.desc(FieldNameConstants.START_TIME),
                    Sort.Order.desc(FieldNameConstants.ID));
            }
            return Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT));
        }
        if (EncounterStatus.IN_PROGRESS.getCode().equals(query.getStatusEnum())) {
            return Sort.by(
                Sort.Order.desc(FieldNameConstants.RECEPTION_TIME),
                Sort.Order.desc(FieldNameConstants.ID));
        }
        if (EncounterStatus.PLANNED.getCode().equals(query.getStatusEnum())) {
            return Sort.by(
                Sort.Order.desc(FieldNameConstants.START_TIME),
                Sort.Order.desc(FieldNameConstants.ID));
        }
        return Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT));
    }

    private Pageable resolveInpatientPageable(InpatientEncounterPageQuery query) {
        int pageNo = 0;
        int pageSize = 20;
        if (query != null) {
            pageNo = query.springPageIndex();
            pageSize = query.getBoundedPageSize();
        }
        return PageRequest.of(pageNo, pageSize, this.resolveInpatientSort(query));
    }

    private Sort resolveInpatientSort(InpatientEncounterPageQuery query) {
        if (query != null && Boolean.TRUE.equals(query.getDoctorStationQueue())) {
            return Sort.by(
                Sort.Order.asc(FieldNameConstants.BUS_NO),
                Sort.Order.asc(FieldNameConstants.ID));
        }
        if (query != null
            && (Boolean.TRUE.equals(query.getPrepayEligibleOnly())
            || Boolean.TRUE.equals(query.getNurseStationQueue())
            || Boolean.TRUE.equals(query.getSettleQueue()))) {
            return Sort.by(
                Sort.Order.desc(FieldNameConstants.START_TIME),
                Sort.Order.desc(FieldNameConstants.ID));
        }
        return Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT));
    }

    private Specification<Encounter> buildInpatientSpecification(
        InpatientEncounterPageQuery query,
        EncounterSessionScope scope) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.IMP.getCode()));
            if (query == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }
            if (Boolean.TRUE.equals(query.getDoctorStationQueue())) {
                this.applyDoctorStationVisibleStatus(predicates, root, cb);
                this.applyDoctorStationOrganizationScope(predicates, root, cb, scope);
            } else if (Boolean.TRUE.equals(query.getNurseStationQueue())) {
                this.applyNurseStationStatusScope(predicates, root, cb, query);
                this.applyNurseStationGeographicScope(predicates, root, cb, criteriaQuery, query, scope);
                this.applyEncounterIdsIn(predicates, root, cb, query.getEncounterIdsIn());
            } else if (Boolean.TRUE.equals(query.getSettleQueue())) {
                this.applySettleQueueFilter(predicates, root, cb, criteriaQuery, query);
            } else {
                if (query.getWardId() != null) {
                    this.applyWardScope(predicates, root, cb, criteriaQuery, query.getWardId());
                }
                if (Boolean.TRUE.equals(query.getPrepayEligibleOnly())) {
                    this.applyPrepayEligibleStatus(predicates, root, cb);
                } else {
                    this.applyRegistrationListStatus(predicates, root, cb, query);
                }
            }
            if (query.getPatientId() != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.PATIENT_ID), query.getPatientId()));
            }
            if (StringUtils.isNotBlank(query.getStatusEnum())) {
                predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum()));
            }
            this.applyAdmissionDate(predicates, root, cb, query);
            this.applyKeyword(predicates, root, cb, criteriaQuery, query);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private void applyDoctorStationVisibleStatus(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb) {
        predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(
            EncounterStatus.IN_PROGRESS.getCode(),
            EncounterStatus.ON_HOLD.getCode()));
    }

    private void applyDoctorStationOrganizationScope(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        EncounterSessionScope scope) {
        Long organizationId = scope.sessionOrganizationUnitId();
        if (organizationId != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId));
            return;
        }
        if (scope.organizationScopeEnforcementActive()) {
            predicates.add(cb.disjunction());
        }
    }

    private void applyNurseStationVisibleStatus(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb) {
        predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(
            EncounterStatus.REGISTERED.getCode(),
            EncounterStatus.IN_PROGRESS.getCode(),
            EncounterStatus.ON_HOLD.getCode(),
            EncounterStatus.PENDING.getCode(),
            EncounterStatus.COMPLETED.getCode()));
    }

    /**
     * 护士站列表状态收窄：支持默认在院并集与召回列表 {@code SETTLED_DISCHARGED}（AS-IP-NUR-001 BR-IP-NUR-012）。
     */
    private void applyNurseStationStatusScope(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        InpatientEncounterPageQuery query) {
        Collection<String> statusIn = this.resolveNurseStationStatusEnumIn(query);
        if (statusIn != null && !statusIn.isEmpty()) {
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(statusIn));
            return;
        }
        this.applyNurseStationVisibleStatus(predicates, root, cb);
    }

    /**
     * 解析护士站状态筛选：优先 {@code statusEnumIn}，其次 {@code nurseStationStatusFilter} 常量。
     */
    private Collection<String> resolveNurseStationStatusEnumIn(InpatientEncounterPageQuery query) {
        if (query.getStatusEnumIn() != null && !query.getStatusEnumIn().isEmpty()) {
            return query.getStatusEnumIn();
        }
        String filter = query.getNurseStationStatusFilter();
        if (StringUtils.isBlank(filter)) {
            return Collections.emptyList();
        }
        if (InpatientNurseStationConstants.STATUS_FILTER_SETTLED_DISCHARGED.equals(filter.trim())) {
            return Arrays.asList(
                EncounterStatus.COMPLETED.getCode(),
                EncounterStatus.DISCHARGED.getCode());
        }
        return Collections.singletonList(filter.trim());
    }

    /**
     * 召回列表是否包含 {@code DISCHARGED}：须改用末次已完成病区地理收窄，而非活动床/病区。
     */
    private boolean nurseStationNeedsRecallGeographicScope(InpatientEncounterPageQuery query) {
        Collection<String> statusIn = this.resolveNurseStationStatusEnumIn(query);
        if (statusIn == null || statusIn.isEmpty()) {
            return false;
        }
        for (String code : statusIn) {
            if (EncounterStatus.DISCHARGED.getCode().equals(code)) {
                return true;
            }
        }
        return false;
    }

    private void applyNurseStationGeographicScope(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        InpatientEncounterPageQuery query,
        EncounterSessionScope scope) {
        // 已出院结算/已清床召回列表：DISCHARGED 行无活动位置，按末次已完成 WARD 或活动床口径并集收窄。
        if (this.nurseStationNeedsRecallGeographicScope(query)) {
            this.applyNurseStationRecallGeographicScope(
                predicates, root, cb, criteriaQuery, query, scope);
            return;
        }
        predicates.add(this.existsAnyActiveWardOrBed(root, cb, criteriaQuery));
        if (query.getHouseId() != null) {
            List<Location> beds = locationQueryService.listBedsUnderParent(query.getHouseId());
            List<Long> bedIds = new ArrayList<>();
            for (Location bed : beds) {
                if (bed != null && bed.getId() != null) {
                    bedIds.add(bed.getId());
                }
            }
            if (bedIds.isEmpty()) {
                predicates.add(cb.disjunction());
                return;
            }
            predicates.add(this.existsActiveBedIn(root, cb, criteriaQuery, bedIds));
            return;
        }
        if (query.getWardId() != null) {
            predicates.add(this.buildWardScopePredicate(root, cb, criteriaQuery, query.getWardId()));
            return;
        }
        List<Long> managedWardIds = query.getManagedWardIds();
        if (managedWardIds == null || managedWardIds.isEmpty()) {
            managedWardIds = scope.managedWardLocationIds();
        }
        if (managedWardIds != null && !managedWardIds.isEmpty()) {
            List<Long> wardIds = new ArrayList<>();
            for (Long wardId : managedWardIds) {
                if (wardId != null) {
                    wardIds.add(wardId);
                }
            }
            if (!wardIds.isEmpty()) {
                predicates.add(this.existsActiveWardIn(root, cb, criteriaQuery, wardIds));
            } else {
                predicates.add(cb.disjunction());
            }
            return;
        }
        predicates.add(cb.disjunction());
    }

    private void applyEncounterIdsIn(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        Collection<Long> encounterIdsIn) {
        if (encounterIdsIn == null) {
            return;
        }
        predicates.add(root.get(FieldNameConstants.ID).in(encounterIdsIn));
    }

    private void applyKeyword(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        InpatientEncounterPageQuery query) {
        if (StringUtils.isBlank(query.getKeyword())) {
            return;
        }
        String pattern = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
        Predicate busNoLike = cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), pattern);
        Predicate nameLike = this.existsPatientNameLike(root, cb, criteriaQuery, pattern);
        Predicate bedLike = this.existsActiveBedNameLike(root, cb, criteriaQuery, pattern);
        predicates.add(cb.or(busNoLike, nameLike, bedLike));
    }

    private Predicate existsPatientNameLike(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        String pattern) {
        Subquery<Long> sq = criteriaQuery.subquery(Long.class);
        Root<Patient> patientRoot = sq.from(Patient.class);
        sq.select(patientRoot.get(FieldNameConstants.ID));
        sq.where(cb.and(
            cb.equal(patientRoot.get(FieldNameConstants.ID), root.get(FieldNameConstants.PATIENT_ID)),
            cb.isFalse(patientRoot.get(FieldNameConstants.IS_DELETED)),
            cb.like(cb.lower(patientRoot.get(FieldNameConstants.NAME)), pattern)));
        return cb.exists(sq);
    }

    private Predicate existsActiveBedNameLike(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        String pattern) {
        Subquery<Long> sq = criteriaQuery.subquery(Long.class);
        Root<EncounterLocation> elRoot = sq.from(EncounterLocation.class);
        Root<Location> locRoot = sq.from(Location.class);
        sq.select(elRoot.get(FieldNameConstants.ENCOUNTER_ID));
        sq.where(cb.and(
            cb.equal(elRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.equal(elRoot.get(FieldNameConstants.STATUS_ENUM), EncounterActivityStatus.ACTIVE.getCode()),
            cb.equal(elRoot.get(FieldNameConstants.FORM_ENUM), LocationForm.BED.getCode()),
            cb.equal(elRoot.get(FieldNameConstants.LOCATION_ID), locRoot.get(FieldNameConstants.ID)),
            cb.isFalse(locRoot.get(FieldNameConstants.IS_DELETED)),
            cb.like(cb.lower(locRoot.get(FieldNameConstants.NAME)), pattern)));
        return cb.exists(sq);
    }

    private void applySettleQueueFilter(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        InpatientEncounterPageQuery query) {
        String tab = null;
        if (query != null) {
            tab = query.getSettleQueueTab();
        }
        if (InpatientSettleQueueTab.SETTLED.equals(tab)) {
            // 已结算页签：仅展示 COMPLETED（财务结算完成、清床前）。
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.COMPLETED.getCode()));
            return;
        }
        // 有待结算页签：排除作废/计划态；可见 = 有待结算费用 或 护士已办出院待零元/全结（PENDING）。
        predicates.add(cb.notEqual(root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.CANCELLED.getCode()));
        predicates.add(cb.notEqual(root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.PLANNED.getCode()));
        // 有待结算费用，或护士已办理出院（PENDING）待零元/全结——后者可无 PENDING_SETTLEMENT 行。
        predicates.add(cb.or(
            this.existsPendingSettlementChargeForEncounter(root, cb, criteriaQuery),
            cb.equal(root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.PENDING.getCode())));
    }

    /**
     * 就诊下是否存在待结算收费行（结算队列「有待结算」页签口径之一）。
     */
    private Predicate existsPendingSettlementChargeForEncounter(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery) {
        Subquery<Long> sq = criteriaQuery.subquery(Long.class);
        Root<ChargeItem> chargeRoot = sq.from(ChargeItem.class);
        sq.select(chargeRoot.get(FieldNameConstants.ENCOUNTER_ID));
        sq.where(cb.and(
            cb.equal(chargeRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.equal(chargeRoot.get(FieldNameConstants.STATUS_ENUM), ChargeItemStatus.PENDING_SETTLEMENT.getCode()),
            cb.isFalse(chargeRoot.get(FieldNameConstants.IS_DELETED))));
        return cb.exists(sq);
    }

    private void applyPrepayEligibleStatus(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb) {
        predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(
            EncounterStatus.REGISTERED.getCode(),
            EncounterStatus.IN_PROGRESS.getCode(),
            EncounterStatus.ON_HOLD.getCode(),
            EncounterStatus.PENDING.getCode(),
            EncounterStatus.COMPLETED.getCode()));
    }

    private void applyAdmissionDate(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        InpatientEncounterPageQuery query) {
        LocalDate admissionDate = query.getAdmissionDate();
        if (admissionDate == null) {
            return;
        }
        LocalDateTime dayStart = admissionDate.atStartOfDay();
        LocalDateTime dayEnd = admissionDate.plusDays(1).atStartOfDay();
        predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.START_TIME), dayStart));
        predicates.add(cb.lessThan(root.get(FieldNameConstants.START_TIME), dayEnd));
    }

    private void applyRegistrationListStatus(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        InpatientEncounterPageQuery query) {
        List<String> listStatuses = query.getRegistrationListStatuses();
        if (listStatuses == null || listStatuses.isEmpty()) {
            if (StringUtils.isNotBlank(query.getRegistrationListStatus())) {
                listStatuses = List.of(query.getRegistrationListStatus().trim());
            }
        }
        if (listStatuses == null || listStatuses.isEmpty()) {
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(
                EncounterStatus.PLANNED.getCode(),
                EncounterStatus.REGISTERED.getCode(),
                EncounterStatus.IN_PROGRESS.getCode()));
            return;
        }
        LinkedHashSet<String> encounterStatuses = new LinkedHashSet<>();
        for (String listStatus : listStatuses) {
            if (StringUtils.isBlank(listStatus)) {
                continue;
            }
            String encounterStatus = InpatientRegistrationConstants.toEncounterStatusCode(listStatus);
            if (encounterStatus == null) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    "registrationListStatus 无效: " + listStatus);
            }
            encounterStatuses.add(encounterStatus);
        }
        if (encounterStatuses.isEmpty()) {
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(
                EncounterStatus.PLANNED.getCode(),
                EncounterStatus.REGISTERED.getCode(),
                EncounterStatus.IN_PROGRESS.getCode()));
            return;
        }
        predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(encounterStatuses));
    }

    private void applyWardScope(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        Long wardId) {
        predicates.add(this.buildWardScopePredicate(root, cb, criteriaQuery, wardId));
    }

    private Predicate buildWardScopePredicate(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        Long wardId) {
        List<Long> bedIds = this.collectBedIdsUnderWard(wardId);
        Predicate activeWard = this.existsActiveWardLocation(root, cb, criteriaQuery, wardId);
        if (bedIds.isEmpty()) {
            return activeWard;
        }
        return cb.or(activeWard, this.existsActiveBedIn(root, cb, criteriaQuery, bedIds));
    }

    private Predicate existsActiveWardLocation(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        Long wardId) {
        Subquery<Long> sq = criteriaQuery.subquery(Long.class);
        Root<EncounterLocation> elRoot = sq.from(EncounterLocation.class);
        sq.select(elRoot.get(FieldNameConstants.ENCOUNTER_ID));
        sq.where(cb.and(
            cb.equal(elRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.equal(elRoot.get(FieldNameConstants.STATUS_ENUM), EncounterActivityStatus.ACTIVE.getCode()),
            cb.equal(elRoot.get(FieldNameConstants.FORM_ENUM), LocationForm.WARD.getCode()),
            cb.equal(elRoot.get(FieldNameConstants.LOCATION_ID), wardId)));
        return cb.exists(sq);
    }

    private Predicate existsAnyActiveWardLocation(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery) {
        Subquery<Long> sq = criteriaQuery.subquery(Long.class);
        Root<EncounterLocation> elRoot = sq.from(EncounterLocation.class);
        sq.select(elRoot.get(FieldNameConstants.ENCOUNTER_ID));
        sq.where(cb.and(
            cb.equal(elRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.equal(elRoot.get(FieldNameConstants.STATUS_ENUM), EncounterActivityStatus.ACTIVE.getCode()),
            cb.equal(elRoot.get(FieldNameConstants.FORM_ENUM), LocationForm.WARD.getCode())));
        return cb.exists(sq);
    }

    private Predicate existsAnyActiveWardOrBed(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery) {
        return cb.or(
            this.existsAnyActiveWardLocation(root, cb, criteriaQuery),
            this.existsActiveBed(root, cb, criteriaQuery));
    }

    private Predicate existsActiveBed(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery) {
        Subquery<Long> sq = criteriaQuery.subquery(Long.class);
        Root<EncounterLocation> elRoot = sq.from(EncounterLocation.class);
        sq.select(elRoot.get(FieldNameConstants.ENCOUNTER_ID));
        sq.where(cb.and(
            cb.equal(elRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.equal(elRoot.get(FieldNameConstants.STATUS_ENUM), EncounterActivityStatus.ACTIVE.getCode()),
            cb.equal(elRoot.get(FieldNameConstants.FORM_ENUM), LocationForm.BED.getCode())));
        return cb.exists(sq);
    }

    private Predicate existsActiveBedIn(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        Collection<Long> bedIds) {
        Subquery<Long> sq = criteriaQuery.subquery(Long.class);
        Root<EncounterLocation> elRoot = sq.from(EncounterLocation.class);
        sq.select(elRoot.get(FieldNameConstants.ENCOUNTER_ID));
        sq.where(cb.and(
            cb.equal(elRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.equal(elRoot.get(FieldNameConstants.STATUS_ENUM), EncounterActivityStatus.ACTIVE.getCode()),
            cb.equal(elRoot.get(FieldNameConstants.FORM_ENUM), LocationForm.BED.getCode()),
            elRoot.get(FieldNameConstants.LOCATION_ID).in(bedIds)));
        return cb.exists(sq);
    }

    private Predicate existsActiveWardIn(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        Collection<Long> wardIds) {
        List<Long> allBedIds = new ArrayList<>();
        for (Long wardId : wardIds) {
            allBedIds.addAll(this.collectBedIdsUnderWard(wardId));
        }
        List<Predicate> parts = new ArrayList<>();
        for (Long wardId : wardIds) {
            parts.add(this.existsActiveWardLocation(root, cb, criteriaQuery, wardId));
        }
        if (!allBedIds.isEmpty()) {
            parts.add(this.existsActiveBedIn(root, cb, criteriaQuery, allBedIds));
        }
        return cb.or(parts.toArray(new Predicate[0]));
    }

    /**
     * 出院召回列表地理收窄：{@code COMPLETED} 仍按活动床/病区；{@code DISCHARGED} 按末次已完成 {@code WARD}。
     */
    private void applyNurseStationRecallGeographicScope(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        InpatientEncounterPageQuery query,
        EncounterSessionScope scope) {
        Collection<String> statusIn = this.resolveNurseStationStatusEnumIn(query);
        boolean includesCompleted = false;
        boolean includesDischarged = false;
        for (String code : statusIn) {
            if (EncounterStatus.COMPLETED.getCode().equals(code)) {
                includesCompleted = true;
            }
            if (EncounterStatus.DISCHARGED.getCode().equals(code)) {
                includesDischarged = true;
            }
        }
        List<Long> wardIds = this.resolveManagedWardIds(query, scope);
        List<Predicate> scopeParts = new ArrayList<>();
        if (includesCompleted) {
            scopeParts.add(this.buildNurseStationActiveGeographicPredicate(
                root, cb, criteriaQuery, query, wardIds));
        }
        if (includesDischarged) {
            List<Long> dischargedWardIds = query.getWardId() != null
                ? List.of(query.getWardId())
                : wardIds;
            if (dischargedWardIds == null || dischargedWardIds.isEmpty()) {
                predicates.add(cb.disjunction());
                return;
            }
            scopeParts.add(this.existsLastCompletedWardIn(root, cb, criteriaQuery, dischargedWardIds));
        }
        if (scopeParts.isEmpty()) {
            predicates.add(cb.disjunction());
            return;
        }
        predicates.add(cb.or(scopeParts.toArray(new Predicate[0])));
    }

    /**
     * 召回列表中 {@code COMPLETED} 行仍要求存在活动病区或床位（与默认在院 BR-002 一致）。
     */
    private Predicate buildNurseStationActiveGeographicPredicate(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        InpatientEncounterPageQuery query,
        List<Long> wardIds) {
        if (query.getHouseId() != null) {
            List<Location> beds = locationQueryService.listBedsUnderParent(query.getHouseId());
            List<Long> bedIds = new ArrayList<>();
            for (Location bed : beds) {
                if (bed != null && bed.getId() != null) {
                    bedIds.add(bed.getId());
                }
            }
            if (bedIds.isEmpty()) {
                return cb.disjunction();
            }
            return this.existsActiveBedIn(root, cb, criteriaQuery, bedIds);
        }
        if (query.getWardId() != null) {
            return this.buildWardScopePredicate(root, cb, criteriaQuery, query.getWardId());
        }
        if (wardIds != null && !wardIds.isEmpty()) {
            return this.existsActiveWardIn(root, cb, criteriaQuery, wardIds);
        }
        return cb.disjunction();
    }

    /**
     * 解析护士站管辖病区：请求显式传入优先，否则取会话解析结果。
     */
    private List<Long> resolveManagedWardIds(
        InpatientEncounterPageQuery query,
        EncounterSessionScope scope) {
        List<Long> managedWardIds = query.getManagedWardIds();
        if (managedWardIds == null || managedWardIds.isEmpty()) {
            managedWardIds = scope.managedWardLocationIds();
        }
        List<Long> wardIds = new ArrayList<>();
        if (managedWardIds == null) {
            return wardIds;
        }
        for (Long wardId : managedWardIds) {
            if (wardId != null) {
                wardIds.add(wardId);
            }
        }
        return wardIds;
    }

    /**
     * 末次已完成病区轨迹落在管辖病区集合内（清床后召回列表地理归属）。
     */
    private Predicate existsLastCompletedWardIn(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        Collection<Long> wardIds) {
        Subquery<LocalDateTime> maxEndSq = criteriaQuery.subquery(LocalDateTime.class);
        Root<EncounterLocation> maxRoot = maxEndSq.from(EncounterLocation.class);
        maxEndSq.select(cb.greatest(maxRoot.<LocalDateTime>get(FieldNameConstants.END_TIME)));
        maxEndSq.where(cb.and(
            cb.equal(maxRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.equal(maxRoot.get(FieldNameConstants.FORM_ENUM), LocationForm.WARD.getCode()),
            cb.equal(maxRoot.get(FieldNameConstants.STATUS_ENUM), EncounterActivityStatus.COMPLETED.getCode())));

        Subquery<Long> sq = criteriaQuery.subquery(Long.class);
        Root<EncounterLocation> elRoot = sq.from(EncounterLocation.class);
        sq.select(elRoot.get(FieldNameConstants.ENCOUNTER_ID));
        sq.where(cb.and(
            cb.equal(elRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.equal(elRoot.get(FieldNameConstants.FORM_ENUM), LocationForm.WARD.getCode()),
            cb.equal(elRoot.get(FieldNameConstants.STATUS_ENUM), EncounterActivityStatus.COMPLETED.getCode()),
            elRoot.get(FieldNameConstants.LOCATION_ID).in(wardIds),
            cb.equal(elRoot.get(FieldNameConstants.END_TIME), maxEndSq)));
        return cb.exists(sq);
    }

    private List<Long> collectBedIdsUnderWard(Long wardId) {
        List<Long> bedIds = new ArrayList<>();
        if (wardId == null) {
            return bedIds;
        }
        List<Location> rooms = locationQueryService.listRoomsUnderWard(wardId);
        for (Location room : rooms) {
            if (room == null || Boolean.TRUE.equals(room.getIsDeleted()) || room.getId() == null) {
                continue;
            }
            for (Location bed : locationQueryService.listBedsUnderParent(room.getId())) {
                if (bed != null && bed.getId() != null) {
                    bedIds.add(bed.getId());
                }
            }
        }
        return bedIds;
    }
}
