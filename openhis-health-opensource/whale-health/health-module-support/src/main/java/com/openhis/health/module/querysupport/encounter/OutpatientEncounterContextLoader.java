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

package com.openhis.health.module.querysupport.encounter;

import com.openhis.health.domain.administration.chargeitem.entity.ChargeItem;
import com.openhis.health.module.common.validator.CurrentManagedOrganizationIdsResolver;
import com.openhis.health.bridge.shared.diagnosis.BridgeEncounterDiagnosisSnapshotAssembler;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.entity.EncounterParticipant;
import com.openhis.health.domain.administration.encounter.queries.OutpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.OutpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.repository.EncounterParticipantRepository;
import com.openhis.health.domain.administration.encounter.repository.EncounterRepository;
import com.openhis.health.domain.administration.healthcareservice.entity.HealthcareService;
import com.openhis.health.domain.administration.healthcareservice.service.HealthcareServiceQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.repository.PatientRepository;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.medication.medicationdefinition.entity.MedicationDefinition;
import com.openhis.health.domain.medication.medicationrequest.entity.MedicationRequest;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.enums.ChargeItemStatus;
import com.openhis.health.domain.share.enums.EncounterActivityStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterParticipantRole;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.RequestStatus;
import com.openhis.health.domain.share.query.AbstractPageQuery;
import com.openhis.health.domain.share.utils.AgeCalculatorUtil;
import com.openhis.health.domain.share.utils.EnumUtil;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.workflow.devicerequest.entity.DeviceRequest;
import com.openhis.health.domain.workflow.servicerequest.entity.ServiceRequest;
import com.openhis.whale.core.application.exception.BusinessException;
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
 * 门诊就诊上下文读侧组装（由 {@link EncounterContextFacade} 委托，替代 {@code OutpatientInfoRMQueryService}）。
 *
 * <p>分页与机构收窄见 B.10.11（{@link OutpatientEncounterPageQuery#springPageIndex()}、
 * {@link CurrentManagedOrganizationIdsResolver#resolveOrganizationUnitIdForQuery()}）。护士站队列含跨 workflow/medication 子查询，
 * 须在应用层完成（迁移文档 10 §三）。</p>
 *
 * <p>DOC-CONFLICT：Criteria 暂经 {@link EncounterRepository}；{@code diagnosisJson} 为 Loader 内部快照，
 * HTTP 出参须 Mapper 结构化（B.6.2）。</p>
 */
@Component
@RequiredArgsConstructor
public class OutpatientEncounterContextLoader {

    private final EncounterRepository encounterRepository;
    private final EncounterParticipantRepository participantRepository;
    private final PatientRepository patientRepository;
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final HealthcareServiceQueryService healthcareServiceQueryService;
    private final BridgeEncounterDiagnosisSnapshotAssembler bridgeEncounterDiagnosisSnapshotAssembler;
    private final CurrentManagedOrganizationIdsResolver managedOrganizationIdsResolver;

    /**
     * 门诊就诊上下文分页（AMB，机构会话收窄）。
     *
     * @param query 分页与筛选
     * @return 门诊就诊上下文页
     */
    public Page<OutpatientEncounterContextQueryModel> pageOutpatientEncounterContexts(
        OutpatientEncounterPageQuery query) {
        OutpatientEncounterPageQuery effective = query;
        if (effective == null) {
            effective = new OutpatientEncounterPageQuery();
        }
        int pageIndex = effective.springPageIndex();
        int pageSize = effective.getBoundedPageSize();
        Sort sort = this.resolveOutpatientListSort(effective);
        Pageable pageable = PageRequest.of(pageIndex, pageSize, sort);
        Specification<Encounter> specification = this.buildSpecification(effective);
        Page<Encounter> page = encounterRepository.findAll(specification, pageable);
        List<OutpatientEncounterContextQueryModel> content = this.toContextModels(page.getContent());
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    /**
     * 门诊护士站执行队列分页（当日在诊、已开立医嘱等护士站专用谓词）。
     *
     * @param query 分页参数
     * @return 门诊就诊上下文页
     */
    public Page<OutpatientEncounterContextQueryModel> pageNurseStationOutpatientQueue(AbstractPageQuery query) {
        AbstractPageQuery effective = query;
        if (effective == null) {
            effective = new AbstractPageQuery();
        }
        int pageIndex = effective.springPageIndex();
        int pageSize = 20;
        if (effective.getPageSize() != null && effective.getPageSize() > 0) {
            pageSize = effective.getPageSize();
        }
        if (pageSize > 100) {
            pageSize = 100;
        }
        Specification<Encounter> specification = this.buildNurseStationSpecification(effective);
        Sort sort = Sort.by(
            Sort.Order.desc(FieldNameConstants.START_TIME),
            Sort.Order.desc(FieldNameConstants.ID));
        Pageable pageable = PageRequest.of(pageIndex, pageSize, sort);
        Page<Encounter> page = encounterRepository.findAll(specification, pageable);
        List<OutpatientEncounterContextQueryModel> content = this.toContextModels(page.getContent());
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    /**
     * 按就诊主键取单条门诊上下文。
     *
     * @param encounterId 就诊主键
     * @return 上下文；非门诊类别时 empty
     */
    public Optional<OutpatientEncounterContextQueryModel> getOutpatientContextByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return Optional.empty();
        }
        return encounterRepository.findById(encounterId)
            .filter(this::isOutpatientEncounter)
            .map(encounter -> this.toContextModels(List.of(encounter)).get(0));
    }

    /**
     * 按调用方顺序批量装载门诊上下文（过滤非门诊行，缺失 id 跳过）。
     *
     * @param encounterIdsOrdered 就诊主键有序列表
     * @return 有序上下文列表
     */
    public List<OutpatientEncounterContextQueryModel> listOutpatientContextsByEncounterIdsOrdered(
        List<Long> encounterIdsOrdered) {
        if (encounterIdsOrdered == null || encounterIdsOrdered.isEmpty()) {
            return new ArrayList<>();
        }
        List<Encounter> loaded = encounterRepository.findAllByIdIn(encounterIdsOrdered);
        List<Encounter> outpatientRows = new ArrayList<>();
        for (Encounter row : loaded) {
            if (this.isOutpatientEncounter(row)) {
                outpatientRows.add(row);
            }
        }
        Map<Long, OutpatientEncounterContextQueryModel> byEncounterId = new HashMap<>(outpatientRows.size() * 2);
        for (OutpatientEncounterContextQueryModel model : this.toContextModels(outpatientRows)) {
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
     * 按就诊主键强制取得门诊上下文，不存在时抛业务异常。
     *
     * @param encounterId 就诊主键
     * @return 上下文
     */
    public OutpatientEncounterContextQueryModel mustGetOutpatientContextByEncounterId(Long encounterId) {
        return this.getOutpatientContextByEncounterId(encounterId)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.ENCOUNTER_NOT_FOUND,
                "门诊就诊上下文不存在"));
    }

    /** 门诊口径：AMB 且未删。 */
    private boolean isOutpatientEncounter(Encounter encounter) {
        if (encounter == null || Boolean.TRUE.equals(encounter.getIsDeleted())) {
            return false;
        }
        return EncounterClass.AMB.getCode().equals(encounter.getClassEnum());
    }

    /**
     * 门诊类别谓词：AMB（禁止类内 List 码表，B.4）。
     */
    private Predicate outpatientClassEnumPredicate(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb) {
        return cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.AMB.getCode());
    }

    /**
     * 门诊上下文列表 Criteria（机构、状态、医生参与者、患者关键字、时段）。
     */
    private Specification<Encounter> buildSpecification(OutpatientEncounterPageQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(this.outpatientClassEnumPredicate(root, cb));
            Long sessionOrganizationUnitId = managedOrganizationIdsResolver.resolveOrganizationUnitIdForQuery();
            if (sessionOrganizationUnitId != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), sessionOrganizationUnitId));
            } else if (managedOrganizationIdsResolver.isOrganizationScopeEnforcementActive()) {
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
                    // 医生站 IF-DOR-01：按就诊状态与参与者角色组合判定当前医师是否可见（BR-DOR-009）。
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

    /**
     * 门诊护士站队列：关键字/时段 + 存在可处置医嘱或退费相关收费事实（不按当前登录科室收窄）。
     */
    private Specification<Encounter> buildNurseStationSpecification(AbstractPageQuery query) {
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

    /**
     * 门诊医生站列表可见性（BR-DOR-009）：诊毕科室共享；待诊按挂号医生；在诊/暂离按活动接诊医生。
     *
     * @param root    就诊根
     * @param cb      CriteriaBuilder
     * @param criteriaQuery 所属查询
     * @param query   分页入参（含状态范围与当前登录医师）
     * @return 可见性谓词
     */
    private Predicate buildDoctorStationViewerVisibilityPredicate(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        OutpatientEncounterPageQuery query) {
        Long viewerPractitionerId = query.getDoctorStationViewerPractitionerId();
        List<String> statusScope = this.resolveDoctorStationStatusScope(query);
        List<Predicate> visibilityOr = new ArrayList<>();
        // 诊毕：本科室内所有医生均可见，不再按参与者收窄。
        if (this.statusScopeIncludes(statusScope, EncounterStatus.DISCHARGED.getCode())) {
            visibilityOr.add(cb.equal(
                root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.DISCHARGED.getCode()));
        }
        // 待诊/已分诊：未指定医生则科室共享；有挂号医生（或历史误写 ADMITTER）则仅匹配医师可见。
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
        // 在诊/暂离：仅活动接诊医生（ADMITTER）可见。
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

    /**
     * 解析医生站可见性判定时参与的状态集合（与 statusEnum / statusEnumIn 一致）。
     */
    private List<String> resolveDoctorStationStatusScope(OutpatientEncounterPageQuery query) {
        if (query.getStatusEnumIn() != null && !query.getStatusEnumIn().isEmpty()) {
            return query.getStatusEnumIn();
        }
        if (StringUtils.isNotBlank(query.getStatusEnum())) {
            return List.of(query.getStatusEnum().trim());
        }
        return List.of();
    }

    /** 状态范围是否包含指定编码。 */
    private boolean statusScopeIncludes(List<String> statusScope, String statusCode) {
        for (String scoped : statusScope) {
            if (statusCode.equals(scoped)) {
                return true;
            }
        }
        return false;
    }

    /** 从状态范围中保留允许出现在本分支的编码。 */
    private List<String> filterStatusScope(List<String> statusScope, String... allowedCodes) {
        List<String> filtered = new ArrayList<>();
        for (String allowed : allowedCodes) {
            if (this.statusScopeIncludes(statusScope, allowed)) {
                filtered.add(allowed);
            }
        }
        return filtered;
    }

    /**
     * 存在活动参与者：指定角色且 practitionerId 匹配。
     */
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

    /** 不存在活动参与者：指定角色（用于「挂到科室、未指定医生」的待诊可见性）。 */
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

    /**
     * 存在活动状态且角色为接诊/主治/挂号医生的参与者。
     */
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

    /**
     * 患者姓名/拼音/五笔关键字 EXISTS（B.2.1 不区分大小写）。
     */
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
     * 护士站门诊队列：存在可处置医嘱（诊疗/耗材/注射皮试）或存在退费中/已退费收费明细（资金退费后仍须可见）。
     */
    private Predicate buildNurseStationRequestExistsAny(
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> owningQuery) {
        // 诊疗/耗材：已完成且非退单
        Subquery<Long> serviceSq = owningQuery.subquery(Long.class);
        Root<ServiceRequest> sr = serviceSq.from(ServiceRequest.class);
        serviceSq.select(sr.get(FieldNameConstants.ID));
        serviceSq.where(cb.and(
            cb.equal(sr.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.equal(sr.get(FieldNameConstants.STATUS_ENUM), RequestStatus.COMPLETED.getCode()),
            cb.isNull(sr.get(FieldNameConstants.REFUND_SERVICE_ID)),
            cb.isFalse(sr.get(FieldNameConstants.IS_DELETED))));

        // 耗材：已完成且非退单
        Subquery<Long> deviceSq = owningQuery.subquery(Long.class);
        Root<DeviceRequest> dr = deviceSq.from(DeviceRequest.class);
        deviceSq.select(dr.get(FieldNameConstants.ID));
        deviceSq.where(cb.and(
            cb.equal(dr.get(FieldNameConstants.ENCOUNTER_ID), root.get(FieldNameConstants.ID)),
            cb.equal(dr.get(FieldNameConstants.STATUS_ENUM), RequestStatus.COMPLETED.getCode()),
            cb.isNull(dr.get(FieldNameConstants.REFUND_DEVICE_ID)),
            cb.isFalse(dr.get(FieldNameConstants.IS_DELETED))));

        // 药品：已完成、非退药，且为注射或皮试
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
     * 就诊下存在退费中/已退费收费明细（门诊资金退费后，护士站队列仍须展示该患者）。
     */
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

    /** 门诊列表排序：多状态/接诊中/预约等分支与 AS 展示一致。 */
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

    /**
     * 批量装配门诊上下文行（患者/科室/医生/诊断快照）；诊断 JSON 仅供 Mapper 结构化（B.6.2）。
     */
    private List<OutpatientEncounterContextQueryModel> toContextModels(List<Encounter> encounters) {
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
        Map<Long, Patient> patientById = new HashMap<>();
        if (!patientIds.isEmpty()) {
            for (Patient patient : patientRepository.findAllByIdIn(patientIds)) {
                if (patient.getId() != null) {
                    patientById.put(patient.getId(), patient);
                }
            }
        }
        Map<Long, String> organizationNameById = organizationQueryService.mapNamesByIds(organizationIds);
        Map<Long, HealthcareService> healthcareServiceById =
            this.healthcareServiceQueryService.loadHealthcareServiceMapByIds(serviceTypeIds);

        String activeStatus = EncounterActivityStatus.ACTIVE.getCode();
        Map<Long, List<EncounterParticipant>> participantsByEncounterId = new HashMap<>();
        if (!encounterIds.isEmpty()) {
            for (EncounterParticipant participant :
                participantRepository.findAllByEncounterIdInAndStatusEnum(encounterIds, activeStatus)) {
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
                if (practitioner.getId() != null && StringUtils.isNotBlank(practitioner.getName())) {
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
                if (patient.getBirthDate() != null) {
                    model.setBirthDate(patient.getBirthDate().atStartOfDay());
                    Date birthDate = Date.from(
                        patient.getBirthDate().atStartOfDay(ZoneId.systemDefault()).toInstant());
                    String ageText = AgeCalculatorUtil.getAge(birthDate);
                    if (StringUtils.isNotBlank(ageText)) {
                        model.setAgeText(ageText);
                    }
                }
            }
            this.applyOutpatientDoctor(
                model,
                participantsByEncounterId.get(encounter.getId()),
                practitionerNameById);
            BridgeEncounterDiagnosisSnapshotAssembler.DiagnosisSnapshot diagnosisSnapshot =
                this.bridgeEncounterDiagnosisSnapshotAssembler.buildSnapshot(encounter.getId());
            model.setDiagnosisJson(diagnosisSnapshot.diagnosisJson());
            models.add(model);
        }
        return models;
    }

    /** 回填接诊/主治/挂号医生展示字段。 */
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
}
