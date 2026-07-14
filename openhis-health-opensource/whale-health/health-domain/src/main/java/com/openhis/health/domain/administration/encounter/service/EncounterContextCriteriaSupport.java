/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.administration.encounter.service;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.entity.EncounterDiagnosis;
import com.openhis.health.domain.administration.encounter.entity.EncounterLocation;
import com.openhis.health.domain.administration.encounter.entity.EncounterParticipant;
import com.openhis.health.domain.administration.encounter.queries.ClinicRegisterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.EncounterSessionScope;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterLocationScope;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.OutpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.RegistrationSlipPageQuery;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.InpatientNurseStationConstants;
import com.openhis.health.domain.share.constants.InpatientRegistrationConstants;
import com.openhis.health.domain.share.constants.InpatientSettleQueueTab;
import com.openhis.health.domain.share.constants.RegistrationSlipConstants;
import com.openhis.health.domain.financial.paymentreconciliation.entity.PaymentReconciliation;
import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.enums.PaymentStatus;
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
import com.openhis.whale.core.application.util.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 就诊上下文分页/列表 Criteria 组装（health-domain；querysupport 编排后调用）。
 *
 * <p>仅产出 {@link Specification}，不注入 {@code *QueryService} / {@code *Repository}。
 * 跨聚合地理收窄由 {@link InpatientEncounterLocationScope} 预传入（B.6.4）。</p>
 */
@Component
public class EncounterContextCriteriaSupport {

