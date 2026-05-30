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

import com.openhis.health.domain.administration.chargeitem.service.ChargeItemQueryService;
import com.openhis.health.domain.administration.encounter.entity.Encounter;
import com.openhis.health.domain.administration.encounter.entity.EncounterLocation;
import com.openhis.health.domain.administration.encounter.entity.EncounterParticipant;
import com.openhis.health.domain.administration.encounter.queries.InpatientEncounterPageQuery;
import com.openhis.health.domain.administration.encounter.queries.model.InpatientEncounterContextQueryModel;
import com.openhis.health.domain.administration.encounter.repository.EncounterLocationRepository;
import com.openhis.health.domain.administration.encounter.repository.EncounterParticipantRepository;
import com.openhis.health.domain.administration.encounter.repository.EncounterRepository;
import com.openhis.health.domain.administration.location.entity.Location;
import com.openhis.health.domain.administration.location.repository.LocationRepository;
import com.openhis.health.domain.administration.location.service.LocationQueryService;
import com.openhis.health.domain.administration.organization.service.OrganizationQueryService;
import com.openhis.health.domain.administration.patient.entity.Patient;
import com.openhis.health.domain.administration.patient.repository.PatientRepository;
import com.openhis.health.domain.administration.practitioner.entity.Practitioner;
import com.openhis.health.domain.administration.practitioner.service.PractitionerQueryService;
import com.openhis.health.domain.custom.encounterhistory.entity.EncounterHistory;
import com.openhis.health.domain.custom.encounterhistory.repository.EncounterHistoryRepository;
import com.openhis.health.domain.share.constants.CustomConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.InpatientRegistrationConstants;
import com.openhis.health.domain.share.constants.InpatientSettleQueueTab;
import com.openhis.health.domain.share.enums.EncounterActivityStatus;
import com.openhis.health.domain.share.enums.EncounterClass;
import com.openhis.health.domain.share.enums.EncounterParticipantRole;
import com.openhis.health.domain.share.enums.EncounterStatus;
import com.openhis.health.domain.share.enums.LocationForm;
import com.openhis.health.domain.share.utils.AgeCalculatorUtil;
import com.openhis.health.domain.share.utils.InpatientEncounterStatusDisplayUtil;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.whale.core.application.exception.BusinessException;
import com.openhis.whale.core.infrastructure.security.ICurrentUser;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 住院就诊上下文读侧组装（由 {@link EncounterContextFacade} 委托，替代 {@code InpatientInfoRMQueryService}）。
 *
 * <p>分页/排序/机构收窄见 B.10.11；病区床位范围经 {@link LocationQueryService} 与就诊位置子表 EXISTS 收敛。</p>
 *
 * <p>DOC-CONFLICT：Criteria 暂经 {@link EncounterRepository} 等仓储；{@code diagnosisJson} 为 Loader 内部字段，
 * 对外须结构化（B.6.2、迁移文档 10 P4）。</p>
 */
@Component
@RequiredArgsConstructor
public class InpatientEncounterContextLoader {

    private final EncounterRepository encounterRepository;
    private final EncounterLocationRepository encounterLocationRepository;
    private final EncounterParticipantRepository participantRepository;
    private final PatientRepository patientRepository;
    private final LocationRepository locationRepository;
    private final LocationQueryService locationQueryService;
    private final OrganizationQueryService organizationQueryService;
    private final PractitionerQueryService practitionerQueryService;
    private final ChargeItemQueryService chargeItemQueryService;
    private final EncounterDiagnosisSnapshotAssembler encounterDiagnosisSnapshotAssembler;
    private final EncounterHistoryRepository encounterHistoryRepository;

    /** 当前会话机构收窄（B.10.11，禁止经 PageQuery 传 organizationId）。 */
    private final ICurrentUser currentUser;

    /**
     * 住院登记队列分页：默认 listStatus 为在院登记列表口径。
     *
     * @param query 分页与地理/关键字筛选
     * @return 住院就诊上下文页
     */
    public Page<InpatientEncounterContextQueryModel> pageRegQueue(InpatientEncounterPageQuery query) {
        InpatientEncounterPageQuery effective = query;
        if (effective == null) {
            effective = new InpatientEncounterPageQuery();
        }
        if (StringUtils.isBlank(effective.getRegistrationListStatus())) {
            effective.setRegistrationListStatus(InpatientRegistrationConstants.DEFAULT_LIST_STATUS);
        }
        return this.pageInpatientEncounterContexts(effective);
    }

