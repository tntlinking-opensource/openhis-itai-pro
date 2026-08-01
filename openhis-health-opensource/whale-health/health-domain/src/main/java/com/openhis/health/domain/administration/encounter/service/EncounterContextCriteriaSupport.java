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
import com.openhis.health.domain.share.utils.KeywordExactFirstOrderSupport;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.workflow.devicerequest.entity.DeviceRequest;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import com.openhis.whale.core.application.exception.BusinessException;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
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
     * <p>关键字语义与收费窗一致：就诊业务号 / 患者姓名（含拼音、五笔），禁止误用诊断描述。</p>
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
                    // 收费窗关键字：就诊号或患者姓名/助记；与 outpatientSpecification 同口径
                    String trimmed = query.getKeyword().trim();
                    String pattern = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                    Predicate busNoLike = cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), pattern);
                    predicates.add(cb.or(busNoLike, this.existsPatientKeyword(root, cb, criteriaQuery, pattern)));
                    // B.2.1：业务号或患者精确命中置顶；无 Sort 时由本 ORDER BY 兜底
                    this.applyEncounterKeywordExactFirstOrder(
                        criteriaQuery, cb, root, trimmed, true, true, false,
                        List.of(cb.desc(root.get(FieldNameConstants.ID))));
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
                    String trimmed = query.getKeyword().trim();
                    String pattern = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                    Predicate busNoLike = cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), pattern);
                    predicates.add(cb.or(busNoLike, this.existsPatientKeyword(root, cb, criteriaQuery, pattern)));
                    // B.2.1：业务号或患者姓名/助记精确置顶（次级排序由 Pageable 或分桶 Spec 提供）
                    this.applyEncounterKeywordExactFirstOrder(
                        criteriaQuery, cb, root, trimmed, true, true, false,
                        List.of(cb.desc(root.get(FieldNameConstants.ID))));
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
                String trimmed = query.getKeyword().trim();
                String pattern = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                predicates.add(this.existsPatientKeyword(root, cb, criteriaQuery, pattern));
                // B.2.1：护士站患者关键字精确置顶，次级开始时间倒序
                this.applyEncounterKeywordExactFirstOrder(
                    criteriaQuery, cb, root, trimmed, false, true, false,
                    List.of(
                        cb.desc(root.get(FieldNameConstants.START_TIME)),
                        cb.desc(root.get(FieldNameConstants.ID))));
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
     * 医生站查看者可见性（BR-DOR-009）：按状态分派，与是否存在活动 {@code REGISTRATION_DOCTOR} 组合 OR。
     *
     * <p>挂到科室（无活动挂号医生）时，待诊与接诊后均科室全员可见；挂到医生时，待诊仅挂号医生、
     * 接诊中/暂离仅接诊医生（ADMITTER）可见；诊毕始终科室共享。</p>
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
            // 候诊/分诊：未指定挂号医生则科室共享；已指定则仅挂号医生（含历史 ADMITTER 误写兼容）
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
            // 挂到科室：无 REGISTRATION_DOCTOR 时接诊后仍科室共享，不收窄到 ADMITTER
            Predicate noRegistrationDoctor = this.notExistsActiveParticipantWithRole(
                root, cb, criteriaQuery, EncounterParticipantRole.REGISTRATION_DOCTOR.getCode());
            Predicate admitterIsViewer = this.existsActiveParticipantWithRoleAndPractitioner(
                root, cb, criteriaQuery,
                EncounterParticipantRole.ADMITTER.getCode(),
                viewerPractitionerId);
            // 挂到医生：接诊中/暂离仅活动 ADMITTER 可见（与旧口径一致）
            Predicate inConsultVisible = cb.or(noRegistrationDoctor, admitterIsViewer);
            visibilityOr.add(cb.and(inConsultStatusIn, inConsultVisible));
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
     * 就诊关键字：SQL 层精确命中升序在前（B.2.1）。
     *
     * @param criteriaQuery      当前查询；count 跳过
     * @param cb                 CriteriaBuilder
     * @param root               Encounter 根
     * @param trimmedKeyword     已 trim 关键字
     * @param includeBusNo       是否对 bus_no 做精确
     * @param includePatient     是否对患者姓名/助记做精确（exists）
     * @param includeBed         是否对活动床位名做精确（exists）
     * @param secondaryOrders    次级排序
     */
    private void applyEncounterKeywordExactFirstOrder(
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        Root<Encounter> root,
        String trimmedKeyword,
        boolean includeBusNo,
        boolean includePatient,
        boolean includeBed,
        List<Order> secondaryOrders) {
        if (criteriaQuery == null || StringUtils.isEmpty(trimmedKeyword)) {
            return;
        }
        Class<?> resultType = criteriaQuery.getResultType();
        if (resultType == Long.class || resultType == long.class) {
            return;
        }
        Expression<Integer> rank = this.encounterKeywordExactRankExpression(
            cb, root, criteriaQuery, trimmedKeyword, includeBusNo, includePatient, includeBed);
        List<Order> orders = new ArrayList<>();
        orders.add(cb.asc(rank));
        if (secondaryOrders != null) {
            orders.addAll(secondaryOrders);
        }
        criteriaQuery.orderBy(orders);
    }

    /** 挂号诊断描述关键字：精确全等诊断描述置顶。 */
    private void applyDiagnosisKeywordExactFirstOrder(
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        Root<Encounter> root,
        String trimmedKeyword) {
        if (criteriaQuery == null || StringUtils.isEmpty(trimmedKeyword)) {
            return;
        }
        Class<?> resultType = criteriaQuery.getResultType();
        if (resultType == Long.class || resultType == long.class) {
            return;
        }
        String exactLower = SearchCodeUtil.toCaseInsensitiveExactValue(trimmedKeyword);
        Expression<Integer> rank = cb.<Integer>selectCase()
            .when(this.existsDiagnosisDescriptionExact(root, cb, criteriaQuery, exactLower),
                KeywordExactFirstOrderSupport.RANK_EXACT)
            .otherwise(KeywordExactFirstOrderSupport.RANK_FUZZY);
        criteriaQuery.orderBy(
            cb.asc(rank),
            cb.desc(root.get(FieldNameConstants.ID)));
    }

    /**
     * 就诊关键字精确命中排序表达式（0=精确，1=仅模糊）。
     */
    private Expression<Integer> encounterKeywordExactRankExpression(
        jakarta.persistence.criteria.CriteriaBuilder cb,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        String trimmedKeyword,
        boolean includeBusNo,
        boolean includePatient,
        boolean includeBed) {
        String exactLower = SearchCodeUtil.toCaseInsensitiveExactValue(trimmedKeyword);
        List<Predicate> exactOrs = new ArrayList<>();
        if (includeBusNo) {
            exactOrs.add(cb.equal(cb.lower(root.get(FieldNameConstants.BUS_NO)), exactLower));
        }
        if (includePatient) {
            exactOrs.add(this.existsPatientKeywordExact(root, cb, criteriaQuery, exactLower));
        }
        if (includeBed) {
            exactOrs.add(this.existsActiveBedNameExact(root, cb, criteriaQuery, exactLower));
        }
        if (exactOrs.isEmpty()) {
            return cb.literal(KeywordExactFirstOrderSupport.RANK_FUZZY);
        }
        return cb.<Integer>selectCase()
            .when(cb.or(exactOrs.toArray(new Predicate[0])), KeywordExactFirstOrderSupport.RANK_EXACT)
            .otherwise(KeywordExactFirstOrderSupport.RANK_FUZZY);
    }

    /** 患者姓名/拼音/五笔与关键字忽略大小写全等。 */
    private Predicate existsPatientKeywordExact(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        String exactLower) {
        Subquery<Long> sq = criteriaQuery.subquery(Long.class);
        Root<Patient> patientRoot = sq.from(Patient.class);
        sq.select(patientRoot.get(FieldNameConstants.ID));
        sq.where(cb.and(
            cb.equal(patientRoot.get(FieldNameConstants.ID), root.get(FieldNameConstants.PATIENT_ID)),
            cb.isFalse(patientRoot.get(FieldNameConstants.IS_DELETED)),
            cb.or(
                cb.equal(cb.lower(patientRoot.get(FieldNameConstants.NAME)), exactLower),
                cb.equal(cb.lower(patientRoot.get(FieldNameConstants.PY_STR)), exactLower),
                cb.equal(cb.lower(patientRoot.get(FieldNameConstants.WB_STR)), exactLower))));
        return cb.exists(sq);
    }

    /** 活动床位名称与关键字忽略大小写全等。 */
    private Predicate existsActiveBedNameExact(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        String exactLower) {
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
            cb.equal(cb.lower(locRoot.get(FieldNameConstants.NAME)), exactLower)));
        return cb.exists(sq);
    }

    /** 诊断描述与关键字忽略大小写全等。 */
    private Predicate existsDiagnosisDescriptionExact(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        String exactLower) {
        Subquery<Long> sq = criteriaQuery.subquery(Long.class);
        Root<EncounterDiagnosis> diagRoot = sq.from(EncounterDiagnosis.class);
        sq.select(cb.literal(1L));
        sq.where(cb.and(
            cb.equal(diagRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.isFalse(diagRoot.get(FieldNameConstants.IS_DELETED)),
            cb.equal(cb.lower(diagRoot.get(FieldNameConstants.DESCRIPTION)), exactLower)));
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
     * 门诊列表排序（BR-DOR-002）：待诊按开始时间升序；在诊/暂离按接诊时间降序。
     *
     * <p>多状态并集时本方法返回 {@link Sort#unsorted()}，实际 CASE 分桶排序由
     * {@link #doctorStationUnionOrderSpecification()} 挂到 Criteria（避免单一 Sort 字段无法同时满足两套规则）。</p>
     *
     * @param query 门诊筛选（状态决定排序字段）
     * @return Spring Data 排序；多状态并集为 unsorted
     */
    public Sort resolveOutpatientListSort(OutpatientEncounterPageQuery query) {
        if (query == null) {
            return Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT));
        }
        // 多状态并集：Pageable 不带 Sort，改由 doctorStationUnionOrderSpecification 写 CASE ORDER BY
        if (isMultiStatusOutpatientQuery(query)) {
            return Sort.unsorted();
        }
        if (query.getStatusEnumIn() != null && query.getStatusEnumIn().size() == 1) {
            return resolveOutpatientSortByStatusCode(query.getStatusEnumIn().get(0));
        }
        if (StringUtils.isNotBlank(query.getStatusEnum())) {
            return resolveOutpatientSortByStatusCode(query.getStatusEnum());
        }
        return Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT));
    }

    /**
     * 是否门诊医生站缺省/显式多状态并集查询（需 CASE 分桶排序）。
     *
     * @param query 门诊分页查询
     * @return true 表示 statusEnumIn 含多个状态码
     */
    public boolean isMultiStatusOutpatientQuery(OutpatientEncounterPageQuery query) {
        if (query == null) {
            return false;
        }
        if (query.getStatusEnumIn() == null) {
            return false;
        }
        return query.getStatusEnumIn().size() > 1;
    }

    /**
     * 医生站多状态并集 ORDER BY：待诊桶 startTime 升序，在诊/暂离桶 receptionTime 降序，其余桶 startTime 降序。
     *
     * <p>count 查询不挂 orderBy。前端按待诊/在诊 Tab 过滤后，同桶内相对序仍符合业务规则。</p>
     *
     * @return 仅附加排序的规约（谓词恒真）
     */
    public Specification<Encounter> doctorStationUnionOrderSpecification() {
        return this.doctorStationUnionOrderSpecification(null);
    }

    /**
     * 医生站多状态并集 ORDER BY；关键字非空时精确命中升序在前，再接状态分桶。
     *
     * @param trimmedKeyword 已 trim 关键字；空白则仅分桶排序
     * @return 仅附加排序的规约（谓词恒真）
     */
    public Specification<Encounter> doctorStationUnionOrderSpecification(String trimmedKeyword) {
        return (root, query, cb) -> {
            // count 查询禁止挂 ORDER BY，避免部分库在 count 上拒绝对表达式排序
            if (query == null) {
                return cb.conjunction();
            }
            if (Long.class.equals(query.getResultType()) || long.class.equals(query.getResultType())) {
                return cb.conjunction();
            }

            List<String> waitingCodes = List.of(
                EncounterStatus.PLANNED.getCode(),
                EncounterStatus.REGISTERED.getCode(),
                EncounterStatus.TRIAGED.getCode());
            List<String> inProgressCodes = List.of(
                EncounterStatus.IN_PROGRESS.getCode(),
                EncounterStatus.ON_HOLD.getCode());

            // 桶序：待诊 → 在诊/暂离 → 诊毕等，保证「全部」Tab 可读，且过滤后同桶相对序正确
            Expression<Object> statusBucket = cb.selectCase()
                .when(root.get(FieldNameConstants.STATUS_ENUM).in(waitingCodes), 0)
                .when(root.get(FieldNameConstants.STATUS_ENUM).in(inProgressCodes), 1)
                .otherwise(2);

            // 待诊桶：挂号/开始时间升序（先挂号先看诊）
            Expression<LocalDateTime> waitingStartTime = cb.<LocalDateTime>selectCase()
                .when(
                    root.get(FieldNameConstants.STATUS_ENUM).in(waitingCodes),
                    root.get(FieldNameConstants.START_TIME))
                .otherwise(cb.nullLiteral(LocalDateTime.class));

            // 在诊/暂离桶：接诊时间降序（后接诊在上）
            Expression<LocalDateTime> inProgressReceptionTime = cb.<LocalDateTime>selectCase()
                .when(
                    root.get(FieldNameConstants.STATUS_ENUM).in(inProgressCodes),
                    root.get(FieldNameConstants.RECEPTION_TIME))
                .otherwise(cb.nullLiteral(LocalDateTime.class));

            // 诊毕等其余桶：开始时间降序
            Expression<LocalDateTime> otherStartTime = cb.<LocalDateTime>selectCase()
                .when(
                    cb.not(root.get(FieldNameConstants.STATUS_ENUM).in(
                        List.of(
                            EncounterStatus.PLANNED.getCode(),
                            EncounterStatus.REGISTERED.getCode(),
                            EncounterStatus.TRIAGED.getCode(),
                            EncounterStatus.IN_PROGRESS.getCode(),
                            EncounterStatus.ON_HOLD.getCode()))),
                    root.get(FieldNameConstants.START_TIME))
                .otherwise(cb.nullLiteral(LocalDateTime.class));

            List<Order> orders = new ArrayList<>();
            // B.2.1：有关键字时精确命中置顶，再接状态分桶
            if (StringUtils.isNotEmpty(trimmedKeyword)) {
                Expression<Integer> exactRank = this.encounterKeywordExactRankExpression(
                    cb, root, query, trimmedKeyword, true, true, false);
                orders.add(cb.asc(exactRank));
            }
            orders.add(cb.asc(statusBucket));
            orders.add(cb.asc(waitingStartTime));
            orders.add(cb.desc(inProgressReceptionTime));
            orders.add(cb.desc(otherStartTime));
            orders.add(cb.desc(root.get(FieldNameConstants.ID)));
            query.orderBy(orders);
            return cb.conjunction();
        };
    }

    /**
     * 按单状态码解析门诊列表 Sort。
     *
     * @param statusCode EncounterStatus 持久化 code
     * @return 对应业务键的 Sort
     */
    private Sort resolveOutpatientSortByStatusCode(String statusCode) {
        // 待诊（含待挂号/已分诊单查）：开始时间升序
        if (isWaitingReceptionStatus(statusCode)) {
            return Sort.by(
                Sort.Order.asc(FieldNameConstants.START_TIME),
                Sort.Order.asc(FieldNameConstants.ID));
        }
        // 在诊/暂离：接诊时间降序
        if (isInProgressOrHoldStatus(statusCode)) {
            return Sort.by(
                Sort.Order.desc(FieldNameConstants.RECEPTION_TIME),
                Sort.Order.desc(FieldNameConstants.ID));
        }
        // 诊毕等其余：开始时间降序
        return Sort.by(
            Sort.Order.desc(FieldNameConstants.START_TIME),
            Sort.Order.desc(FieldNameConstants.ID));
    }

    /**
     * 是否待接诊态（排队叫号按挂号时间升序）。
     *
     * @param statusCode 就诊状态 code
     * @return true 表示 PLANNED/REGISTERED/TRIAGED
     */
    private boolean isWaitingReceptionStatus(String statusCode) {
        if (statusCode == null) {
            return false;
        }
        if (EncounterStatus.PLANNED.getCode().equals(statusCode)) {
            return true;
        }
        if (EncounterStatus.REGISTERED.getCode().equals(statusCode)) {
            return true;
        }
        if (EncounterStatus.TRIAGED.getCode().equals(statusCode)) {
            return true;
        }
        return false;
    }

    /**
     * 是否在诊或暂离（按接诊时间降序）。
     *
     * @param statusCode 就诊状态 code
     * @return true 表示 IN_PROGRESS/ON_HOLD
     */
    private boolean isInProgressOrHoldStatus(String statusCode) {
        if (statusCode == null) {
            return false;
        }
        if (EncounterStatus.IN_PROGRESS.getCode().equals(statusCode)) {
            return true;
        }
        if (EncounterStatus.ON_HOLD.getCode().equals(statusCode)) {
            return true;
        }
        return false;
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
        // B.2.1：关键字非空时无 Sort，保留 Spec 内精确优先 ORDER BY
        if (query != null && StringUtils.isNotEmpty(query.getKeyword())) {
            return PageRequest.of(pageNo, pageSize);
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
     * 护士站列表状态收窄：默认在院并集；{@code statusEnumIn} 可由应用层预解析后传入。
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
     * 解析护士站状态筛选：优先 {@code statusEnumIn}；{@code nurseStationStatusFilter} 已废止不再解析。
     */
    private Collection<String> resolveNurseStationStatusEnumIn(InpatientEncounterPageQuery query) {
        if (query.getStatusEnumIn() != null && !query.getStatusEnumIn().isEmpty()) {
            return query.getStatusEnumIn();
        }
        return Collections.emptyList();
    }

    /** 护士站地理收窄：活动床/病区 + 预解析床位（默认在院列表）。 */
    private void applyNurseStationGeographicScope(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        InpatientEncounterPageQuery query,
        EncounterSessionScope scope,
        InpatientEncounterLocationScope locationScope) {
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
        // B.2.1：住院队列关键字（单号/姓名/床位）精确置顶
        this.applyEncounterKeywordExactFirstOrder(
            criteriaQuery, cb, root, query.getKeyword().trim(), true, true, true,
            List.of(
                cb.desc(root.get(FieldNameConstants.START_TIME)),
                cb.desc(root.get(FieldNameConstants.ID))));
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
}