    /**
     * 门诊挂号列表筛选规约（AMB、软删、关键字/状态/时段）。
     *
     * @param query 挂号筛选；{@code null} 时仅保留类别与未删谓词
     * @return JPA 规约
     */
    public Specification<Encounter> clinicRegisterSpecification(ClinicRegisterPageQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.AMB.getCode()));
            if (query != null) {
                if (StringUtils.isNotEmpty(query.getKeyword())) {
                    String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
                    predicates.add(this.existsDiagnosisDescriptionLike(root, cb, criteriaQuery, likeIc));
                }
                if (StringUtils.isNotEmpty(query.getStatusEnum())) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum().trim()));
                }
                if (query.getExcludeStatusEnums() != null && !query.getExcludeStatusEnums().isEmpty()) {
                    List<Predicate> excludePredicates = new ArrayList<>();
                    for (String excluded : query.getExcludeStatusEnums()) {
                        if (StringUtils.isNotEmpty(excluded)) {
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

    /**
     * 挂号单分页筛选规约（按就诊主键/患者/状态/时段）。
     *
     * @param query 挂号单筛选；{@code null} 时仅保留类别与未删谓词
     * @return JPA 规约
     */
    public Specification<Encounter> registrationSlipSpecification(RegistrationSlipPageQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.AMB.getCode()));
            if (query != null) {
                Long encounterId = query.getEncounterId();
                if (encounterId == null && StringUtils.isNotEmpty(query.getSlipNo())) {
                    // 仅传 slipNo 时从后缀解析 encounterId
                    encounterId = this.parseEncounterIdFromSlipNo(query.getSlipNo().trim());
                }
                if (encounterId != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.ID), encounterId));
                }
                if (query.getPatientId() != null) {
                    predicates.add(cb.equal(root.get(FieldNameConstants.PATIENT_ID), query.getPatientId()));
                }
                if (StringUtils.isNotEmpty(query.getStatusEnum())) {
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

    /** 从挂号单号后缀解析就诊主键（前缀不匹配或非数字时返回 null）。 */
    private Long parseEncounterIdFromSlipNo(String slipNo) {
        if (StringUtils.isEmpty(slipNo)) {
            return null;
        }
        String prefix = RegistrationSlipConstants.SLIP_NO_PREFIX;
        if (!slipNo.startsWith(prefix)) {
            return null;
        }
        String suffix = slipNo.substring(prefix.length());
        if (StringUtils.isEmpty(suffix.trim())) {
            return null;
        }
        try {
            return Long.parseLong(suffix.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /** 就诊诊断描述模糊匹配（挂号关键字搜诊断用 exists 子查询）。 */
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

    /**
     * 门诊就诊上下文筛选规约（机构会话收窄、医生站可见性、关键字）。
     *
     * @param query 门诊筛选
     * @param scope 会话机构收窄；{@code organizationScopeEnforcementActive} 且无机构时结果为 empty
     * @return JPA 规约
     */
    public Specification<Encounter> outpatientSpecification(
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
                // 会话要求机构收窄但未解析到机构：结果集为空
                predicates.add(cb.disjunction());
            }
            if (query != null) {
                if (query.getStatusEnumIn() != null && !query.getStatusEnumIn().isEmpty()) {
                    predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(query.getStatusEnumIn()));
                } else if (StringUtils.isNotEmpty(query.getStatusEnum())) {
                    predicates.add(cb.equal(
                        root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum().trim()));
                }
                if (query.getDoctorStationViewerPractitionerId() != null) {
                    // 医生站列表：按查看者身份与状态组合可见性收窄
                    predicates.add(this.buildDoctorStationViewerVisibilityPredicate(
                        root, cb, criteriaQuery, query));
                } else if (query.getDoctorId() != null) {
                    predicates.add(this.existsActiveDoctorParticipant(
                        root, cb, criteriaQuery, query.getDoctorId()));
                }
                if (StringUtils.isNotEmpty(query.getKeyword())) {
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

    /**
     * 门诊护士站执行队列筛选规约（待执行医嘱/申请存在性）。
     *
     * @param query 分页与关键字/日期窗；{@code null} 时使用空条件
     * @return JPA 规约
     */
    public Specification<Encounter> nurseStationOutpatientSpecification(AbstractPageQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(this.outpatientClassEnumPredicate(root, cb));
            if (StringUtils.isNotEmpty(query.getKeyword())) {
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

    /** 门诊类别谓词：仅 AMB。 */
    private Predicate outpatientClassEnumPredicate(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb) {
        return cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.AMB.getCode());
    }

    /**
     * 医生站查看者可见性：按状态分派（已诊出/候诊/接诊中）与参与者角色组合 OR。
     */
    private Predicate buildDoctorStationViewerVisibilityPredicate(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        OutpatientEncounterPageQuery query) {
        Long viewerPractitionerId = query.getDoctorStationViewerPractitionerId();
        List<String> statusScope = this.resolveDoctorStationStatusScope(query);
        List<Predicate> visibilityOr = new ArrayList<>();
        if (this.statusScopeIncludes(statusScope, EncounterStatus.DISCHARGED.getCode())) {
            // 已诊出：全员可见（不按参与者收窄）
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
            // 候诊/分诊：未分诊或分诊医生为当前查看者（含历史 ADMITTER 角色兼容）
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
            // 接诊中/暂挂：仅接诊医生（ADMITTER）为查看者时可见
            visibilityOr.add(cb.and(inConsultStatusIn, admitterIsViewer));
        }
        if (visibilityOr.isEmpty()) {
            // 状态范围与可见性规则无交集：空结果
            return cb.disjunction();
        }
        if (visibilityOr.size() == 1) {
            return visibilityOr.get(0);
        }
        Predicate[] branches = visibilityOr.toArray(new Predicate[0]);
        return cb.or(branches);
    }

    /** 医生站状态范围：优先 {@code statusEnumIn}，否则单值 {@code statusEnum}。 */
    private List<String> resolveDoctorStationStatusScope(OutpatientEncounterPageQuery query) {
        if (query.getStatusEnumIn() != null && !query.getStatusEnumIn().isEmpty()) {
            return query.getStatusEnumIn();
        }
        if (StringUtils.isNotEmpty(query.getStatusEnum())) {
            return List.of(query.getStatusEnum().trim());
        }
        return List.of();
    }

    /** 状态范围是否包含指定就诊状态 code。 */
    private boolean statusScopeIncludes(List<String> statusScope, String statusCode) {
        for (String scoped : statusScope) {
            if (statusCode.equals(scoped)) {
                return true;
            }
        }
        return false;
    }

    /** 从状态范围中筛出允许的状态子集（医生站可见性分支用）。 */
    private List<String> filterStatusScope(List<String> statusScope, String... allowedCodes) {
        List<String> filtered = new ArrayList<>();
        for (String allowed : allowedCodes) {
            if (this.statusScopeIncludes(statusScope, allowed)) {
                filtered.add(allowed);
            }
        }
        return filtered;
    }

    /** 存在指定角色且 practitioner 为给定值的活跃参与者。 */
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

    /** 不存在指定角色的活跃参与者（候诊未分诊判定）。 */
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

    /** 存在活跃接诊/挂号/主治医生参与者（按 doctorId 筛选用）。 */
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

    /** 患者姓名/拼音/五笔模糊匹配（关键字子查询）。 */
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

    /**
     * 门诊护士站队列：就诊下存在待执行 completed 申请（服务/设备/注射或皮试药品）或退费中收费行。
     */
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

    /** 就诊下存在退费中/已退费收费行（护士站执行队列口径之一）。 */
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

    /**
     * 门诊列表排序：医生站队列按接诊时间，其余按创建时间。
     *
     * @param query 门诊筛选（状态决定排序字段）
     * @return Spring Data 排序
     */
    public Sort resolveOutpatientListSort(OutpatientEncounterPageQuery query) {
        if (query == null) {
            return Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT));
        }
        if (query.getStatusEnumIn() != null && query.getStatusEnumIn().size() > 1) {
            // 多状态并集：按 startTime 倒序
            return Sort.by(
                Sort.Order.desc(FieldNameConstants.START_TIME),
                Sort.Order.desc(FieldNameConstants.ID));
        }
        if (query.getStatusEnumIn() != null && query.getStatusEnumIn().size() == 1) {
            String onlyStatus = query.getStatusEnumIn().get(0);
            if (EncounterStatus.IN_PROGRESS.getCode().equals(onlyStatus)) {
                // 仅接诊中：按 receptionTime
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

    /**
     * 住院列表分页参数（含医生站/护士站/结算队列排序口径）。
     *
     * @param query 住院筛选；{@code null} 时使用默认页码与页大小
     * @return 分页与排序
     */
    public Pageable resolveInpatientPageable(InpatientEncounterPageQuery query) {
        int pageNo = 0;
        int pageSize = 20;
        if (query != null) {
            pageNo = query.springPageIndex();
            pageSize = query.getBoundedPageSize();
        }
        return PageRequest.of(pageNo, pageSize, this.resolveInpatientSort(query));
    }

    /** 住院列表排序：医生站按 busNo，队列类按 startTime，默认 createdAt。 */
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

    /**
     * 住院就诊上下文筛选规约（医生站/护士站/结算队列/登记列表等多场景）。
     *
     * @param query          住院筛选
     * @param scope          会话机构/管辖病区收窄
     * @param locationScope  预解析床位主键（B.6.4）；{@code null} 视为 empty
     * @return JPA 规约
     */
    public Specification<Encounter> inpatientSpecification(
        InpatientEncounterPageQuery query,
        EncounterSessionScope scope,
        InpatientEncounterLocationScope locationScope) {
        InpatientEncounterLocationScope effectiveLocationScope;
        if (locationScope != null) {
            effectiveLocationScope = locationScope;
        } else {
            effectiveLocationScope = InpatientEncounterLocationScope.empty();
        }
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.IMP.getCode()));
            if (query == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }
            if (Boolean.TRUE.equals(query.getDoctorStationQueue())) {
                // 住院医生站（Q-IPI-006 / BR-IP-DOR-010）：IMP 在科态 + 会话科室等值；
                // 不按主治/转科参与者收窄（禁止复用门诊 buildDoctorStationViewerVisibilityPredicate）。
                this.applyDoctorStationVisibleStatus(predicates, root, cb);
                this.applyInpatientDoctorStationOrganizationScope(predicates, root, cb, scope);
            } else if (Boolean.TRUE.equals(query.getNurseStationQueue())) {
                // 护士站：状态 + 地理（床位/病区，含召回口径）+ 可选 encounterIdsIn
                this.applyNurseStationStatusScope(predicates, root, cb, query);
                this.applyNurseStationGeographicScope(
                    predicates, root, cb, criteriaQuery, query, scope, effectiveLocationScope);
                this.applyEncounterIdsIn(predicates, root, cb, query.getEncounterIdsIn());
            } else if (Boolean.TRUE.equals(query.getSettleQueue())) {
                // 住院结算队列：按页签区分已结算 vs 有待结算
                this.applySettleQueueFilter(predicates, root, cb, criteriaQuery, query);
            } else {
                if (query.getWardId() != null) {
                    this.applyWardScope(predicates, root, cb, criteriaQuery, query.getWardId(), effectiveLocationScope);
                }
                if (Boolean.TRUE.equals(query.getPrepayEligibleOnly())) {
                    this.applyPrepayEligibleStatus(predicates, root, cb);
                } else {
                    // 登记/入院列表默认状态并集
                    this.applyRegistrationListStatus(predicates, root, cb, query);
                }
            }
            if (query.getPatientId() != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.PATIENT_ID), query.getPatientId()));
            }
            if (StringUtils.isNotEmpty(query.getStatusEnum())) {
                predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum()));
            }
            this.applyAdmissionDate(predicates, root, cb, query);
            this.applyKeyword(predicates, root, cb, criteriaQuery, query);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 住院医生站可见状态（**BR-IP-DOR-002**）：已入科 + 待转科/待出院过渡态。
     *
     * @param predicates 谓词累加器
     * @param root       就诊根
     * @param cb         CriteriaBuilder
     */
    private void applyDoctorStationVisibleStatus(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb) {
        // 离院（DISCHARGED）前医生站均可查看：含待结算/已结算待清床；写 Gate 另拦 PENDING/COMPLETED/DISCHARGED
        predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(
            EncounterStatus.IN_PROGRESS.getCode(),
            EncounterStatus.ON_HOLD.getCode(),
            EncounterStatus.PENDING.getCode(),
            EncounterStatus.COMPLETED.getCode()));
    }

    /**
     * 住院医生站科室收窄（**BR-IP-DOR-003 / BR-IP-DOR-010**）。
     *
     * <p>当前会话 {@code organization_id} 等值即全科可见；<strong>不</strong>按活动
     * {@code ATTENDING_DOCTOR}/{@code ADMITTER} 或转科里程碑操作者收窄（与门诊 **BR-DOR-009** 不同）。</p>
     *
     * @param predicates 谓词累加器
     * @param root       就诊根
     * @param cb         CriteriaBuilder
     * @param scope      会话机构收窄（出诊管辖科室经 App Loader 解析后写入）
     */
    private void applyInpatientDoctorStationOrganizationScope(
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
            // 强制机构 scope 但未解析到机构：空结果
            predicates.add(cb.disjunction());
        }
    }

    /** 护士站默认在院状态并集（无显式 status 筛选时）。 */
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
        if (StringUtils.isEmpty(filter)) {
            return Collections.emptyList();
        }
        if (InpatientNurseStationConstants.STATUS_FILTER_SETTLED_DISCHARGED.equals(filter.trim())) {
            // 召回列表：COMPLETED + DISCHARGED 并集（AS-IP-NUR-001）
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

    /** 护士站地理收窄：召回列表走末次 WARD，否则活动床/病区 + 预解析床位。 */
    private void applyNurseStationGeographicScope(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        InpatientEncounterPageQuery query,
        EncounterSessionScope scope,
        InpatientEncounterLocationScope locationScope) {
        // 已出院结算/已清床召回列表：DISCHARGED 行无活动位置，按末次已完成 WARD 或活动床口径并集收窄。
        if (this.nurseStationNeedsRecallGeographicScope(query)) {
            this.applyNurseStationRecallGeographicScope(
                predicates, root, cb, criteriaQuery, query, scope, locationScope);
            return;
        }
        predicates.add(this.existsAnyActiveWardOrBed(root, cb, criteriaQuery));
        if (query.getHouseId() != null) {
            List<Long> bedIds = locationScope.houseBedIds();
            if (bedIds.isEmpty()) {
                // 病房下无床位：不可能命中
                predicates.add(cb.disjunction());
                return;
            }
            predicates.add(this.existsActiveBedIn(root, cb, criteriaQuery, bedIds));
            return;
        }
        if (query.getWardId() != null) {
            predicates.add(this.buildWardScopePredicate(root, cb, criteriaQuery, query.getWardId(), locationScope));
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
                predicates.add(this.existsActiveWardIn(root, cb, criteriaQuery, wardIds, locationScope));
            } else {
                predicates.add(cb.disjunction());
            }
            return;
        }
        // 护士站未指定地理范围且无管辖病区：空结果
        predicates.add(cb.disjunction());
    }

    /** 可选就诊主键白名单（护士站批量收窄）。 */
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

    /** 住院关键字：busNo / 患者姓名 / 活动床位名 OR。 */
    private void applyKeyword(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        InpatientEncounterPageQuery query) {
        if (StringUtils.isEmpty(query.getKeyword())) {
            return;
        }
        String pattern = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
        Predicate busNoLike = cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), pattern);
        Predicate nameLike = this.existsPatientNameLike(root, cb, criteriaQuery, pattern);
        Predicate bedLike = this.existsActiveBedNameLike(root, cb, criteriaQuery, pattern);
        predicates.add(cb.or(busNoLike, nameLike, bedLike));
    }

    /** 患者姓名模糊 exists 子查询。 */
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

    /** 活动床位名称模糊 exists（EncounterLocation + Location 联表）。 */
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

    /** 住院结算队列页签筛选（已结算 vs 有待结算/PENDING 出院）。 */
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
            // 已结算页签：存在住院临床结算流水（SUCCESS/REFUNDED）即可查阅，与在院/床位/召回后 REGISTERED 无关。
            predicates.add(cb.notEqual(root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.CANCELLED.getCode()));
            predicates.add(cb.notEqual(root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.PLANNED.getCode()));
            predicates.add(this.existsInpatientClinicSettlementHistoryForEncounter(root, cb, criteriaQuery));
            return;
        }
        // 有待结算页签：仅护士已办理出院（PENDING），与 IF-IP-STL-06 结算门禁一致（BR-IP-STL-004）。
        // 召回后 REGISTERED 虽有 PENDING_SETTLEMENT 费用，亦不得入队，须二次分床→校对→办理出院后再现。
        predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.PENDING.getCode()));
    }

    /**
     * 就诊下是否存在住院临床结算历史（IF-IP-STL-07 只读口径：INPATIENT_CLINIC + SUCCESS/REFUNDED）。
     */
    private Predicate existsInpatientClinicSettlementHistoryForEncounter(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery) {
        Subquery<Long> sq = criteriaQuery.subquery(Long.class);
        Root<PaymentReconciliation> paymentRoot = sq.from(PaymentReconciliation.class);
        sq.select(paymentRoot.get(FieldNameConstants.ENCOUNTER_ID));
        sq.where(cb.and(
            cb.equal(paymentRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.equal(paymentRoot.get(FieldNameConstants.KIND_ENUM), PaymentKind.INPATIENT_CLINIC),
            paymentRoot.get(FieldNameConstants.STATUS_ENUM).in(
                PaymentStatus.SUCCESS,
                PaymentStatus.REFUNDED),
            cb.isFalse(paymentRoot.get(FieldNameConstants.IS_DELETED))));
        return cb.exists(sq);
    }

    /** 预交金 eligible 状态并集（登记后至 COMPLETED 前各态）。 */
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

    /** 入院日期当天 [startOfDay, nextDay) 收窄 startTime。 */
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

    /**
     * 入院/登记列表状态：UI listStatus 映射为 encounter status；缺省为 PLANNED+REGISTERED+IN_PROGRESS。
     */
    private void applyRegistrationListStatus(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        InpatientEncounterPageQuery query) {
        List<String> listStatuses = query.getRegistrationListStatuses();
        if (listStatuses == null || listStatuses.isEmpty()) {
            if (StringUtils.isNotEmpty(query.getRegistrationListStatus())) {
                listStatuses = List.of(query.getRegistrationListStatus().trim());
            }
        }
        if (listStatuses == null || listStatuses.isEmpty()) {
            // 未传登记列表状态：默认三态并集
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(
                EncounterStatus.PLANNED.getCode(),
                EncounterStatus.REGISTERED.getCode(),
                EncounterStatus.IN_PROGRESS.getCode()));
            return;
        }
        LinkedHashSet<String> encounterStatuses = new LinkedHashSet<>();
        for (String listStatus : listStatuses) {
            if (StringUtils.isEmpty(listStatus)) {
                continue;
            }
            String encounterStatus = InpatientRegistrationConstants.toEncounterStatusCode(listStatus);
            if (encounterStatus == null) {
                // 登记 UI 状态无法映射：参数非法
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
                    "registrationListStatus 无效: " + listStatus);
            }
            encounterStatuses.add(encounterStatus);
        }
        if (encounterStatuses.isEmpty()) {
            // 映射后无有效状态：回落默认三态
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(
                EncounterStatus.PLANNED.getCode(),
                EncounterStatus.REGISTERED.getCode(),
                EncounterStatus.IN_PROGRESS.getCode()));
            return;
        }
        predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(encounterStatuses));
    }

    /** 登记/列表按病区收窄（委托 wardScope 谓词）。 */
    private void applyWardScope(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        Long wardId,
        InpatientEncounterLocationScope locationScope) {
        predicates.add(this.buildWardScopePredicate(root, cb, criteriaQuery, wardId, locationScope));
    }

    /** 病区 scope：活动 WARD 轨迹或下属活动床位（床位 id 来自预解析 scope）。 */
    private Predicate buildWardScopePredicate(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        Long wardId,
        InpatientEncounterLocationScope locationScope) {
        List<Long> bedIds = locationScope.bedIdsForWard(wardId);
        Predicate activeWard = this.existsActiveWardLocation(root, cb, criteriaQuery, wardId);
        if (bedIds.isEmpty()) {
            // 病区下未展开到床位：仅按活动 WARD 轨迹
            return activeWard;
        }
        return cb.or(activeWard, this.existsActiveBedIn(root, cb, criteriaQuery, bedIds));
    }

    /** 存在指定 wardId 的活跃 WARD 型 EncounterLocation。 */
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

    /** 存在任意活跃 WARD 型 EncounterLocation（护士站默认在院地理前提）。 */
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

    /** 存在活跃 WARD 或 BED 轨迹（护士站地理并集前提）。 */
    private Predicate existsAnyActiveWardOrBed(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery) {
        return cb.or(
            this.existsAnyActiveWardLocation(root, cb, criteriaQuery),
            this.existsActiveBed(root, cb, criteriaQuery));
    }

    /** 存在任意活跃 BED 型 EncounterLocation。 */
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

    /** 活动床位 locationId 落在给定集合内。 */
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

    /** 活动 WARD 或下属活动床位落在给定 wardIds 集合（ward 下床位来自预解析 scope）。 */
    private Predicate existsActiveWardIn(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        Collection<Long> wardIds,
        InpatientEncounterLocationScope locationScope) {
        List<Long> allBedIds = locationScope.bedIdsForWards(wardIds);
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
        EncounterSessionScope scope,
        InpatientEncounterLocationScope locationScope) {
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
                root, cb, criteriaQuery, query, wardIds, locationScope));
        }
        if (includesDischarged) {
            List<Long> dischargedWardIds;
            if (query.getWardId() != null) {
                // 召回列表显式病区：仅按该病区末次 WARD 轨迹收窄
                dischargedWardIds = List.of(query.getWardId());
            } else {
                dischargedWardIds = wardIds;
            }
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
        List<Long> wardIds,
        InpatientEncounterLocationScope locationScope) {
        if (query.getHouseId() != null) {
            List<Long> bedIds = locationScope.houseBedIds();
            if (bedIds.isEmpty()) {
                return cb.disjunction();
            }
            return this.existsActiveBedIn(root, cb, criteriaQuery, bedIds);
        }
        if (query.getWardId() != null) {
            return this.buildWardScopePredicate(root, cb, criteriaQuery, query.getWardId(), locationScope);
        }
        if (wardIds != null && !wardIds.isEmpty()) {
            return this.existsActiveWardIn(root, cb, criteriaQuery, wardIds, locationScope);
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
}