    /**
     * 住院预交金患者队列：清床离院（{@code DISCHARGED}）前均在列，含已结算待清床（{@code COMPLETED}）。
     *
     * <p>收退门禁由 {@code InpatientPrepayAppService} 另行校验，与本读侧可见范围分离。</p>
     *
     * @param query 分页与筛选
     * @return 住院就诊上下文页
     */
    public Page<InpatientEncounterContextQueryModel> pagePrepayQueue(InpatientEncounterPageQuery query) {
        InpatientEncounterPageQuery effective = query;
        if (effective == null) {
            effective = new InpatientEncounterPageQuery();
        }
        effective.setPrepayEligibleOnly(Boolean.TRUE);
        return this.pageInpatientEncounterContexts(effective);
    }

    /**
     * 住院护士站在院队列分页（含任务筛选、地理收窄等护士站专用谓词）。
     *
     * @param query 分页与筛选
     * @return 住院就诊上下文页
     */
    public Page<InpatientEncounterContextQueryModel> pageNurseStationQueue(InpatientEncounterPageQuery query) {
        InpatientEncounterPageQuery effective = query;
        if (effective == null) {
            effective = new InpatientEncounterPageQuery();
        }
        effective.setNurseStationQueue(Boolean.TRUE);
        return this.pageInpatientEncounterContexts(effective);
    }

    /**
     * 住院医生站在院队列分页。
     *
     * @param query 分页与筛选
     * @return 住院就诊上下文页
     */
    public Page<InpatientEncounterContextQueryModel> pageDoctorStationQueue(InpatientEncounterPageQuery query) {
        InpatientEncounterPageQuery effective = query;
        if (effective == null) {
            effective = new InpatientEncounterPageQuery();
        }
        effective.setDoctorStationQueue(Boolean.TRUE);
        return this.pageInpatientEncounterContexts(effective);
    }

    /**
     * 住院结算队列：待结算页签时先按待结算收费项收窄 encounterId 集合。
     *
     * @param query 分页、页签与筛选
     * @return 住院就诊上下文页
     */
    public Page<InpatientEncounterContextQueryModel> pageSettleQueue(InpatientEncounterPageQuery query) {
        InpatientEncounterPageQuery effective = query;
        if (effective == null) {
            effective = new InpatientEncounterPageQuery();
        }
        effective.setSettleQueue(Boolean.TRUE);
        if (StringUtils.isBlank(effective.getSettleQueueTab())) {
            effective.setSettleQueueTab(InpatientSettleQueueTab.PENDING_SETTLE);
        }
        if (InpatientSettleQueueTab.PENDING_SETTLE.equals(effective.getSettleQueueTab())) {
            List<Long> encounterIds = chargeItemQueryService.listEncounterIdsWithPendingSettlementCharges();
            if (encounterIds.isEmpty()) {
                Pageable pageable = this.resolvePageable(effective);
                return Page.empty(pageable);
            }
            effective.setEncounterIdsIn(encounterIds);
        }
        return this.pageInpatientEncounterContexts(effective);
    }

    /**
     * 通用住院就诊上下文分页（机构由 {@link ICurrentUser} 收窄）。
     *
     * @param query 分页与筛选
     * @return 住院就诊上下文页
     */
    public Page<InpatientEncounterContextQueryModel> pageInpatientEncounterContexts(InpatientEncounterPageQuery query) {
        Pageable pageable = this.resolvePageable(query);
        Specification<Encounter> specification = this.buildSpecification(query);
        Page<Encounter> page = encounterRepository.findAll(specification, pageable);
        List<InpatientEncounterContextQueryModel> content = this.toContextModels(page.getContent());
        return new PageImpl<>(content, pageable, page.getTotalElements());
    }

