/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.service;

import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.administration.encounter.entity.Encounter;
import com.zxplat.domain.administration.encounter.entity.EncounterDiagnosis;
import com.zxplat.domain.administration.encounter.entity.EncounterLocation;
import com.zxplat.domain.administration.encounter.entity.EncounterParticipant;
import com.zxplat.domain.administration.encounter.queries.ClinicRegisterPageQuery;
import com.zxplat.domain.administration.encounter.queries.EncounterSessionScope;
import com.zxplat.domain.administration.encounter.queries.InpatientEncounterLocationScope;
import com.zxplat.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.zxplat.domain.administration.encounter.queries.NurseStationOutpatientQueueQuery;
import com.zxplat.domain.administration.encounter.queries.MedicalTechWorkstationQueueQuery;
import com.zxplat.domain.administration.encounter.queries.OutpatientEncounterPageQuery;
import com.zxplat.domain.administration.encounter.queries.RegistrationSlipPageQuery;
import com.zxplat.domain.administration.location.entity.Location;
import com.zxplat.domain.administration.organization.entity.Organization;
import com.zxplat.domain.administration.patient.entity.Patient;
import com.zxplat.domain.clinical.procedure.entity.Procedure;
import com.zxplat.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.zxplat.domain.medication.medicationrequest.entity.MedicationRequest;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.constants.GeneralSharedConstants;
import com.zxplat.domain.share.constants.InpatientDailySettlementConstants;
import com.zxplat.domain.share.constants.InpatientRegistrationConstants;
import com.zxplat.domain.share.constants.InpatientSettleQueueTab;
import com.zxplat.domain.share.constants.MedicalTechExecutionConstants;
import com.zxplat.domain.share.constants.RegistrationSlipConstants;
import com.zxplat.domain.share.constants.TableNameConstants;
import com.zxplat.domain.financial.paymentreconciliation.entity.PaymentReconciliation;
import com.zxplat.domain.share.enums.PaymentKind;
import com.zxplat.domain.share.enums.PaymentStatus;
import com.zxplat.domain.share.enums.ChargeItemStatus;
import com.zxplat.domain.share.enums.EncounterActivityStatus;
import com.zxplat.domain.share.enums.EncounterClass;
import com.zxplat.domain.share.enums.EncounterParticipantRole;
import com.zxplat.domain.share.enums.EncounterStatus;
import com.zxplat.domain.share.enums.EventStatus;
import com.zxplat.domain.share.enums.LocationForm;
import com.zxplat.domain.share.enums.OrganizationClassification;
import com.zxplat.domain.share.enums.RequestStatus;
import com.zxplat.domain.share.enums.DispenseStatus;
import com.zxplat.domain.share.enums.TherapyType;
import com.zxplat.domain.share.query.AbstractPageQuery;
import com.zxplat.domain.share.utils.KeywordExactFirstOrderSupport;
import com.zxplat.domain.share.utils.SearchCodeUtil;
import com.zxplat.domain.workflow.devicedispense.entity.DeviceDispense;
import com.zxplat.domain.workflow.devicerequest.entity.DeviceRequest;
import com.zxplat.domain.workflow.servicerequest.entity.ServiceRequest;
import com.zxplat.framework.core.application.exception.BusinessException;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
     * 门诊护士站执行队列筛选规约（BR-NUR-001 准入；可选开立科室、日期窗、仅待发耗材）。
     *
     * @param query 分页与关键字/日期窗/开立科室/{@code pendingDeviceOnly}；{@code null} 时使用空条件
     * @return JPA 规约
     */
    public Specification<Encounter> nurseStationOutpatientSpecification(AbstractPageQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(this.outpatientClassEnumPredicate(root, cb));
            AbstractPageQuery effective;
            // 空入参按无筛选壳，避免 NPE
            if (query != null) {
                effective = query;
            } else {
                effective = new AbstractPageQuery();
            }
            // 开立科室：非空时按就诊 organization_id 等值收窄；空=全部科室
            if (effective instanceof NurseStationOutpatientQueueQuery nurseQuery
                && nurseQuery.getOrgId() != null) {
                predicates.add(cb.equal(
                    root.get(FieldNameConstants.ORGANIZATION_ID), nurseQuery.getOrgId()));
            }
            // 关键字：患者姓名/拼音/五笔 EXISTS，并精确命中置顶
            if (StringUtils.isNotEmpty(effective.getKeyword())) {
                String trimmed = effective.getKeyword().trim();
                String pattern = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                predicates.add(this.existsPatientKeyword(root, cb, criteriaQuery, pattern));
                // B.2.1：护士站患者关键字精确置顶，次级开始时间倒序
                this.applyEncounterKeywordExactFirstOrder(
                    criteriaQuery, cb, root, trimmed, false, true, false,
                    List.of(
                        cb.desc(root.get(FieldNameConstants.START_TIME)),
                        cb.desc(root.get(FieldNameConstants.ID))));
            }
            // 就诊开始时间下界（工作台日期区间起日 00:00:00）
            if (effective.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get(FieldNameConstants.START_TIME), effective.getStartDate()));
            }
            // 就诊开始时间上界（工作台日期区间止日 23:59:59）
            if (effective.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    root.get(FieldNameConstants.START_TIME), effective.getEndDate()));
            }
            predicates.add(this.buildNurseStationRequestExistsAny(root, cb, criteriaQuery));
            // 仅待发耗材：在 BR-NUR-001 准入之上再收窄到仍有待发放耗材的就诊（分页前）
            if (effective instanceof NurseStationOutpatientQueueQuery nursePendingQuery
                && Boolean.TRUE.equals(nursePendingQuery.getPendingDeviceOnly())) {
                predicates.add(this.buildNurseStationPendingDeviceExists(root, cb, criteriaQuery));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 医技工作站患者队列筛选规约（BR-MT-EXE-002 有活；可选仅待发耗材 BR-MT-EXE-003；
     * 可选待执行/已执行分桶 BR-MT-EXE-023）。
     *
     * <p>须携带登录医技科 {@code loginTechOrgId} 与 {@code encounterScope}；跨聚合 EXISTS 落在本 Support，
     * 不由 {@code EncounterQueryService} 内联 subquery（B.6.4）。住院按 {@code inpatientStayFilter}
     * 收窄在院（已入科/待转科·待出院/待出院结算）或不在院（已清床离院）。</p>
     *
     * <p>队列准入以本站可见诊疗有活为硬门槛：仅有本科耗材房待发、无本站诊疗项目的就诊不进队
     * （避免他科划价耗材挂在本科房却无本站项目时误入队）。勾选「仅待发」时在诊疗有活之上再叠加耗材待发。
     * {@code executionQueueFilter}：门诊按诊疗尚可执行 ∪ 待发耗材分桶；住院仅按诊疗尚可执行（不看耗材）。</p>
     *
     * @param query 分页与 TECH 科/scope/关键字/日期窗/{@code pendingDeviceOnly}/
     *              {@code executionQueueFilter}/{@code inpatientStayFilter}；空或缺关键字段时返回空结果规约
     * @return JPA 规约
     */
    public Specification<Encounter> medicalTechWorkstationSpecification(MedicalTechWorkstationQueueQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            // Gate：缺登录科或 scope 非法则空结果，避免误扫他科/全类别
            if (query == null || query.getLoginTechOrgId() == null) {
                predicates.add(cb.disjunction());
                return cb.and(predicates.toArray(new Predicate[0]));
            }
            String classEnumCode = this.resolveMedicalTechEncounterClassCode(query.getEncounterScope());
            if (classEnumCode == null) {
                predicates.add(cb.disjunction());
                return cb.and(predicates.toArray(new Predicate[0]));
            }
            predicates.add(cb.equal(root.get(FieldNameConstants.CLASS_ENUM), classEnumCode));
            Long loginTechOrgId = query.getLoginTechOrgId();
            // 诊疗有活可见轴：共看组 IN；否则回落登录科单值
            List<Long> visibleTechOrgIds =
                this.resolveMedicalTechVisibleTechOrgIds(query.getVisibleTechOrgIds(), loginTechOrgId);
            // 住院：按在院/不在院筛选就诊态（默认在院；排除待入科）
            if (EncounterClass.IMP.getCode().equals(classEnumCode)) {
                this.applyMedicalTechInpatientVisibleStatus(
                    predicates, root, cb, query.getInpatientStayFilter());
            }
            // 关键字：患者姓名/拼音/五笔 EXISTS，并精确命中置顶
            if (StringUtils.isNotEmpty(query.getKeyword())) {
                String trimmed = query.getKeyword().trim();
                String pattern = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
                predicates.add(this.existsPatientKeyword(root, cb, criteriaQuery, pattern));
                // B.2.1：医技站患者关键字精确置顶，次级开始时间倒序
                this.applyEncounterKeywordExactFirstOrder(
                    criteriaQuery, cb, root, trimmed, false, true, false,
                    List.of(
                        cb.desc(root.get(FieldNameConstants.START_TIME)),
                        cb.desc(root.get(FieldNameConstants.ID))));
            }
            // 就诊开始时间闭区间
            if (query.getStartDate() != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                    root.get(FieldNameConstants.START_TIME), query.getStartDate()));
            }
            if (query.getEndDate() != null) {
                predicates.add(cb.lessThanOrEqualTo(
                    root.get(FieldNameConstants.START_TIME), query.getEndDate()));
            }
            // Pre：本站可见诊疗有活为硬门槛；仅耗材待发不得单独进队
            predicates.add(this.buildMedicalTechServiceRequestExists(
                root, cb, criteriaQuery, visibleTechOrgIds, classEnumCode));
            // 仅待发：在诊疗有活之上再收窄本科耗材房待发（BR-MT-EXE-003）
            if (Boolean.TRUE.equals(query.getPendingDeviceOnly())) {
                predicates.add(this.buildMedicalTechPendingDeviceExists(
                    root, cb, criteriaQuery, loginTechOrgId));
            }
            // 待执行/已执行分桶（BR-MT-EXE-023）；空或非法码不收窄，兼容旧调用
            this.applyMedicalTechExecutionQueueFilter(
                predicates,
                root,
                cb,
                criteriaQuery,
                query.getExecutionQueueFilter(),
                classEnumCode,
                visibleTechOrgIds,
                loginTechOrgId);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 医技队列按整单执行态分桶（BR-MT-EXE-023）。
     *
     * <p>{@code PENDING}：仍有尚可执行诊疗；门诊另含待发耗材。
     * {@code EXECUTED}：诊疗均已做满；门诊另无待发耗材；住院不看耗材。
     * 空/非法码不追加谓词。</p>
     *
     * @param predicates         外层谓词列表（就地追加）
     * @param root               就诊根
     * @param cb                 Criteria 构造器
     * @param owningQuery        外层查询
     * @param executionQueueFilter {@code PENDING}/{@code EXECUTED}；可空
     * @param classEnumCode      AMB 或 IMP
     * @param visibleTechOrgIds  可见执行科室
     * @param loginTechOrgId     登录医技科（耗材可见轴）
     */
    private void applyMedicalTechExecutionQueueFilter(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> owningQuery,
        String executionQueueFilter,
        String classEnumCode,
        List<Long> visibleTechOrgIds,
        Long loginTechOrgId) {
        // 空筛选：保持历史「有活全量」行为，不按执行态收窄
        if (StringUtils.isEmpty(executionQueueFilter)) {
            return;
        }
        String filterCode = executionQueueFilter.trim();
        boolean outpatient = EncounterClass.AMB.getCode().equals(classEnumCode);
        Predicate pendingService = this.buildMedicalTechPendingServiceExists(
            root, cb, owningQuery, visibleTechOrgIds, classEnumCode);
        // 门诊分桶才叠耗材；住院整单只认诊疗尚可执行
        Predicate pendingBucket;
        if (outpatient) {
            Predicate pendingDevice = this.buildMedicalTechPendingDeviceExists(
                root, cb, owningQuery, loginTechOrgId);
            pendingBucket = cb.or(pendingService, pendingDevice);
        } else {
            // 住院：项目全做完即已执行，待发耗材不把患者拉回待执行
            pendingBucket = pendingService;
        }
        if (MedicalTechExecutionConstants.EXECUTION_QUEUE_FILTER_PENDING.equals(filterCode)) {
            // 待执行：整单仍有活
            predicates.add(pendingBucket);
            return;
        }
        if (MedicalTechExecutionConstants.EXECUTION_QUEUE_FILTER_EXECUTED.equals(filterCode)) {
            // 已执行：整单活已清（仍须本站诊疗有活硬门槛）
            predicates.add(cb.not(pendingBucket));
            return;
        }
        // 非法码：不收窄，避免误空页
    }

    /**
     * 解析医技队列诊疗有活可见科室：入参非空用入参；否则单元素登录科。
     *
     * @param visibleTechOrgIds 共看组可见科室（可空）
     * @param loginTechOrgId    登录医技科
     * @return 非空可见列表；登录科空则空列表
     */
    private List<Long> resolveMedicalTechVisibleTechOrgIds(
        List<Long> visibleTechOrgIds,
        Long loginTechOrgId) {
        List<Long> resolved = new ArrayList<>();
        if (visibleTechOrgIds != null) {
            for (Long orgId : visibleTechOrgIds) {
                if (orgId != null && !resolved.contains(orgId)) {
                    resolved.add(orgId);
                }
            }
        }
        if (!resolved.isEmpty()) {
            return resolved;
        }
        if (loginTechOrgId != null) {
            resolved.add(loginTechOrgId);
        }
        return resolved;
    }

    /** 门诊类别谓词：仅 AMB。 */
    private Predicate outpatientClassEnumPredicate(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb) {
        return cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.AMB.getCode());
    }

    /**
     * 医生站查看者可见性（BR-DOR-009）：按状态分派，与是否存在活动 {@code REGISTRATION_DOCTOR}/{@code ADMITTER} 组合。
     *
     * <p>待诊：无活动挂号医生（且无活动接诊医生）时科室全员可见；有挂号医生时仅该医师可见（历史误写
     * {@code ADMITTER} 兼容同口径）。接诊中/暂离：无论挂号是否指定医生，仅活动接诊医生
     * （{@code ADMITTER}）可见。诊毕：始终科室共享。</p>
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
            // 接诊中/暂离：统一仅活动 ADMITTER 可见（挂到科室与挂到医生同口径）
            Predicate admitterIsViewer = this.existsActiveParticipantWithRoleAndPractitioner(
                root, cb, criteriaQuery,
                EncounterParticipantRole.ADMITTER.getCode(),
                viewerPractitionerId);
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
     *
     * <p>可见性轴（DD / BR-NUR-001）：诊疗执行科室非 TECH；耗材发放房上级科室非 TECH（无发放兜底进护士站）；
     * 输液/皮试药支路不受 TECH 轴影响。</p>
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
            cb.isFalse(sr.get(FieldNameConstants.IS_DELETED)),
            // 执行科室 class=TECH 归医技站，不计入护士站有活
            this.notExistsTechOrganizationForOrgId(sr.get(FieldNameConstants.ORG_ID), cb, owningQuery)));

        Subquery<Long> deviceSq = owningQuery.subquery(Long.class);
        Root<DeviceRequest> dr = deviceSq.from(DeviceRequest.class);
        deviceSq.select(dr.get(FieldNameConstants.ID));
        deviceSq.where(cb.and(
            cb.equal(dr.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.equal(dr.get(FieldNameConstants.STATUS_ENUM), RequestStatus.COMPLETED.getCode()),
            cb.isNull(dr.get(FieldNameConstants.REFUND_DEVICE_ID)),
            cb.isFalse(dr.get(FieldNameConstants.IS_DELETED)),
            // 请求库位挂靠科室（或父科）为 TECH 时归医技站
            this.notExistsTechOrganizationForDispenseLocation(
                dr.get(FieldNameConstants.LOCATION_ID), cb, owningQuery),
            // 发放耗材房上级（或父科）为 TECH 时归医技站；无发放行时再看请求库位
            this.notExistsTechLocationDispenseForDeviceRequest(dr, cb, owningQuery)));

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

    /**
     * 门诊护士站：就诊下存在「已收费且仍待发放」的耗材（发放房上级非 TECH）。
     *
     * <p>与处置单侧栏一致：耗材请求 {@code COMPLETED}、非退单；发放单缺失或状态为草稿/待配药/待发药；
     * TECH 耗材房待发不计入护士站红点（BR-NUR-017 / 可见性 DD）。</p>
     *
     * @param root        就诊根
     * @param cb          Criteria 构造器
     * @param owningQuery 外层查询
     * @return EXISTS 谓词
     */
    private Predicate buildNurseStationPendingDeviceExists(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> owningQuery) {
        Subquery<Long> pendingRequestSq = owningQuery.subquery(Long.class);
        Root<DeviceRequest> deviceRequestRoot = pendingRequestSq.from(DeviceRequest.class);

        // 仍为草稿/待配药/待发药的发放行（空状态不在 IN 列表，由「无发放单」分支覆盖）
        Subquery<Long> pendingDispenseSq = owningQuery.subquery(Long.class);
        Root<DeviceDispense> pendingDispenseRoot = pendingDispenseSq.from(DeviceDispense.class);
        pendingDispenseSq.select(pendingDispenseRoot.get(FieldNameConstants.ID));
        pendingDispenseSq.where(cb.and(
            cb.equal(
                pendingDispenseRoot.get(FieldNameConstants.DEVICE_REQ_ID),
                deviceRequestRoot.get(FieldNameConstants.ID)),
            cb.isFalse(pendingDispenseRoot.get(FieldNameConstants.IS_DELETED)),
            pendingDispenseRoot.get(FieldNameConstants.STATUS_ENUM).in(
                DispenseStatus.DRAFT.getCode(),
                DispenseStatus.PREPARATION.getCode(),
                DispenseStatus.IN_PROGRESS.getCode()),
            // 待发红点排除 TECH 耗材房
            this.notExistsTechOrganizationForDispenseLocation(
                pendingDispenseRoot.get(FieldNameConstants.LOCATION_ID), cb, owningQuery)));

        // 该请求下是否已有任意未删发放单（用于「已收费但尚未生成发放」）
        Subquery<Long> anyDispenseSq = owningQuery.subquery(Long.class);
        Root<DeviceDispense> anyDispenseRoot = anyDispenseSq.from(DeviceDispense.class);
        anyDispenseSq.select(anyDispenseRoot.get(FieldNameConstants.ID));
        anyDispenseSq.where(cb.and(
            cb.equal(
                anyDispenseRoot.get(FieldNameConstants.DEVICE_REQ_ID),
                deviceRequestRoot.get(FieldNameConstants.ID)),
            cb.isFalse(anyDispenseRoot.get(FieldNameConstants.IS_DELETED))));

        // 有效计费：请求锚或发放锚上仍存在 PLANNED/SETTLED（排除退费后仍 PREPARATION 的脏待发，BR-NUR-018）
        Subquery<Long> billableChargeSq = owningQuery.subquery(Long.class);
        Root<ChargeItem> billableChargeRoot = billableChargeSq.from(ChargeItem.class);
        Subquery<Long> requestDispenseIdsSq = owningQuery.subquery(Long.class);
        Root<DeviceDispense> requestDispenseRoot = requestDispenseIdsSq.from(DeviceDispense.class);
        requestDispenseIdsSq.select(requestDispenseRoot.get(FieldNameConstants.ID));
        requestDispenseIdsSq.where(cb.and(
            cb.equal(
                requestDispenseRoot.get(FieldNameConstants.DEVICE_REQ_ID),
                deviceRequestRoot.get(FieldNameConstants.ID)),
            cb.isFalse(requestDispenseRoot.get(FieldNameConstants.IS_DELETED))));
        billableChargeSq.select(billableChargeRoot.get(FieldNameConstants.ID));
        billableChargeSq.where(cb.and(
            cb.equal(billableChargeRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.isFalse(billableChargeRoot.get(FieldNameConstants.IS_DELETED)),
            billableChargeRoot.get(FieldNameConstants.STATUS_ENUM).in(
                ChargeItemStatus.PLANNED.getCode(),
                ChargeItemStatus.SETTLED.getCode()),
            cb.or(
                cb.and(
                    cb.equal(
                        billableChargeRoot.get(FieldNameConstants.SERVICE_TABLE),
                        TableNameConstants.WKF_DEVICE_REQUEST),
                    cb.equal(
                        billableChargeRoot.get(FieldNameConstants.SERVICE_ID),
                        deviceRequestRoot.get(FieldNameConstants.ID))),
                cb.and(
                    cb.equal(
                        billableChargeRoot.get(FieldNameConstants.SERVICE_TABLE),
                        TableNameConstants.WKF_DEVICE_DISPENSE),
                    billableChargeRoot.get(FieldNameConstants.SERVICE_ID).in(requestDispenseIdsSq)),
                cb.and(
                    cb.equal(
                        billableChargeRoot.get(FieldNameConstants.DISPENSE_TABLE),
                        TableNameConstants.WKF_DEVICE_DISPENSE),
                    billableChargeRoot.get(FieldNameConstants.DISPENSE_ID).in(requestDispenseIdsSq)))));

        pendingRequestSq.select(deviceRequestRoot.get(FieldNameConstants.ID));
        pendingRequestSq.where(cb.and(
            cb.equal(deviceRequestRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.equal(deviceRequestRoot.get(FieldNameConstants.STATUS_ENUM), RequestStatus.COMPLETED.getCode()),
            cb.isNull(deviceRequestRoot.get(FieldNameConstants.REFUND_DEVICE_ID)),
            cb.isFalse(deviceRequestRoot.get(FieldNameConstants.IS_DELETED)),
            cb.exists(billableChargeSq),
            // 仅 TECH 耗材房发放的请求不进护士站待发
            this.notExistsTechLocationDispenseForDeviceRequest(deviceRequestRoot, cb, owningQuery),
            cb.or(cb.exists(pendingDispenseSq), cb.not(cb.exists(anyDispenseSq)))));
        return cb.exists(pendingRequestSq);
    }

    /**
     * 断言 orgId 对应科室不是 TECH（orgId 空视为非 TECH 兜底）。
     *
     * @param orgIdPath   ServiceRequest.orgId 路径
     * @param cb          Criteria 构造器
     * @param owningQuery 外层查询
     * @return NOT EXISTS TECH 组织谓词
     */
    private Predicate notExistsTechOrganizationForOrgId(
        jakarta.persistence.criteria.Path<Long> orgIdPath,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> owningQuery) {
        Subquery<Long> techOrgSq = owningQuery.subquery(Long.class);
        Root<Organization> orgRoot = techOrgSq.from(Organization.class);
        techOrgSq.select(orgRoot.get(FieldNameConstants.ID));
        techOrgSq.where(cb.and(
            cb.equal(orgRoot.get(FieldNameConstants.ID), orgIdPath),
            cb.equal(orgRoot.get(FieldNameConstants.CLASS_ENUM), OrganizationClassification.TECH),
            cb.isFalse(orgRoot.get(FieldNameConstants.IS_DELETED))));
        return cb.or(cb.isNull(orgIdPath), cb.not(cb.exists(techOrgSq)));
    }

    /**
     * 断言耗材请求下不存在发放房挂靠科室（或父科）为 TECH 的发放行。
     *
     * <p>无发放行时本谓词为真；一旦签发到 TECH 房（含挂「医技」父科）则护士站不再认该请求有活。</p>
     *
     * @param deviceRequestRoot 耗材请求根
     * @param cb                Criteria 构造器
     * @param owningQuery       外层查询
     * @return NOT EXISTS TECH 房发放谓词
     */
    private Predicate notExistsTechLocationDispenseForDeviceRequest(
        Root<DeviceRequest> deviceRequestRoot,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> owningQuery) {
        Subquery<Long> techDispenseSq = owningQuery.subquery(Long.class);
        Root<DeviceDispense> dispenseRoot = techDispenseSq.from(DeviceDispense.class);
        Root<Location> locationRoot = techDispenseSq.from(Location.class);
        Root<Organization> orgRoot = techDispenseSq.from(Organization.class);
        Root<Organization> parentOrgRoot = techDispenseSq.from(Organization.class);
        techDispenseSq.select(dispenseRoot.get(FieldNameConstants.ID));
        techDispenseSq.where(cb.and(
            cb.equal(
                dispenseRoot.get(FieldNameConstants.DEVICE_REQ_ID),
                deviceRequestRoot.get(FieldNameConstants.ID)),
            cb.isFalse(dispenseRoot.get(FieldNameConstants.IS_DELETED)),
            cb.equal(dispenseRoot.get(FieldNameConstants.LOCATION_ID), locationRoot.get(FieldNameConstants.ID)),
            cb.isFalse(locationRoot.get(FieldNameConstants.IS_DELETED)),
            cb.equal(locationRoot.get(FieldNameConstants.ORGANIZATION_ID), orgRoot.get(FieldNameConstants.ID)),
            cb.isFalse(orgRoot.get(FieldNameConstants.IS_DELETED)),
            // 挂靠科室自身为 TECH，或父科为 TECH（科室药房挂医技上级）
            this.orgSelfOrParentIsTech(cb, orgRoot, parentOrgRoot)));
        return cb.not(cb.exists(techDispenseSq));
    }

    /**
     * 断言 location 挂靠科室（或父科）不是 TECH（location 空视为非 TECH）。
     *
     * @param locationIdPath 发放/请求 location_id 路径
     * @param cb             Criteria 构造器
     * @param owningQuery    外层查询
     * @return NOT EXISTS TECH 谓词
     */
    private Predicate notExistsTechOrganizationForDispenseLocation(
        jakarta.persistence.criteria.Path<Long> locationIdPath,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> owningQuery) {
        Subquery<Long> techLocSq = owningQuery.subquery(Long.class);
        Root<Location> locationRoot = techLocSq.from(Location.class);
        Root<Organization> orgRoot = techLocSq.from(Organization.class);
        Root<Organization> parentOrgRoot = techLocSq.from(Organization.class);
        techLocSq.select(locationRoot.get(FieldNameConstants.ID));
        techLocSq.where(cb.and(
            cb.equal(locationRoot.get(FieldNameConstants.ID), locationIdPath),
            cb.isFalse(locationRoot.get(FieldNameConstants.IS_DELETED)),
            cb.equal(locationRoot.get(FieldNameConstants.ORGANIZATION_ID), orgRoot.get(FieldNameConstants.ID)),
            cb.isFalse(orgRoot.get(FieldNameConstants.IS_DELETED)),
            this.orgSelfOrParentIsTech(cb, orgRoot, parentOrgRoot)));
        return cb.or(cb.isNull(locationIdPath), cb.not(cb.exists(techLocSq)));
    }

    /**
     * 组织自身 class=TECH，或父组织 class=TECH。
     *
     * @param cb            Criteria 构造器
     * @param orgRoot       挂靠科室
     * @param parentOrgRoot 父组织根（与挂靠科室同查询）
     * @return TECH 轴谓词
     */
    private Predicate orgSelfOrParentIsTech(
        jakarta.persistence.criteria.CriteriaBuilder cb,
        Root<Organization> orgRoot,
        Root<Organization> parentOrgRoot) {
        Predicate selfTech = cb.equal(orgRoot.get(FieldNameConstants.CLASS_ENUM), OrganizationClassification.TECH);
        Predicate parentTech = cb.and(
            cb.equal(orgRoot.get(FieldNameConstants.PARENT_ID), parentOrgRoot.get(FieldNameConstants.ID)),
            cb.isFalse(parentOrgRoot.get(FieldNameConstants.IS_DELETED)),
            cb.equal(parentOrgRoot.get(FieldNameConstants.CLASS_ENUM), OrganizationClassification.TECH));
        return cb.or(selfTech, parentTech);
    }

    /**
     * 将医技队列 {@code encounterScope} 映射为就诊 {@code class_enum}。
     *
     * @param encounterScope {@code OUTPATIENT}/{@code INPATIENT}；其它返回 null
     * @return AMB/IMP code，非法时 null
     */
    private String resolveMedicalTechEncounterClassCode(String encounterScope) {
        if (StringUtils.isEmpty(encounterScope)) {
            return null;
        }
        String trimmed = encounterScope.trim();
        if ("OUTPATIENT".equalsIgnoreCase(trimmed)) {
            return EncounterClass.AMB.getCode();
        }
        if ("INPATIENT".equalsIgnoreCase(trimmed)) {
            return EncounterClass.IMP.getCode();
        }
        return null;
    }

    /**
     * 医技诊疗有活：执行科室 ∈ 可见 TECH 集合，退费关联空；门诊 COMPLETED / 住院 VERIFIED。
     *
     * @param root              就诊根
     * @param cb                Criteria 构造器
     * @param owningQuery       外层查询
     * @param visibleTechOrgIds 可见执行科室（共看组或登录科单值）
     * @param classEnumCode     AMB 或 IMP
     * @return EXISTS 谓词
     */
    private Predicate buildMedicalTechServiceRequestExists(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> owningQuery,
        List<Long> visibleTechOrgIds,
        String classEnumCode) {
        Subquery<Long> serviceSq = owningQuery.subquery(Long.class);
        Root<ServiceRequest> sr = serviceSq.from(ServiceRequest.class);
        serviceSq.select(sr.get(FieldNameConstants.ID));
        String requiredStatus;
        // 门诊已收费完成态；住院已校对态（BR-MT-EXE-002）
        if (EncounterClass.AMB.getCode().equals(classEnumCode)) {
            requiredStatus = RequestStatus.COMPLETED.getCode();
        } else {
            requiredStatus = RequestStatus.VERIFIED.getCode();
        }
        // Gate：可见科室空 → 永不命中，避免误扫全科
        Predicate orgPredicate;
        if (visibleTechOrgIds == null || visibleTechOrgIds.isEmpty()) {
            orgPredicate = cb.disjunction();
        } else if (visibleTechOrgIds.size() == 1) {
            orgPredicate = cb.equal(sr.get(FieldNameConstants.ORG_ID), visibleTechOrgIds.get(0));
        } else {
            orgPredicate = sr.get(FieldNameConstants.ORG_ID).in(visibleTechOrgIds);
        }
        serviceSq.where(cb.and(
            cb.equal(sr.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            orgPredicate,
            cb.equal(sr.get(FieldNameConstants.STATUS_ENUM), requiredStatus),
            cb.isNull(sr.get(FieldNameConstants.REFUND_SERVICE_ID)),
            cb.isFalse(sr.get(FieldNameConstants.IS_DELETED))));
        return cb.exists(serviceSq);
    }

    /**
     * 医技诊疗尚可执行：本站可见 SR 中至少一条净完成次数 &lt; 计划次数（与执行单 canExecute 同口径）。
     *
     * <p>计划次数：长期医嘱恒为 1；否则认 {@code execution_count}（缺/≤0 视为不可再执行）。
     * 净次 = 可见轴内 COMPLETED −（CANCEL/历史 ABORTED）；谓词用
     * {@code planned > 0 AND planned + cancelled > completed}，与 Java 侧 net 下限归零后比较等价。</p>
     *
     * @param root              就诊根
     * @param cb                Criteria 构造器
     * @param owningQuery       外层查询
     * @param visibleTechOrgIds 可见执行科室（Procedure.org_id 同轴）
     * @param classEnumCode     AMB 或 IMP
     * @return EXISTS 谓词
     */
    private Predicate buildMedicalTechPendingServiceExists(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> owningQuery,
        List<Long> visibleTechOrgIds,
        String classEnumCode) {
        Subquery<Long> pendingSrSq = owningQuery.subquery(Long.class);
        Root<ServiceRequest> sr = pendingSrSq.from(ServiceRequest.class);
        pendingSrSq.select(sr.get(FieldNameConstants.ID));
        String requiredStatus;
        // 门诊已收费；住院已校对（与有活准入同态）
        if (EncounterClass.AMB.getCode().equals(classEnumCode)) {
            requiredStatus = RequestStatus.COMPLETED.getCode();
        } else {
            requiredStatus = RequestStatus.VERIFIED.getCode();
        }
        Predicate orgPredicate = this.buildMedicalTechServiceOrgPredicate(cb, sr, visibleTechOrgIds);
        // 计划次数：长嘱=1；否则 coalesce(execution_count, 0)
        Expression<Integer> plannedExpr = cb.<Integer>selectCase()
            .when(
                cb.equal(sr.get(FieldNameConstants.THERAPY_ENUM), TherapyType.LONG_TERM.getCode()),
                cb.literal(Integer.valueOf(1)))
            .otherwise(cb.coalesce(sr.get(FieldNameConstants.EXECUTION_COUNT), cb.literal(Integer.valueOf(0))));
        Subquery<Long> completedCountSq = this.buildMedicalTechProcedureCountSubquery(
            owningQuery,
            cb,
            sr,
            visibleTechOrgIds,
            List.of(EventStatus.COMPLETED.getCode()));
        Subquery<Long> cancelledCountSq = this.buildMedicalTechProcedureCountSubquery(
            owningQuery,
            cb,
            sr,
            visibleTechOrgIds,
            List.of(
                EventStatus.CANCEL.getCode(),
                GeneralSharedConstants.EVENT_STATUS_LEGACY_ABORTED_CODE));
        // planned > 0 且 planned + cancelled > completed → 净次未满（含取消对冲后仍待执行）
        Expression<Long> plannedAsLong = plannedExpr.as(Long.class);
        Expression<Long> plannedPlusCancelled = cb.sum(plannedAsLong, cancelledCountSq);
        Predicate stillExecutable = cb.and(
            cb.greaterThan(plannedExpr, cb.literal(Integer.valueOf(0))),
            cb.greaterThan(plannedPlusCancelled, completedCountSq));
        pendingSrSq.where(cb.and(
            cb.equal(sr.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            orgPredicate,
            cb.equal(sr.get(FieldNameConstants.STATUS_ENUM), requiredStatus),
            cb.isNull(sr.get(FieldNameConstants.REFUND_SERVICE_ID)),
            cb.isFalse(sr.get(FieldNameConstants.IS_DELETED)),
            stillExecutable));
        return cb.exists(pendingSrSq);
    }

    /**
     * 诊疗请求执行科室 ∈ 可见 TECH 集合；空集合永不命中。
     *
     * @param cb                Criteria 构造器
     * @param sr                服务申请根
     * @param visibleTechOrgIds 可见执行科室
     * @return 组织谓词
     */
    private Predicate buildMedicalTechServiceOrgPredicate(
        jakarta.persistence.criteria.CriteriaBuilder cb,
        Root<ServiceRequest> sr,
        List<Long> visibleTechOrgIds) {
        if (visibleTechOrgIds == null || visibleTechOrgIds.isEmpty()) {
            return cb.disjunction();
        }
        if (visibleTechOrgIds.size() == 1) {
            return cb.equal(sr.get(FieldNameConstants.ORG_ID), visibleTechOrgIds.get(0));
        }
        return sr.get(FieldNameConstants.ORG_ID).in(visibleTechOrgIds);
    }

    /**
     * 统计挂靠某 ServiceRequest、且 org_id ∈ 可见轴、状态 ∈ 给定集合的 Procedure 条数。
     *
     * <p>与医技执行单净次同轴：仅计可见 TECH 科 Procedure，不含护士侧空 org。</p>
     *
     * @param owningQuery       外层查询
     * @param cb                Criteria 构造器
     * @param serviceRequestRoot 相关 ServiceRequest 根（取 id）
     * @param visibleTechOrgIds 可见执行科室
     * @param statusCodes       计入的 status_enum 集合
     * @return 计数子查询
     */
    private Subquery<Long> buildMedicalTechProcedureCountSubquery(
        jakarta.persistence.criteria.CriteriaQuery<?> owningQuery,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        Root<ServiceRequest> serviceRequestRoot,
        List<Long> visibleTechOrgIds,
        List<String> statusCodes) {
        Subquery<Long> countSq = owningQuery.subquery(Long.class);
        Root<Procedure> procedureRoot = countSq.from(Procedure.class);
        countSq.select(cb.count(procedureRoot.get(FieldNameConstants.ID)));
        Predicate orgPredicate;
        // Gate：可见科空 → 计数恒 0，避免把护士侧误算进医技净次
        if (visibleTechOrgIds == null || visibleTechOrgIds.isEmpty()) {
            orgPredicate = cb.disjunction();
        } else if (visibleTechOrgIds.size() == 1) {
            orgPredicate = cb.equal(procedureRoot.get(FieldNameConstants.ORG_ID), visibleTechOrgIds.get(0));
        } else {
            orgPredicate = procedureRoot.get(FieldNameConstants.ORG_ID).in(visibleTechOrgIds);
        }
        Predicate statusPredicate;
        if (statusCodes == null || statusCodes.isEmpty()) {
            statusPredicate = cb.disjunction();
        } else if (statusCodes.size() == 1) {
            statusPredicate = cb.equal(procedureRoot.get(FieldNameConstants.STATUS_ENUM), statusCodes.get(0));
        } else {
            statusPredicate = procedureRoot.get(FieldNameConstants.STATUS_ENUM).in(statusCodes);
        }
        countSq.where(cb.and(
            cb.equal(procedureRoot.get(FieldNameConstants.REQUEST_ID), serviceRequestRoot.get(FieldNameConstants.ID)),
            cb.equal(procedureRoot.get("requestTable"), TableNameConstants.WKF_SERVICE_REQUEST),
            orgPredicate,
            statusPredicate,
            cb.isFalse(procedureRoot.get(FieldNameConstants.IS_DELETED))));
        return countSq;
    }

    /**
     * 医技耗材有活/待发：发放耗材房挂靠科室属登录 TECH 可见轴，且发放仍为待配/待发族状态。
     *
     * <p>可见轴与 {@code DeviceSupplyTechVisibilitySupport} 同口径：挂靠 = 登录科；
     * 挂靠父 = 登录科；登录科挂在挂靠 TECH 之下。禁止「任意 TECH 互看」。</p>
     *
     * @param root           就诊根
     * @param cb             Criteria 构造器
     * @param owningQuery    外层查询
     * @param loginTechOrgId 登录医技科
     * @return EXISTS 谓词
     */
    private Predicate buildMedicalTechPendingDeviceExists(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> owningQuery,
        Long loginTechOrgId) {
        Subquery<Long> pendingSq = owningQuery.subquery(Long.class);
        Root<DeviceDispense> dispenseRoot = pendingSq.from(DeviceDispense.class);
        Root<Location> locationRoot = pendingSq.from(Location.class);
        Root<Organization> hangOrgRoot = pendingSq.from(Organization.class);
        pendingSq.select(dispenseRoot.get(FieldNameConstants.ID));
        pendingSq.where(cb.and(
            cb.equal(dispenseRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.isFalse(dispenseRoot.get(FieldNameConstants.IS_DELETED)),
            cb.equal(dispenseRoot.get(FieldNameConstants.LOCATION_ID), locationRoot.get(FieldNameConstants.ID)),
            cb.isFalse(locationRoot.get(FieldNameConstants.IS_DELETED)),
            cb.equal(locationRoot.get(FieldNameConstants.ORGANIZATION_ID), hangOrgRoot.get(FieldNameConstants.ID)),
            cb.isFalse(hangOrgRoot.get(FieldNameConstants.IS_DELETED)),
            this.locationHangBelongsToLoginTech(cb, owningQuery, hangOrgRoot, loginTechOrgId),
            dispenseRoot.get(FieldNameConstants.STATUS_ENUM).in(
                DispenseStatus.DRAFT.getCode(),
                DispenseStatus.PREPARATION.getCode(),
                DispenseStatus.IN_PROGRESS.getCode(),
                DispenseStatus.PREPARED.getCode())));
        return cb.exists(pendingSq);
    }

    /**
     * 库位挂靠科室是否属登录 TECH 可见轴。
     *
     * <p>相等；挂靠父=登录；或登录科挂在挂靠 TECH 之下。
     * 与应用层 {@code DeviceSupplyTechVisibilitySupport} 对齐：禁止任意 TECH 互看。</p>
     *
     * @param cb             Criteria 构造器
     * @param owningQuery    外层查询（供子查询）
     * @param hangOrgRoot    挂靠科室根
     * @param loginTechOrgId 登录 TECH
     * @return 可见轴谓词
     */
    private Predicate locationHangBelongsToLoginTech(
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> owningQuery,
        Root<Organization> hangOrgRoot,
        Long loginTechOrgId) {
        // 挂靠即登录科（检验科登录 → 仅检验科挂靠房）
        Predicate hangEqualsLogin = cb.equal(hangOrgRoot.get(FieldNameConstants.ID), loginTechOrgId);
        // 挂靠科室的父 = 登录科（本科下属药房）
        Predicate hangParentIsLogin = cb.equal(hangOrgRoot.get(FieldNameConstants.PARENT_ID), loginTechOrgId);
        // 登录科的父 = 挂靠科室，且挂靠科室 class=TECH（药房挂医技上级、登录子科）
        Subquery<Long> loginUnderHangSq = owningQuery.subquery(Long.class);
        Root<Organization> loginChildRoot = loginUnderHangSq.from(Organization.class);
        loginUnderHangSq.select(loginChildRoot.get(FieldNameConstants.ID));
        loginUnderHangSq.where(cb.and(
            cb.equal(loginChildRoot.get(FieldNameConstants.ID), loginTechOrgId),
            cb.isFalse(loginChildRoot.get(FieldNameConstants.IS_DELETED)),
            cb.equal(loginChildRoot.get(FieldNameConstants.PARENT_ID), hangOrgRoot.get(FieldNameConstants.ID)),
            cb.equal(hangOrgRoot.get(FieldNameConstants.CLASS_ENUM), OrganizationClassification.TECH)));
        return cb.or(hangEqualsLogin, hangParentIsLogin, cb.exists(loginUnderHangSq));
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

    /** 住院列表排序：医生站按 busNo；护士站床号自然序由 Spec ORDER BY；其余队列按 startTime。 */
    private Sort resolveInpatientSort(InpatientEncounterPageQuery query) {
        if (query != null && Boolean.TRUE.equals(query.getDoctorStationQueue())) {
            return Sort.by(
                Sort.Order.asc(FieldNameConstants.BUS_NO),
                Sort.Order.asc(FieldNameConstants.ID));
        }
        // 护士站：Pageable 不带 Sort，改由 applyNurseStationBedNaturalOrderBy 挂床号自然序
        if (query != null && Boolean.TRUE.equals(query.getNurseStationQueue())) {
            return Sort.unsorted();
        }
        if (query != null
            && (Boolean.TRUE.equals(query.getPrepayEligibleOnly())
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
                    // 预交金队列：资格态求交；不在院态须 EXISTS 预交金流水（无流水则列表无查阅意义）
                    this.applyPrepayEligibleStatus(predicates, root, cb, criteriaQuery, query);
                } else {
                    // 登记/入院列表：有筛选则映射 status；未传则查全部 IMP
                    this.applyRegistrationListStatus(predicates, root, cb, query);
                }
            }
            if (query.getPatientId() != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.PATIENT_ID), query.getPatientId()));
            }
            // 登记/入院列表显式科室筛选：等值匹配就诊 organization_id（与医生站会话收窄无关）
            if (query.getOrganizationId() != null) {
                predicates.add(cb.equal(
                    root.get(FieldNameConstants.ORGANIZATION_ID), query.getOrganizationId()));
            }
            if (StringUtils.isNotEmpty(query.getStatusEnum())) {
                predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum()));
            }
            this.applyAdmissionDate(predicates, root, cb, query);
            this.applyDischargeDate(predicates, root, cb, query);
            this.applyKeyword(predicates, root, cb, criteriaQuery, query);
            // 护士站无关键字：按活动床位名自然序（房号→床号→加床）；有关键字时保留精确置顶 ORDER BY
            if (Boolean.TRUE.equals(query.getNurseStationQueue())
                && StringUtils.isEmpty(query.getKeyword())) {
                this.applyNurseStationBedNaturalOrderBy(root, criteriaQuery, cb);
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 护士站队列 ORDER BY：活动床位名升序（轻量，仅一层标量子查询），未分床靠后，稳定键 id。
     *
     * <p>精确自然序（504-5 &lt; 504-14、加床靠后）由应用层对本页 DTO / 前端全量再排，避免 SQL 多层
     * replace/lpad 拖慢分页。</p>
     *
     * @param root          就诊根
     * @param criteriaQuery 当前查询；null 时跳过
     * @param cb            CriteriaBuilder
     */
    private void applyNurseStationBedNaturalOrderBy(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        jakarta.persistence.criteria.CriteriaBuilder cb) {
        if (criteriaQuery == null) {
            return;
        }
        Class<?> resultType = criteriaQuery.getResultType();
        if (resultType == Long.class || resultType == long.class) {
            return;
        }
        Expression<String> bedName = this.activeBedLocationNameExpression(root, criteriaQuery, cb);
        // 有床在前（null 床名垫底），再按床名、id
        Expression<Integer> assignedRank = cb.<Integer>selectCase()
            .when(cb.or(cb.isNull(bedName), cb.equal(cb.trim(bedName), "")), 1)
            .otherwise(0);
        List<Order> orders = new ArrayList<>();
        orders.add(cb.asc(assignedRank));
        orders.add(cb.asc(bedName));
        orders.add(cb.asc(root.get(FieldNameConstants.ID)));
        criteriaQuery.orderBy(orders);
    }

    /**
     * 活动床位 Location.name 标量子查询（多床时取 max(name) 保标量）。
     *
     * @param root          就诊根
     * @param criteriaQuery 外层查询
     * @param cb            CriteriaBuilder
     * @return 床位名表达式；无活动床时为 null
     */
    private Expression<String> activeBedLocationNameExpression(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        jakarta.persistence.criteria.CriteriaBuilder cb) {
        Subquery<String> sq = criteriaQuery.subquery(String.class);
        Root<EncounterLocation> elRoot = sq.from(EncounterLocation.class);
        Root<Location> locRoot = sq.from(Location.class);
        sq.select(cb.function("max", String.class, locRoot.get(FieldNameConstants.NAME)));
        // 与部分索引 idx_adm_enc_loc_enc_form_status（WHERE is_deleted=false）谓词对齐，便于命中索引
        sq.where(cb.and(
            cb.equal(elRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.isFalse(elRoot.get(FieldNameConstants.IS_DELETED)),
            cb.equal(elRoot.get(FieldNameConstants.STATUS_ENUM), EncounterActivityStatus.ACTIVE.getCode()),
            cb.equal(elRoot.get(FieldNameConstants.FORM_ENUM), LocationForm.BED.getCode()),
            cb.equal(elRoot.get(FieldNameConstants.LOCATION_ID), locRoot.get(FieldNameConstants.ID)),
            cb.isFalse(locRoot.get(FieldNameConstants.IS_DELETED))));
        return sq;
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

    /**
     * 医技工作站住院队列可见状态：按在院/不在院筛选。
     *
     * <p>{@code IN_HOSPITAL}（默认/空/非法）：已入科 / 待转科·待出院 / 待出院结算。
     * {@code NOT_IN_HOSPITAL}：已清床离院（{@code DISCHARGED}）。
     * 均排除待入科（{@code REGISTERED}）。</p>
     *
     * @param predicates           外层谓词列表
     * @param root                 就诊根
     * @param cb                   Criteria 构造器
     * @param inpatientStayFilter  在院态筛选码；可空
     */
    private void applyMedicalTechInpatientVisibleStatus(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        String inpatientStayFilter) {
        // 不在院：仅已清床离院
        if (MedicalTechExecutionConstants.INPATIENT_STAY_FILTER_NOT_IN_HOSPITAL.equals(
            inpatientStayFilter)) {
            predicates.add(cb.equal(
                root.get(FieldNameConstants.STATUS_ENUM),
                EncounterStatus.DISCHARGED.getCode()));
            return;
        }
        // 在院（默认）：已入科 / 待转科·待出院 / 待出院结算
        predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(
            EncounterStatus.IN_PROGRESS.getCode(),
            EncounterStatus.ON_HOLD.getCode(),
            EncounterStatus.PENDING.getCode()));
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

    /**
     * 预交金队列可见状态并集（在院四态 + COMPLETED 待清床 + DISCHARGED 已出院 + CANCELLED 取消登记/退院）。
     *
     * <p>若 {@code statusEnumIn} 非空，与可见态求交后下推；求交为空则无结果。
     * 收退写门禁仍仅四态。不在院三态（COMPLETED/DISCHARGED/CANCELLED）须 EXISTS 已收口预交金流水，
     * 否则「不在院」列表无查阅意义（对齐 IF-IP-PREP-02 可见流水口径）。</p>
     *
     * @param predicates     谓词累加器
     * @param root           就诊根
     * @param cb             CriteriaBuilder
     * @param criteriaQuery  外层查询（供 EXISTS 子查询）
     * @param query          住院分页查询（可读 {@code statusEnumIn}）
     */
    private void applyPrepayEligibleStatus(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        InpatientEncounterPageQuery query) {
        List<String> eligible = new ArrayList<>();
        eligible.add(EncounterStatus.REGISTERED.getCode());
        eligible.add(EncounterStatus.IN_PROGRESS.getCode());
        eligible.add(EncounterStatus.ON_HOLD.getCode());
        eligible.add(EncounterStatus.PENDING.getCode());
        eligible.add(EncounterStatus.COMPLETED.getCode());
        // 已清床离院：预交金页「不在院/全部」只读查阅流水与余额
        eligible.add(EncounterStatus.DISCHARGED.getCode());
        // 取消登记（院内口语「退院」）：余额已退清后仍须可查历史预交金流水
        eligible.add(EncounterStatus.CANCELLED.getCode());
        Collection<String> statusEnumIn = null;
        if (query != null) {
            statusEnumIn = query.getStatusEnumIn();
        }
        List<String> effectiveStatuses = new ArrayList<>();
        if (statusEnumIn == null || statusEnumIn.isEmpty()) {
            effectiveStatuses.addAll(eligible);
        } else {
            // 与可见态求交，避免无关状态码穿透
            for (String code : statusEnumIn) {
                if (StringUtils.isEmpty(code)) {
                    continue;
                }
                if (eligible.contains(code)) {
                    effectiveStatuses.add(code);
                }
            }
        }
        if (effectiveStatuses.isEmpty()) {
            predicates.add(cb.disjunction());
            return;
        }
        List<String> inHospitalStatuses = new ArrayList<>();
        List<String> notInHospitalStatuses = new ArrayList<>();
        for (String code : effectiveStatuses) {
            if (this.isPrepayInHospitalStatus(code)) {
                inHospitalStatuses.add(code);
            } else {
                // COMPLETED / DISCHARGED / CANCELLED：只读查阅，须有预交金流水
                notInHospitalStatuses.add(code);
            }
        }
        if (notInHospitalStatuses.isEmpty()) {
            // 仅在院：可无流水（待收退办理）
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(inHospitalStatuses));
            return;
        }
        Predicate hasDepositHistory =
            this.existsHospitalDepositHistoryForEncounter(root, cb, criteriaQuery);
        if (inHospitalStatuses.isEmpty()) {
            // 仅不在院：无流水则卡片无查阅价值，硬过滤
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(notInHospitalStatuses));
            predicates.add(hasDepositHistory);
            return;
        }
        // 全部：在院四态直接入队；不在院须同时有预交金流水
        Predicate inHospitalBranch =
            root.get(FieldNameConstants.STATUS_ENUM).in(inHospitalStatuses);
        Predicate notInHospitalBranch = cb.and(
            root.get(FieldNameConstants.STATUS_ENUM).in(notInHospitalStatuses),
            hasDepositHistory);
        predicates.add(cb.or(inHospitalBranch, notInHospitalBranch));
    }

    /**
     * 是否预交金队列「在院」可收退态（四态，不要求已有流水）。
     *
     * @param statusCode 就诊状态 code
     * @return 在院四态为 true
     */
    private boolean isPrepayInHospitalStatus(String statusCode) {
        if (EncounterStatus.REGISTERED.getCode().equals(statusCode)) {
            return true;
        }
        if (EncounterStatus.IN_PROGRESS.getCode().equals(statusCode)) {
            return true;
        }
        if (EncounterStatus.ON_HOLD.getCode().equals(statusCode)) {
            return true;
        }
        if (EncounterStatus.PENDING.getCode().equals(statusCode)) {
            return true;
        }
        return false;
    }

    /**
     * 就诊下是否存在已收口住院预交金流水（与 IF-IP-PREP-02 展示口径对齐：HOSPITAL_DEPOSIT/历史 PRE_DEPOSIT + SUCCESS）。
     *
     * <p>DRAFT/CANCELLED 草稿不作查阅依据；收/退均 settle 为 SUCCESS（payment_enum 区分方向）。</p>
     *
     * @param root           就诊根
     * @param cb             CriteriaBuilder
     * @param criteriaQuery  外层查询
     * @return EXISTS 谓词
     */
    private Predicate existsHospitalDepositHistoryForEncounter(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery) {
        Subquery<Long> sq = criteriaQuery.subquery(Long.class);
        Root<PaymentReconciliation> paymentRoot = sq.from(PaymentReconciliation.class);
        jakarta.persistence.criteria.Path<PaymentKind> kindPath =
            paymentRoot.get(FieldNameConstants.KIND_ENUM);
        // 须逐条 equal 匹配 @Convert 枚举；历史 PRE_DEPOSIT 用 as(String)（同日结候选口径）
        Predicate hospitalDeposit = cb.equal(kindPath, PaymentKind.HOSPITAL_DEPOSIT);
        Predicate legacyPreDeposit = cb.equal(
            kindPath.as(String.class),
            InpatientDailySettlementConstants.LEGACY_HOSPITAL_DEPOSIT_KIND_CODE);
        sq.select(paymentRoot.get(FieldNameConstants.ENCOUNTER_ID));
        sq.where(cb.and(
            cb.equal(paymentRoot.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.or(hospitalDeposit, legacyPreDeposit),
            cb.equal(paymentRoot.get(FieldNameConstants.STATUS_ENUM), PaymentStatus.SUCCESS),
            cb.isFalse(paymentRoot.get(FieldNameConstants.IS_DELETED))));
        return cb.exists(sq);
    }

    /**
     * 入院日期过滤：优先闭区间 {@code admissionDateFrom}～{@code admissionDateTo}（含起止日），
     * 否则回退单日 {@code admissionDate}；按 {@code startTime} 落在 [日始, 次日) 收窄。
     *
     * @param predicates 谓词累加器
     * @param root       Encounter 根
     * @param cb         CriteriaBuilder
     * @param query      住院分页查询
     */
    private void applyAdmissionDate(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        InpatientEncounterPageQuery query) {
        LocalDate from = query.getAdmissionDateFrom();
        LocalDate to = query.getAdmissionDateTo();
        // 区间优先：起止任一有值即按下推（开放边界允许）
        if (from != null || to != null) {
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(
                        root.get(FieldNameConstants.START_TIME), from.atStartOfDay()));
            }
            if (to != null) {
                // 含止日整天：次日 0 点前
                predicates.add(cb.lessThan(
                        root.get(FieldNameConstants.START_TIME), to.plusDays(1).atStartOfDay()));
            }
            return;
        }
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
     * 出院日期过滤：闭区间 {@code dischargeDateFrom}～{@code dischargeDateTo}（含起止日），
     * 按 {@code endTime} 落在 [日始, 次日) 收窄；无出院时间的在院就诊不命中。
     *
     * @param predicates 谓词累加器
     * @param root       Encounter 根
     * @param cb         CriteriaBuilder
     * @param query      住院分页查询
     */
    private void applyDischargeDate(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        InpatientEncounterPageQuery query) {
        LocalDate from = query.getDischargeDateFrom();
        LocalDate to = query.getDischargeDateTo();
        // 未选出院日：不下推出院时间条件
        if (from == null && to == null) {
            return;
        }
        // 在院无 endTime：不能落入出院区间
        predicates.add(cb.isNotNull(root.get(FieldNameConstants.END_TIME)));
        if (from != null) {
            predicates.add(cb.greaterThanOrEqualTo(
                    root.get(FieldNameConstants.END_TIME), from.atStartOfDay()));
        }
        if (to != null) {
            // 含止日整天：次日 0 点前
            predicates.add(cb.lessThan(
                    root.get(FieldNameConstants.END_TIME), to.plusDays(1).atStartOfDay()));
        }
    }

    /**
     * 入院/登记列表状态：UI listStatus 映射为 encounter status；未传则不按状态收窄（查全部 IMP）。
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
            // 未传登记列表状态：不按 status_enum 收窄（产品：不选=全部）
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
            // 映射后无有效状态：与未传一致，不按状态收窄
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