    /**
     * 住院就诊上下文列表（无分页，按创建时间倒序）。
     *
     * @param query 筛选条件
     * @return 上下文行列表
     */
    public List<InpatientEncounterContextQueryModel> listInpatientEncounterContexts(InpatientEncounterPageQuery query) {
        Specification<Encounter> specification = this.buildSpecification(query);
        Sort sort = Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT));
        List<Encounter> rows = encounterRepository.findAll(specification, sort);
        return this.toContextModels(rows);
    }

    /**
     * 按就诊主键取单条住院上下文（非 IMP 类别不返回）。
     *
     * @param encounterId 就诊主键
     * @return 上下文；不存在或非住院时 empty
     */
    public Optional<InpatientEncounterContextQueryModel> getInpatientContextByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return Optional.empty();
        }
        return encounterRepository.findByIdAndClassEnum(encounterId, EncounterClass.IMP.getCode())
            .map(encounter -> this.toContextModels(List.of(encounter)).get(0));
    }

    /**
     * 按调用方给定顺序批量装载住院上下文（缺失 id 跳过，不抛错）。
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
            encounterRepository.findAllByIdInAndClassEnum(encounterIdsOrdered, EncounterClass.IMP.getCode());
        Map<Long, InpatientEncounterContextQueryModel> byEncounterId = new HashMap<>(loaded.size() * 2);
        for (InpatientEncounterContextQueryModel model : this.toContextModels(loaded)) {
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

    /**
     * 按就诊主键强制取得住院上下文，不存在时抛 {@link ErrorCodeConstants#ENCOUNTER_NOT_FOUND}。
     *
     * @param encounterId 就诊主键
     * @return 上下文
     */
    public InpatientEncounterContextQueryModel mustGetInpatientContextByEncounterId(Long encounterId) {
        return this.getInpatientContextByEncounterId(encounterId)
            .orElseThrow(() -> new BusinessException(
                ErrorCodeConstants.ENCOUNTER_NOT_FOUND,
                "住院就诊上下文不存在"));
    }

    /** 住院列表分页壳（B.10.11：{@code springPageIndex} + {@code getBoundedPageSize}）。 */
    private Pageable resolvePageable(InpatientEncounterPageQuery query) {
        int pageNo = 0;
        int pageSize = 20;
        if (query != null) {
            pageNo = query.springPageIndex();
            pageSize = query.getBoundedPageSize();
        }
        return PageRequest.of(pageNo, pageSize, this.resolveSort(query));
    }

    /** 按队列类型（医生站/护士站/预交金/结算）选择排序字段。 */
    private Sort resolveSort(InpatientEncounterPageQuery query) {
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
     * 住院上下文主 Criteria：按 {@code doctorStationQueue} / {@code nurseStationQueue} / {@code settleQueue} 等分支收窄。
     */
    private Specification<Encounter> buildSpecification(InpatientEncounterPageQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.IMP.getCode()));
            if (query == null) {
                return cb.and(predicates.toArray(new Predicate[0]));
            }
            if (Boolean.TRUE.equals(query.getDoctorStationQueue())) {
                this.applyDoctorStationVisibleStatus(predicates, root, cb);
                this.applyDoctorStationOrganizationScope(predicates, root, cb);
            } else if (Boolean.TRUE.equals(query.getNurseStationQueue())) {
                this.applyNurseStationVisibleStatus(predicates, root, cb);
                this.applyNurseStationGeographicScope(predicates, root, cb, criteriaQuery, query);
                this.applyEncounterIdsIn(predicates, root, cb, query.getEncounterIdsIn());
            } else if (Boolean.TRUE.equals(query.getSettleQueue())) {
                this.applySettleQueueFilter(predicates, root, cb, query);
                this.applyEncounterIdsIn(predicates, root, cb, query.getEncounterIdsIn());
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

    /** 医生站：在院/留观状态子集。 */
    private void applyDoctorStationVisibleStatus(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb) {
        predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(
            EncounterStatus.IN_PROGRESS.getCode(),
            EncounterStatus.ON_HOLD.getCode()));
    }

    /**
     * 医生站队列：按当前登录科室收窄就诊机构（会话机构，非 HTTP 入参）。
     */
    private void applyDoctorStationOrganizationScope(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb) {
        Long organizationId = this.currentUser.getCurrentOrganizationUnitId();
        if (organizationId == null) {
            return;
        }
        predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId));
    }

    /** 护士站：登记至出院前多状态子集。 */
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
     * 护士站地理范围：病房下全部床位 / 单病区 / 管辖病区列表（含已登记未分床）。
     */
    private void applyNurseStationGeographicScope(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        InpatientEncounterPageQuery query) {
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
        if (managedWardIds != null && !managedWardIds.isEmpty()) {
            List<Long> wardIds = new ArrayList<>();
            for (Long wardId : managedWardIds) {
                if (wardId != null) {
                    wardIds.add(wardId);
                }
            }
            if (!wardIds.isEmpty()) {
                Predicate wardInManaged = this.existsActiveWardIn(root, cb, criteriaQuery, wardIds);
                Predicate registeredWithoutBed = cb.and(
                    cb.not(this.existsActiveBed(root, cb, criteriaQuery)),
                    cb.equal(root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.REGISTERED.getCode()));
                predicates.add(cb.or(wardInManaged, registeredWithoutBed));
            }
        }
    }

    /** 外部分页后传入的 encounterId 白名单（护士站/结算队列）。 */
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

    /** 住院号 / 患者姓名 / 活动床位名 OR 模糊（B.2.1）。 */
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

    /** 患者姓名子串 EXISTS。 */
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

    /** 当前活动床位名称模糊 EXISTS。 */
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

    /** 结算队列页签：已结算 / 待结算（排除取消与计划）。 */
    private void applySettleQueueFilter(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        InpatientEncounterPageQuery query) {
        String tab = query.getSettleQueueTab();
        if (InpatientSettleQueueTab.SETTLED.equals(tab)) {
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.COMPLETED.getCode()));
            return;
        }
        predicates.add(cb.notEqual(root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.CANCELLED.getCode()));
        predicates.add(cb.notEqual(root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.PLANNED.getCode()));
    }

    /**
     * 预交金队列可见态：自登记至清床前（与护士站在院口径一致，含 {@code COMPLETED} 待清床）。
     *
     * <p>不含 {@code DISCHARGED} / {@code PLANNED} / {@code CANCELLED}；收退写路径仍以 App 层四态白名单为准。</p>
     */
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

    /** 入院日过滤：按 {@code startTime} 落在当日。 */
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

    /** 入院登记列表虚拟状态 → 就诊 status_enum。 */
    private void applyRegistrationListStatus(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        InpatientEncounterPageQuery query) {
        String listStatus = query.getRegistrationListStatus();
        if (StringUtils.isBlank(listStatus)) {
            return;
        }
        if (InpatientRegistrationConstants.LIST_STATUS_PLANNED.equals(listStatus)) {
            predicates.add(cb.equal(
                root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.PLANNED.getCode()));
            return;
        }
        if (InpatientRegistrationConstants.LIST_STATUS_WAIT_ADMISSION.equals(listStatus)) {
            predicates.add(cb.equal(
                root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.REGISTERED.getCode()));
            return;
        }
        if (InpatientRegistrationConstants.LIST_STATUS_ADMITTED.equals(listStatus)) {
            predicates.add(cb.equal(
                root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.IN_PROGRESS.getCode()));
            return;
        }
        throw new BusinessException(
            ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT,
            "registrationListStatus 无效: " + listStatus);
    }

    /** 登记列表按病区收窄。 */
    private void applyWardScope(
        List<Predicate> predicates,
        Root<Encounter> root,
        jakarta.persistence.criteria.CriteriaBuilder cb,
        jakarta.persistence.criteria.CriteriaQuery<?> criteriaQuery,
        Long wardId) {
        predicates.add(this.buildWardScopePredicate(root, cb, criteriaQuery, wardId));
    }

    /** 病区直接占床 OR 病区下任一活动床位。 */
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

    /** 活动轨迹落在指定病区库位。 */
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

    /** 是否存在任意活动床位轨迹。 */
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

    /** 活动床位落在给定床位 id 集合内。 */
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

    /** 活动轨迹落在多个病区之一（含下属床位）。 */
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

    /** 病区 → 病房 → 床位 id 列表（登记/护士站地理筛选用）。 */
    private List<Long> collectBedIdsUnderWard(Long wardId) {
        List<Long> bedIds = new ArrayList<>();
        if (wardId == null) {
            return bedIds;
        }
        List<Location> rooms = locationRepository.findAllByParentIdAndFormEnum(
            wardId, LocationForm.HOUSE.getCode());
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

    /**
     * 批量装配住院上下文（患者/科室/床位/参与者/诊断快照/费用摘要字段）。
     */
    private List<InpatientEncounterContextQueryModel> toContextModels(List<Encounter> encounters) {
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
        Map<Long, Patient> patientById = new HashMap<>();
        if (!patientIds.isEmpty()) {
            for (Patient patient : patientRepository.findAllByIdIn(patientIds)) {
                if (patient.getId() != null) {
                    patientById.put(patient.getId(), patient);
                }
            }
        }
        Map<Long, String> organizationNameById = organizationQueryService.mapNamesByIds(organizationIds);

        String activeStatus = EncounterActivityStatus.ACTIVE.getCode();
        Map<Long, List<EncounterLocation>> locationsByEncounterId = new HashMap<>();
        if (!encounterIds.isEmpty()) {
            for (EncounterLocation row : encounterLocationRepository.findAllByEncounterIdInAndStatusEnum(
                encounterIds, activeStatus)) {
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
        Map<Long, Location> locationById = new HashMap<>();
        if (!locationIds.isEmpty()) {
            for (Location location : locationRepository.findAllById(locationIds)) {
                if (location.getId() != null) {
                    locationById.put(location.getId(), location);
                }
            }
        }
        Set<Long> wardParentIds = new LinkedHashSet<>();
        for (Location location : locationById.values()) {
            if (location.getParentId() != null
                && LocationForm.BED.getCode().equals(location.getFormEnum())) {
                wardParentIds.add(location.getParentId());
            }
        }
        for (Long parentId : wardParentIds) {
            if (!locationById.containsKey(parentId)) {
                locationIds.add(parentId);
            }
        }
        if (!wardParentIds.isEmpty()) {
            for (Location parent : locationRepository.findAllById(wardParentIds)) {
                if (parent.getId() != null) {
                    locationById.put(parent.getId(), parent);
                }
            }
        }

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
                    if (StringUtils.isNotBlank(ageText)) {
                        model.setAgeText(ageText);
                    }
                }
            }
            this.applyActiveLocation(model, locationsByEncounterId.get(encounter.getId()), locationById);
            this.applyActiveParticipants(
                model,
                participantsByEncounterId.get(encounter.getId()),
                practitionerNameById);
            EncounterDiagnosisSnapshotAssembler.DiagnosisSnapshot diagnosisSnapshot =
                this.encounterDiagnosisSnapshotAssembler.buildSnapshot(encounter.getId());
            model.setDiagnosisJson(diagnosisSnapshot.diagnosisJson());
            models.add(model);
        }
        // ON_HOLD 待转科/待出院分流列（替代 InpatientInfo 投影的 transfer_org_id、discharge_type_enum）。
        this.applyInpatientHoldRoutingFields(models, encounters);
        for (InpatientEncounterContextQueryModel model : models) {
            model.setStatusEnumText(InpatientEncounterStatusDisplayUtil.resolveDisplayText(
                model.getStatusEnum(),
                model.getTransferOrgId(),
                model.getDischargeTypeEnum()));
        }
        return models;
    }

    /**
     * 回填 {@code ON_HOLD} 转科目标与出院类型（DM-ADM-002 §2.3.2；替代废止的 InpatientInfo 读模型列）。
     *
     * @param models     已装配的住院上下文行
     * @param encounters 同源就诊主档
     */
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
        for (EncounterHistory row : encounterHistoryRepository.findAllByEncounterIdIn(onHoldEncounterIds)) {
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
            Long transferTargetOrgId = this.resolveLatestTransferTargetOrganizationId(histories);
            if (transferTargetOrgId != null) {
                model.setTransferOrgId(transferTargetOrgId);
                continue;
            }
            Encounter encounter = encounterById.get(encounterId);
            if (encounter != null && StringUtils.isNotBlank(encounter.getOutWayCode())) {
                model.setDischargeTypeEnum(CustomConstants.EncounterHistoryMilestoneName.DISCHARGE);
                continue;
            }
            if (this.hasDischargeMilestone(histories)) {
                model.setDischargeTypeEnum(CustomConstants.EncounterHistoryMilestoneName.DISCHARGE);
            }
        }
    }

    /**
     * 取最近一次转科里程碑上的目标科室（待转科分流）。
     *
     * @param histories 该就诊里程碑列表
     * @return 目标科室主键；无转科里程碑时为 null
     */
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

    /**
     * 是否存在出院类里程碑（医生站出院医嘱等路径）。
     *
     * @param histories 该就诊里程碑列表
     * @return true 表示已记录出院里程碑
     */
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

    /** 回填病区/床位/房间展示字段（活动轨迹解析）。 */
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

    /** 回填主治/责任护士等参与者展示字段。 */
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
