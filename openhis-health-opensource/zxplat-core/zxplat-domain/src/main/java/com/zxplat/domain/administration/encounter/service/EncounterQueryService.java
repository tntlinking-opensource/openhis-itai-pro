/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.administration.encounter.service;

import com.zxplat.domain.share.constants.EncounterConstants;
import com.zxplat.domain.share.constants.HealthcareServiceConstants;
import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.administration.encounter.entity.Encounter;
import com.zxplat.domain.administration.encounter.entity.EncounterDiagnosis;
import com.zxplat.domain.administration.encounter.entity.EncounterLocation;
import com.zxplat.domain.administration.encounter.entity.EncounterParticipant;
import com.zxplat.domain.administration.encounter.queries.EncounterProjectionQuery;
import com.zxplat.domain.administration.encounter.queries.InpatientInOutDetailReportPageQuery;
import com.zxplat.domain.administration.encounter.queries.HistoricalFollowUpEligibilityQuery;
import com.zxplat.domain.administration.encounter.queries.MedicalTechWorkstationQueueQuery;
import com.zxplat.domain.administration.encounter.queries.OutpatientDuplicateCheckQuery;
import com.zxplat.domain.administration.encounter.queries.OutpatientRegistrationReportPageQuery;
import com.zxplat.domain.administration.encounter.queries.model.InpatientAutoRollEncounterQueryModel;
import com.zxplat.domain.administration.encounter.queries.model.EncounterCompletionCheckQueryModel;
import com.zxplat.domain.administration.encounter.queries.model.EncounterDetailQueryModel;
import com.zxplat.domain.administration.encounter.queries.model.EncounterDiagnosisQueryModel;
import com.zxplat.domain.administration.encounter.queries.model.EncounterLocationInfoQueryModel;
import com.zxplat.domain.administration.encounter.queries.model.EncounterLocationQueryModel;
import com.zxplat.domain.administration.encounter.queries.model.EncounterParticipantQueryModel;
import com.zxplat.domain.administration.encounter.queries.model.EncounterProjectionQueryModel;
import com.zxplat.domain.administration.encounter.queries.model.EncounterSimpleQueryModel;
import com.zxplat.domain.administration.encounter.queries.model.InpatientInOutDetailPersonTimesQueryModel;
import com.zxplat.domain.administration.encounter.queries.model.InpatientInOutDetailReportLineQueryModel;
import com.zxplat.domain.administration.encounter.queries.model.OpenInpatientLineQuery;
import com.zxplat.domain.administration.encounter.queries.model.OutpatientDuplicateCheckQueryModel;
import com.zxplat.domain.administration.encounter.queries.model.OutpatientRegistrationDetailStatsQueryModel;
import com.zxplat.domain.administration.encounter.queries.model.OutpatientRegistrationReportLineQueryModel;
import com.zxplat.domain.administration.encounter.queries.model.OutpatientRegistrationReportSummaryRow;
import com.zxplat.domain.administration.encounter.queries.model.PractitionerWorkloadEncounterContextQueryModel;
import com.zxplat.domain.share.constants.InpatientInOutDetailReportConstants;
import com.zxplat.domain.share.constants.OutpatientRegistrationReportConstants;
import com.zxplat.domain.administration.encounter.repository.EncounterDiagnosisRepository;
import com.zxplat.domain.administration.encounter.repository.EncounterLocationRepository;
import com.zxplat.domain.administration.encounter.repository.EncounterParticipantRepository;
import com.zxplat.domain.administration.encounter.repository.EncounterRepository;
import com.zxplat.domain.share.constants.EncounterDiagnosisBindingConstants;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.utils.EncounterDiagnosisIdsSupport;
import com.zxplat.domain.share.utils.IdInQueryChunkSupport;
import com.zxplat.domain.share.utils.KeywordExactFirstOrderSupport;
import com.zxplat.domain.share.utils.SearchCodeUtil;
import com.zxplat.domain.share.enums.EncounterActivityStatus;
import com.zxplat.domain.share.enums.EncounterClass;
import com.zxplat.domain.share.enums.EncounterParticipantRole;
import com.zxplat.domain.share.enums.EncounterStatus;
import com.zxplat.domain.share.enums.LocationForm;
import com.zxplat.domain.share.utils.EnumUtil;
import com.zxplat.framework.core.application.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 就诊读侧查询服务。
 *
 * <p>职责：加载就诊主档与参与者/位置/诊断等子集查询模型，提供分页投影、完诊校验与门诊重复挂号校验等只读能力。</p>
 *
 * <p>边界：不写库；强一致详情以聚合仓储为准；投影列表中患者姓名等字段若与读模型不一致，以代码内 DOC-CONFLICT 为准。</p>
 * <p>聚合边界：仅本聚合 {@link EncounterRepository} 及子表仓储；医技/护士站队列跨表 EXISTS 经
 * {@link EncounterContextCriteriaSupport} 产出 Spec 后由本类分页（B.6.4）。</p>
 */
@Service
@RequiredArgsConstructor
public class EncounterQueryService {

    private final EncounterRepository encounterRepository;
    private final EncounterParticipantRepository participantRepository;
    private final EncounterLocationRepository locationRepository;
    private final EncounterDiagnosisRepository diagnosisRepository;
    private final EncounterContextCriteriaSupport encounterContextCriteriaSupport;
    private final EntityManager entityManager;

    /**
     * 按主键加载就诊详情查询模型。
     *
     * @param encounterId 就诊标识
     * @return 详情模型
     * @throws BusinessException 就诊不存在时抛出
     */
    public EncounterDetailQueryModel loadEncounterById(Long encounterId) {
        // 强依赖主档：不存在则抛错，避免下游用空主键继续展示。
        Encounter encounter = this.mustGetEncounterEntity(encounterId);
        return this.toDetailModel(encounter);
    }

    /**
     * 与 {@link #loadEncounterById(Long)} 等价，兼容历史命名。
     *
     * @param encounterId 就诊标识
     * @return 详情模型
     * @throws BusinessException 就诊不存在时抛出
     */
    public EncounterDetailQueryModel mustGetEncounter(Long encounterId) {
        return this.loadEncounterById(encounterId);
    }

    /**
     * 是否存在未终态的体检就诊（防重复在检，BR-REG / REG-02）。
     *
     * @param patientId 患者主键
     * @return 存在返回 true
     */
    public boolean existsActivePeEncounter(Long patientId) {
        if (patientId == null) {
            // 无患者则不可能有在检占用
            return false;
        }
        return encounterRepository.existsByPatientIdAndClassEnumAndStatusEnumNotInAndIsDeletedFalse(
            patientId, EncounterClass.PE.getCode(), this.peTerminalStatusCodes());
    }

    /**
     * 批量判定患者是否仍有在院住院就诊（门诊日结患者分项「是否住院」；**BR-OP-DCL-032**）。
     *
     * <p>口径：存在 {@code class=IMP} 且状态不在取消/错误录入/完诊/已完成 的未软删就诊。</p>
     *
     * @param patientIds 患者主键集合；空则返回空集
     * @return 有在院住院线的 patientId 集合（保序无关）
     */
    public Set<Long> findPatientIdsWithActiveInpatientStay(Collection<Long> patientIds) {
        Set<Long> result = new LinkedHashSet<>();
        // 空入参：日结患者分项无需打住院线查询
        if (patientIds == null || patientIds.isEmpty()) {
            return result;
        }
        List<Long> distinct = new ArrayList<>();
        Set<Long> seen = new LinkedHashSet<>();
        for (Long patientId : patientIds) {
            // 跳过空主键，避免 IN 列表污染
            if (patientId == null) {
                continue;
            }
            // 去重保序后再分批，防止同患者重复扫库
            if (seen.add(patientId)) {
                distinct.add(patientId);
            }
        }
        // 全为 null 时无需访问仓储
        if (distinct.isEmpty()) {
            return result;
        }
        // Pre：分批 IN，避免超长 IN 列表
        int from = 0;
        while (from < distinct.size()) {
            int to = Math.min(from + 500, distinct.size());
            List<Long> chunk = distinct.subList(from, to);
            List<Encounter> rows =
                encounterRepository.findByPatientIdInAndClassEnumAndStatusEnumNotInAndIsDeletedFalse(
                    chunk,
                    EncounterClass.IMP.getCode(),
                    this.inpatientNonActiveStatusCodes());
            for (Encounter row : rows) {
                // 命中行写入结果集，供门诊日结「是否住院=是」
                if (row != null && row.getPatientId() != null) {
                    result.add(row.getPatientId());
                }
            }
            from = to;
        }
        return result;
    }

    /**
     * 住院「非在院」终态：取消、错误录入、完诊、已完成。
     *
     * @return 排除状态 code
     */
    private List<String> inpatientNonActiveStatusCodes() {
        return List.of(
            EncounterStatus.CANCELLED.getCode(),
            EncounterStatus.ENTERED_IN_ERROR.getCode(),
            EncounterStatus.DISCHARGED.getCode(),
            EncounterStatus.COMPLETED.getCode());
    }

    /**
     * 列出患者未终态的体检就诊（在检防重同源口径）。
     *
     * @param patientId 患者主键
     * @return 在检就诊，按开始时间倒序；无则空列表
     */
    public List<Encounter> listActivePeEncounters(Long patientId) {
        if (patientId == null) {
            // 无患者则不可能有在检单
            return List.of();
        }
        return encounterRepository
            .findByPatientIdAndClassEnumAndStatusEnumNotInAndIsDeletedFalseOrderByStartTimeDesc(
                patientId, EncounterClass.PE.getCode(), this.peTerminalStatusCodes());
    }

    /**
     * 体检视为已结束、不再占用「在检」的状态。
     *
     * @return 终态 code
     */
    private List<String> peTerminalStatusCodes() {
        return List.of(
            EncounterStatus.CANCELLED.getCode(),
            EncounterStatus.ENTERED_IN_ERROR.getCode(),
            EncounterStatus.COMPLETED.getCode(),
            EncounterStatus.DISCHARGED.getCode());
    }

    /**
     * BR-PE-ANNUAL-01：同一患者同一自然年是否已有未取消的 PE 就诊。
     *
     * @param patientId 患者主键
     * @param calendarYear 自然年（如 2026）
     * @return 存在返回 true
     */
    public boolean existsPeEncounterInCalendarYear(Long patientId, int calendarYear) {
        if (patientId == null) {
            return false;
        }
        java.time.LocalDateTime yearStart = java.time.LocalDate.of(calendarYear, 1, 1).atStartOfDay();
        java.time.LocalDateTime yearEnd = java.time.LocalDate.of(calendarYear, 12, 31).atTime(23, 59, 59);
        List<String> cancelled = List.of(
            EncounterStatus.CANCELLED.getCode(),
            EncounterStatus.ENTERED_IN_ERROR.getCode());
        return encounterRepository.count((root, q, cb) -> cb.and(
            cb.equal(root.get("patientId"), patientId),
            cb.equal(root.get("classEnum"), EncounterClass.PE.getCode()),
            cb.isFalse(root.get("isDeleted")),
            cb.not(root.get("statusEnum").in(cancelled)),
            cb.greaterThanOrEqualTo(root.get("createdAt"), yearStart),
            cb.lessThanOrEqualTo(root.get("createdAt"), yearEnd)
        )) > 0;
    }

    /**
     * 患者是否已有任意未取消的 PE 体检就诊（用于新生/年检归类）。
     *
     * @param patientId 患者主键
     * @return 存在返回 true
     */
    public boolean existsAnyNonCancelledPeEncounter(Long patientId) {
        if (patientId == null) {
            return false;
        }
        List<String> cancelled = List.of(
            EncounterStatus.CANCELLED.getCode(),
            EncounterStatus.ENTERED_IN_ERROR.getCode());
        return encounterRepository.count((root, q, cb) -> cb.and(
            cb.equal(root.get("patientId"), patientId),
            cb.equal(root.get("classEnum"), EncounterClass.PE.getCode()),
            cb.isFalse(root.get("isDeleted")),
            cb.not(root.get("statusEnum").in(cancelled))
        )) > 0;
    }

    /**
     * 列出患者全部未软删的 PE 体检就诊（含已取消，由调用方过滤）。
     *
     * @param patientId 患者主键
     * @return 就诊实体列表
     */
    public List<Encounter> listPeEncounterEntitiesByPatientId(Long patientId) {
        if (patientId == null) {
            return List.of();
        }
        return encounterRepository.findAll((root, q, cb) -> cb.and(
            cb.equal(root.get("patientId"), patientId),
            cb.equal(root.get("classEnum"), EncounterClass.PE.getCode()),
            cb.isFalse(root.get("isDeleted"))
        ));
    }

    /**
     * 按主键加载就诊实体（adapter 读路径）；不存在或已软删时 empty。
     *
     * @param encounterId 就诊标识
     * @return 就诊实体
     */
    public Optional<Encounter> findOptionalEncounterEntityById(Long encounterId) {
        if (encounterId == null) {
            return Optional.empty();
        }
        return encounterRepository.findById(encounterId)
            .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()));
    }

    /**
     * 统计某次就诊之前该患者的住院（IMP）次数，供 MRMS 病案「第几次住院」。
     *
     * @param patientId          患者标识
     * @param beforeEncounterId  当前就诊标识（不含）
     * @return 住院次数
     */
    public int countInpatientAdmissionsBefore(long patientId, long beforeEncounterId) {
        Specification<Encounter> spec = (root, query, cb) -> cb.and(
            cb.equal(root.get(FieldNameConstants.PATIENT_ID), patientId),
            cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.IMP.getCode()),
            cb.lessThan(root.get(FieldNameConstants.ID), beforeEncounterId),
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
        return (int) encounterRepository.count(spec);
    }

    /**
     * 按主键加载指定就诊类别的详情（住院登记、门诊挂号等须与通用 {@link #mustGetEncounter(Long)} 隔离）。
     *
     * @param encounterId 就诊标识
     * @param classEnum 期望的就诊类别 {@code code}（如 {@link EncounterClass#IMP}、{@link EncounterClass#AMB}）
     * @param wrongClassErrorCode 主键存在但类别不符时的业务错误码
     * @param wrongClassMessage 类别不符时的提示文案
     * @return 详情模型
     * @throws BusinessException 记录不存在或类别不符时抛出
     */
    public EncounterDetailQueryModel mustGetEncounterOfClass(
            Long encounterId,
            String classEnum,
            String wrongClassErrorCode,
            String wrongClassMessage) {
        Encounter encounter = this.mustGetEncounterEntityOfClass(
            encounterId, classEnum, wrongClassErrorCode, wrongClassMessage);
        return this.toDetailModel(encounter);
    }

    /**
     * 读侧门控：按主键与就诊类别加载聚合根实体（应用层校验后读取 organizationId 等头字段）。
     *
     * @param encounterId 就诊标识
     * @param classEnum 期望的就诊类别 {@code code}
     * @param wrongClassErrorCode 主键存在但类别不符时的业务错误码
     * @param wrongClassMessage 类别不符时的提示文案
     * @return 非空就诊实体
     * @throws BusinessException 记录不存在或类别不符时抛出
     */
    public Encounter requireEncounterEntityOfClass(
            Long encounterId,
            String classEnum,
            String wrongClassErrorCode,
            String wrongClassMessage) {
        return this.mustGetEncounterEntityOfClass(
            encounterId, classEnum, wrongClassErrorCode, wrongClassMessage);
    }

    /**
     * 按业务单号加载就诊摘要（用于扫码、外链等按单号定位场景）。
     *
     * @param busNo 就诊业务单号
     * @return 摘要模型
     * @throws BusinessException 单号不存在时抛出
     */
    public EncounterSimpleQueryModel loadEncounterByBusNo(String busNo) {
        // 业务单号常用于扫码入场，单号无效须明确失败以便终端提示重扫。
        Encounter encounter = encounterRepository.findByBusNo(busNo)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "就诊记录不存在"));
        EncounterSimpleQueryModel model = new EncounterSimpleQueryModel();
        model.setId(encounter.getId());
        model.setBusNo(encounter.getBusNo());
        model.setClassEnum(encounter.getClassEnum());
        model.setStatusEnum(encounter.getStatusEnum());
        return model;
    }

    /**
     * 发药单列表关键字：按就诊业务号模糊匹配未删除就诊主键。
     *
     * @param keyword 用户关键字；空白时返回空列表
     * @return 命中就诊主键列表
     */
    public List<Long> listEncounterIdsByBusNoKeyword(String keyword) {
        if (StringUtils.isEmpty(keyword)) {
            return Collections.emptyList();
        }
        String trimmed = keyword.trim();
        String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(trimmed);
        Specification<Encounter> specification = (root, criteriaQuery, cb) -> {
            jakarta.persistence.criteria.Predicate predicate = cb.and(
                cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
                cb.like(cb.lower(root.get(FieldNameConstants.BUS_NO)), likeIc));
            // B.2.1：就诊业务号精确全等置顶
            KeywordExactFirstOrderSupport.applyExactFirstOrder(
                criteriaQuery,
                cb,
                trimmed,
                List.of(cb.desc(root.get(FieldNameConstants.ID))),
                root.get(FieldNameConstants.BUS_NO));
            return predicate;
        };
        List<Encounter> rows = this.encounterRepository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        for (Encounter row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 发药单列表关键字：按患者主键集合反查未删除就诊主键。
     *
     * @param patientIds 患者主键集合；空时返回空列表
     * @return 就诊主键列表
     */
    public List<Long> listEncounterIdsByPatientIds(Collection<Long> patientIds) {
        if (patientIds == null || patientIds.isEmpty()) {
            return Collections.emptyList();
        }
        Specification<Encounter> specification = (root, criteriaQuery, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            root.get(FieldNameConstants.PATIENT_ID).in(patientIds));
        List<Encounter> rows = this.encounterRepository.findAll(specification);
        List<Long> ids = new ArrayList<>();
        for (Encounter row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 按病区位置主键反查曾挂靠该病区的就诊主键（WARD 轨迹，含已收口）。
     *
     * <p>供护士站汇总单列表按管辖病区收窄：发放行 {@code encounter_id} 与本结果求交后反查 {@code summary_no}。
     * 聚合边界：仅读本聚合 {@code EncounterLocation}；不编排发放/供应写。</p>
     *
     * @param wardLocationIds 病区 {@code adm_location.id} 集合；空时返回空列表
     * @return 就诊主键列表（去重保序）
     */
    public List<Long> listEncounterIdsByWardLocationIds(Collection<Long> wardLocationIds) {
        if (wardLocationIds == null || wardLocationIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> distinctWardIds = new LinkedHashSet<>();
        for (Long wardId : wardLocationIds) {
            if (wardId != null) {
                distinctWardIds.add(wardId);
            }
        }
        // 无有效病区主键：无法定位任何就诊
        if (distinctWardIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<EncounterLocation> rows = this.locationRepository.findAllByLocationIdInAndFormEnum(
            distinctWardIds, LocationForm.WARD.getCode());
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        for (EncounterLocation row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            // 软删轨迹不参与管辖病区可见性
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            encounterIds.add(row.getEncounterId());
        }
        return new ArrayList<>(encounterIds);
    }

    /**
     * 按活动接诊医生（ADMITTER）医务人员主键集合反查就诊主键。
     *
     * <p>聚合边界：仅本聚合参与者仓储；用于门诊病历等按接诊医生收窄列表。</p>
     *
     * @param practitionerIds 接诊医生医务人员主键；空时返回空列表
     * @return 就诊主键列表（去重保序）
     */
    public List<Long> listEncounterIdsByActiveAdmitterPractitionerIds(Collection<Long> practitionerIds) {
        if (practitionerIds == null || practitionerIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> distinctPractitionerIds = new LinkedHashSet<>();
        for (Long practitionerId : practitionerIds) {
            if (practitionerId != null) {
                distinctPractitionerIds.add(practitionerId);
            }
        }
        if (distinctPractitionerIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<EncounterParticipant> participants =
            participantRepository.findAllByPractitionerIdInAndRoleCodeAndStatusEnum(
                distinctPractitionerIds,
                EncounterConstants.ROLE_ADMITTER,
                EncounterActivityStatus.ACTIVE.getCode());
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        for (EncounterParticipant participant : participants) {
            if (participant == null || participant.getEncounterId() == null) {
                continue;
            }
            encounterIds.add(participant.getEncounterId());
        }
        return new ArrayList<>(encounterIds);
    }

    /**
     * 批量映射活动接诊医生（ADMITTER；同就诊多医生保留首次）。
     *
     * @param encounterIds 就诊主键集合
     * @return encounterId → practitionerId
     */
    public Map<Long, Long> mapActiveAdmitterPractitionerIdByEncounterIds(Collection<Long> encounterIds) {
        Map<Long, Long> result = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long encounterId : encounterIds) {
            if (encounterId != null) {
                distinct.add(encounterId);
            }
        }
        if (distinct.isEmpty()) {
            return result;
        }
        List<EncounterParticipant> participants = participantRepository.findAllByEncounterIdInAndStatusEnum(
            distinct, EncounterActivityStatus.ACTIVE.getCode());
        String role = EncounterConstants.ROLE_ADMITTER;
        for (EncounterParticipant participant : participants) {
            if (participant == null || participant.getEncounterId() == null) {
                continue;
            }
            if (!role.equals(participant.getRoleCode())) {
                continue;
            }
            if (!result.containsKey(participant.getEncounterId())) {
                result.put(participant.getEncounterId(), participant.getPractitionerId());
            }
        }
        return result;
    }

    /**
     * 门诊收银/退费队列：按 AMB、会话机构与就诊开始时段收敛就诊主键（不含患者文本关键字）。
     *
     * @param organizationId 会话机构；{@code null} 时不按机构收窄
     * @param startDate      就诊开始下界；{@code null} 忽略
     * @param endDate        就诊开始上界；{@code null} 忽略
     * @return 命中就诊主键；无匹配时空列表
     */
    public List<Long> listOutpatientAmbEncounterIdsForCashierScope(
        Long organizationId,
        LocalDateTime startDate,
        LocalDateTime endDate) {
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Encounter> root = cq.from(Encounter.class);
        cq.select(root.get(FieldNameConstants.ID));
        List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
        predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
        predicates.add(cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.AMB.getCode()));
        if (organizationId != null) {
            predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId));
        }
        if (startDate != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.START_TIME), startDate));
        }
        if (endDate != null) {
            predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.START_TIME), endDate));
        }
        cq.where(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        List<Long> ids = this.entityManager.createQuery(cq).getResultList();
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return IdInQueryChunkSupport.distinctNonNullIds(ids);
    }

    /**
     * 门诊发药/退药：按科室（机构）收窄 AMB 就诊主键。
     *
     * @param organizationId 科室主键
     * @return 命中就诊主键；{@code organizationId} 为空时返回空列表
     */
    public List<Long> listOutpatientAmbEncounterIdsByOrganization(Long organizationId) {
        if (organizationId == null) {
            return Collections.emptyList();
        }
        Specification<Encounter> specification = (root, criteriaQuery, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.AMB.getCode()),
            cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId));
        return this.collectEncounterIds(this.encounterRepository.findAll(specification));
    }

    /**
     * 住院医保费用明细等：按入院科室（{@code organization_id}）收窄 IMP 就诊主键。
     *
     * @param organizationId 入院科室主键
     * @return 命中就诊主键；{@code organizationId} 为空时返回空列表
     */
    public List<Long> listInpatientImpEncounterIdsByOrganization(Long organizationId) {
        if (organizationId == null) {
            return Collections.emptyList();
        }
        Specification<Encounter> specification = (root, criteriaQuery, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.IMP.getCode()),
            cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId));
        return this.collectEncounterIds(this.encounterRepository.findAll(specification));
    }

    /**
     * 住院药房发药/退药队列：IMP 就诊主键，可选按病区收窄。
     *
     * <p>可见态含在院 {@code IN_PROGRESS}/{@code ON_HOLD} 与出院轨 {@code PENDING}/{@code COMPLETED}/{@code DISCHARGED}
     *（办理出院后仍须按患者发药/查已发；AS-IP-PHA-001 BR-IPP-001）。</p>
     * <p>病区收窄：优先活动 WARD/BED；无活动位置的已清床就诊回落末次已完成病区/床位。</p>
     *
     * @param wardId           病区主键；{@code null} 不按病区过滤
     * @param bedIdsUnderWard  病区下启用床位主键；{@code wardId} 非空时由应用层经 Location 聚合解析
     * @return 命中就诊主键
     */
    public List<Long> listInpatientInHospitalEncounterIds(Long wardId, Collection<Long> bedIdsUnderWard) {
        Specification<Encounter> baseSpec = (root, criteriaQuery, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.IMP.getCode()),
            root.get(FieldNameConstants.STATUS_ENUM).in(this.pharmacyQueueVisibleEncounterStatusCodes()));
        List<Encounter> baseRows = this.encounterRepository.findAll(baseSpec);
        if (baseRows.isEmpty()) {
            return Collections.emptyList();
        }
        if (wardId == null) {
            return this.collectEncounterIds(baseRows);
        }
        LinkedHashSet<Long> bedIds = new LinkedHashSet<>();
        if (bedIdsUnderWard != null) {
            for (Long bedId : bedIdsUnderWard) {
                if (bedId != null) {
                    bedIds.add(bedId);
                }
            }
        }
        LinkedHashSet<Long> activeWardEncounterIds = this.collectEncounterIdsByLocationFormAndStatus(
            List.of(wardId), LocationForm.WARD.getCode(), EncounterActivityStatus.ACTIVE.getCode());
        LinkedHashSet<Long> activeBedEncounterIds = new LinkedHashSet<>();
        if (!bedIds.isEmpty()) {
            activeBedEncounterIds = this.collectEncounterIdsByLocationFormAndStatus(
                bedIds, LocationForm.BED.getCode(), EncounterActivityStatus.ACTIVE.getCode());
        }
        LinkedHashSet<Long> activeMatched = new LinkedHashSet<>();
        activeMatched.addAll(activeWardEncounterIds);
        activeMatched.addAll(activeBedEncounterIds);
        // 仅已清床（无活动床）回落末次病区/床；转科在院患者不得因历史 COMPLETED 病区误入。
        LinkedHashSet<Long> dischargedWithoutActiveMatch = new LinkedHashSet<>();
        for (Encounter encounter : baseRows) {
            if (encounter == null || encounter.getId() == null) {
                continue;
            }
            Long encounterId = encounter.getId();
            if (activeMatched.contains(encounterId)) {
                continue;
            }
            if (!EncounterStatus.DISCHARGED.getCode().equals(encounter.getStatusEnum())) {
                continue;
            }
            dischargedWithoutActiveMatch.add(encounterId);
        }
        LinkedHashSet<Long> completedMatched = this.matchEncountersByLastCompletedWardOrBed(
            dischargedWithoutActiveMatch, wardId, bedIds);
        LinkedHashSet<Long> wardScoped = new LinkedHashSet<>();
        for (Encounter encounter : baseRows) {
            if (encounter == null || encounter.getId() == null) {
                continue;
            }
            Long encounterId = encounter.getId();
            if (activeMatched.contains(encounterId) || completedMatched.contains(encounterId)) {
                wardScoped.add(encounterId);
            }
        }
        return new ArrayList<>(wardScoped);
    }

    /**
     * 住院药房队列关键字：按床位 location 反查 IMP 就诊主键（活动床 + 末次已完成床）。
     *
     * @param bedLocationIds 床位 location 主键
     * @return 命中就诊主键；入参空时返回空列表
     */
    public List<Long> listInpatientInHospitalEncounterIdsByActiveBedLocationIds(Collection<Long> bedLocationIds) {
        if (bedLocationIds == null || bedLocationIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> distinctBeds = new LinkedHashSet<>();
        for (Long bedId : bedLocationIds) {
            if (bedId != null) {
                distinctBeds.add(bedId);
            }
        }
        if (distinctBeds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> candidateEncounterIds = new LinkedHashSet<>();
        candidateEncounterIds.addAll(this.collectEncounterIdsByLocationFormAndStatus(
            distinctBeds, LocationForm.BED.getCode(), EncounterActivityStatus.ACTIVE.getCode()));
        // 清床后关键字按床号仍可命中：末次已完成床位轨迹。
        candidateEncounterIds.addAll(this.collectEncounterIdsByLocationFormAndStatus(
            distinctBeds, LocationForm.BED.getCode(), EncounterActivityStatus.COMPLETED.getCode()));
        if (candidateEncounterIds.isEmpty()) {
            return Collections.emptyList();
        }
        return this.filterInpatientInHospitalEncounterIds(candidateEncounterIds);
    }

    /**
     * 保留 AMB 未删就诊主键（关键字 union 后的二次过滤）。
     *
     * @param encounterIds 候选就诊主键
     * @return 过滤后列表
     */
    public List<Long> filterOutpatientAmbEncounterIds(Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return Collections.emptyList();
        }
        List<Encounter> rows = this.encounterRepository.findAllByIdInAndClassEnum(distinct, EncounterClass.AMB.getCode());
        return this.collectEncounterIds(rows);
    }

    /**
     * 体检收银队列：按 PE、会话机构与就诊开始时段收敛就诊主键（不含患者文本关键字）。
     *
     * @param organizationId 会话机构；{@code null} 时不按机构收窄
     * @param startDate      就诊开始下界；{@code null} 忽略
     * @param endDate        就诊开始上界；{@code null} 忽略
     * @return 命中就诊主键；无匹配时空列表
     */
    public List<Long> listPeEncounterIdsForCashierScope(
        Long organizationId,
        LocalDateTime startDate,
        LocalDateTime endDate) {
        Specification<Encounter> specification = (root, criteriaQuery, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.PE.getCode()));
            if (organizationId != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId));
            }
            if (startDate != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.START_TIME), startDate));
            }
            if (endDate != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.START_TIME), endDate));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return this.collectEncounterIds(this.encounterRepository.findAll(specification));
    }

    /**
     * 保留 PE 未删就诊主键（关键字 union 后的二次过滤）。
     *
     * @param encounterIds 候选就诊主键
     * @return 过滤后列表
     */
    public List<Long> filterPeEncounterIds(Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return Collections.emptyList();
        }
        List<Encounter> rows = this.encounterRepository.findAllByIdInAndClassEnum(distinct, EncounterClass.PE.getCode());
        return this.collectEncounterIds(rows);
    }

    /**
     * 保留住院药房队列可见 IMP 就诊主键（关键字 union 后的二次过滤）。
     *
     * <p>可见态同 {@link #listInpatientInHospitalEncounterIds}：在院 + 出院轨。</p>
     *
     * @param encounterIds 候选就诊主键
     * @return 过滤后列表
     */
    public List<Long> filterInpatientInHospitalEncounterIds(Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return Collections.emptyList();
        }
        Specification<Encounter> specification = (root, criteriaQuery, cb) -> cb.and(
            root.get(FieldNameConstants.ID).in(distinct),
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.IMP.getCode()),
            root.get(FieldNameConstants.STATUS_ENUM).in(this.pharmacyQueueVisibleEncounterStatusCodes()));
        return this.collectEncounterIds(this.encounterRepository.findAll(specification));
    }

    /**
     * 住院药房发药/退药队列可见的就诊状态 code（在院 + 办理出院后至清床离院）。
     *
     * @return 不可变状态 code 列表
     */
    private List<String> pharmacyQueueVisibleEncounterStatusCodes() {
        return List.of(
            EncounterStatus.IN_PROGRESS.getCode(),
            EncounterStatus.ON_HOLD.getCode(),
            EncounterStatus.PENDING.getCode(),
            EncounterStatus.COMPLETED.getCode(),
            EncounterStatus.DISCHARGED.getCode());
    }

    /**
     * 按位置主键 + 形态 + 轨迹状态批量收集就诊主键。
     *
     * @param locationIds 位置主键
     * @param formEnum    位置形态（WARD/BED）
     * @param statusEnum  轨迹状态（ACTIVE/COMPLETED）
     * @return 命中就诊主键集合
     */
    private LinkedHashSet<Long> collectEncounterIdsByLocationFormAndStatus(
        Collection<Long> locationIds,
        String formEnum,
        String statusEnum) {
        LinkedHashSet<Long> encounterIds = new LinkedHashSet<>();
        if (locationIds == null || locationIds.isEmpty()) {
            return encounterIds;
        }
        List<EncounterLocation> rows = this.locationRepository.findAllByLocationIdInAndFormEnumAndStatusEnum(
            locationIds, formEnum, statusEnum);
        for (EncounterLocation row : rows) {
            if (row != null && row.getEncounterId() != null) {
                encounterIds.add(row.getEncounterId());
            }
        }
        return encounterIds;
    }

    /**
     * 无活动位置的就诊：末次已完成病区等于目标病区，或末次已完成床位落在病区下床位集合。
     *
     * @param encounterIds 候选就诊（通常为已清床）
     * @param wardId       目标病区
     * @param bedIds       病区下床位主键
     * @return 命中就诊主键
     */
    private LinkedHashSet<Long> matchEncountersByLastCompletedWardOrBed(
        Collection<Long> encounterIds,
        Long wardId,
        Collection<Long> bedIds) {
        LinkedHashSet<Long> matched = new LinkedHashSet<>();
        if (encounterIds == null || encounterIds.isEmpty() || wardId == null) {
            return matched;
        }
        Map<Long, Long> lastWardByEncounterId =
            this.mapLastCompletedWardLocationIdByEncounterIds(encounterIds);
        for (Map.Entry<Long, Long> entry : lastWardByEncounterId.entrySet()) {
            if (wardId.equals(entry.getValue())) {
                matched.add(entry.getKey());
            }
        }
        if (bedIds == null || bedIds.isEmpty()) {
            return matched;
        }
        LinkedHashSet<Long> bedIdSet = new LinkedHashSet<>();
        for (Long bedId : bedIds) {
            if (bedId != null) {
                bedIdSet.add(bedId);
            }
        }
        if (bedIdSet.isEmpty()) {
            return matched;
        }
        Map<Long, Long> lastBedByEncounterId =
            this.mapLastCompletedBedLocationIdByEncounterIds(encounterIds);
        for (Map.Entry<Long, Long> entry : lastBedByEncounterId.entrySet()) {
            if (bedIdSet.contains(entry.getValue())) {
                matched.add(entry.getKey());
            }
        }
        return matched;
    }

    /**
     * 就诊实体集合 → 去重 id 列表；跳过 null 与已软删行。
     *
     * @param rows 查询结果
     * @return 稳定顺序的 encounterId 列表
     */
    private List<Long> collectEncounterIds(Collection<Encounter> rows) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        if (rows == null) {
            return Collections.emptyList();
        }
        for (Encounter row : rows) {
            if (row != null && row.getId() != null && !Boolean.TRUE.equals(row.getIsDeleted())) {
                ids.add(row.getId());
            }
        }
        return new ArrayList<>(ids);
    }

    /**
     * 列出就诊下仍处于激活状态的参与者（如当前在岗医生、护士）。
     *
     * <p><strong>写前门禁 / 占床责任判定</strong>专用：仅 {@code ACTIVE}。
     * 列表/卡片/病案等<strong>展示</strong>须用 {@link #listDisplayEncounterParticipants}，
     * 否则清床离院或召回未恢复参与者后主治/责任护士会空。</p>
     *
     * @param encounterId 就诊标识
     * @return 参与者视图列表
     */
    public List<EncounterParticipantQueryModel> listActiveEncounterParticipants(Long encounterId) {
        List<EncounterParticipant> rows = participantRepository
            .findAllByEncounterIdAndStatusEnum(encounterId, EncounterActivityStatus.ACTIVE.getCode());
        return this.toParticipantQueryModels(rows);
    }

    /**
     * 按角色编码筛选激活参与者（如仅主治、仅麻醉师）。
     *
     * <p>仅 {@code ACTIVE}；展示回显见 {@link #listDisplayEncounterParticipantsByType}。</p>
     *
     * @param encounterId 就诊标识
     * @param roleCode    参与者角色编码
     * @return 参与者视图列表
     */
    public List<EncounterParticipantQueryModel> listActiveEncounterParticipantsByType(Long encounterId, String roleCode) {
        List<EncounterParticipant> rows = participantRepository
            .findAllByEncounterIdAndRoleCodeAndStatusEnum(
                encounterId, roleCode, EncounterActivityStatus.ACTIVE.getCode());
        return this.toParticipantQueryModels(rows);
    }

    /**
     * 展示用参与者：优先活动行；同角色无活动时回落末次已完成行。
     *
     * <p>清床离院会收口全部活动参与者；出院召回不恢复参与者。结算/护士站/病案等读侧须用本方法，
     * 禁止仅调 {@link #listActiveEncounterParticipants} 做卡片展示。</p>
     *
     * @param encounterId 就诊标识
     * @return 参与者视图列表（每角色至多一条：ACTIVE 或末次 COMPLETED）
     */
    public List<EncounterParticipantQueryModel> listDisplayEncounterParticipants(Long encounterId) {
        if (encounterId == null) {
            return Collections.emptyList();
        }
        return this.toParticipantQueryModels(
            this.listDisplayParticipantEntitiesByEncounterIds(List.of(encounterId)));
    }

    /**
     * 展示用按角色参与者：优先活动行；无活动时回落该角色末次已完成行。
     *
     * @param encounterId 就诊标识
     * @param roleCode    参与者角色编码
     * @return 参与者视图列表
     */
    public List<EncounterParticipantQueryModel> listDisplayEncounterParticipantsByType(
        Long encounterId, String roleCode) {
        if (encounterId == null || StringUtils.isEmpty(roleCode)) {
            return Collections.emptyList();
        }
        List<EncounterParticipantQueryModel> all = this.listDisplayEncounterParticipants(encounterId);
        List<EncounterParticipantQueryModel> matched = new ArrayList<>();
        for (EncounterParticipantQueryModel row : all) {
            if (row == null) {
                continue;
            }
            // 仅保留请求角色，供门诊转住院回显接诊医生等单角色场景。
            if (roleCode.equals(row.getRoleCode())) {
                matched.add(row);
            }
        }
        return matched;
    }

    /**
     * 列出就诊的位置轨迹（按开始时间升序，含历史科室/床位）。
     *
     * @param encounterId 就诊标识
     * @return 位置记录视图列表
     */
    public List<EncounterLocationQueryModel> listEncounterLocations(Long encounterId) {
        List<EncounterLocation> rows = locationRepository.findAllByEncounterIdOrderByStartTimeAsc(encounterId);
        List<EncounterLocationQueryModel> models = new ArrayList<>();
        // Loop：遍历位置记录，装配查询模型（含历史科室/床位）
        for (EncounterLocation row : rows) {
            EncounterLocationQueryModel model = new EncounterLocationQueryModel();
            model.setLocationRecordId(row.getId());
            model.setEncounterId(row.getEncounterId());
            model.setLocationId(row.getLocationId());
            model.setFormEnum(row.getFormEnum());
            model.setStatusEnum(row.getStatusEnum());
            model.setStartTime(row.getStartTime());
            model.setEndTime(row.getEndTime());
            models.add(model);
        }
        return models;
    }

    /**
     * 首次分床时刻：全部 BED 轨迹最早 {@code start_time}（转床不改此锚点）。
     *
     * @param encounterId 就诊标识
     * @return 分床时刻；无床位轨迹时返回 {@code null}
     */
    public LocalDateTime findFirstBedAssignedAt(Long encounterId) {
        if (encounterId == null) {
            return null;
        }
        List<EncounterLocation> rows = locationRepository.findAllByEncounterIdOrderByStartTimeAsc(encounterId);
        LocalDateTime earliest = null;
        for (EncounterLocation row : rows) {
            if (row == null || !LocationForm.BED.getCode().equals(row.getFormEnum())) {
                continue;
            }
            LocalDateTime startTime = row.getStartTime();
            if (startTime == null) {
                continue;
            }
            if (earliest == null || startTime.isBefore(earliest)) {
                earliest = startTime;
            }
        }
        return earliest;
    }

    /**
     * 清床时刻：当前无活动 BED，且存在已完成 BED 时，取末次已完成 BED 的 {@code end_time}。
     *
     * <p>仍占床（存在 ACTIVE BED）时返回 {@code null}，避免把转床收口时间误当作出院日。</p>
     *
     * @param encounterId 就诊标识
     * @return 清床时刻；未清床或无床位轨迹时返回 {@code null}
     */
    public LocalDateTime findBedClearedAt(Long encounterId) {
        if (encounterId == null) {
            return null;
        }
        List<EncounterLocation> activeRows = locationRepository.findAllByEncounterIdAndStatusEnum(
            encounterId, EncounterActivityStatus.ACTIVE.getCode());
        for (EncounterLocation row : activeRows) {
            if (row != null && LocationForm.BED.getCode().equals(row.getFormEnum())) {
                return null;
            }
        }
        List<EncounterLocation> rows = locationRepository.findAllByEncounterIdOrderByStartTimeAsc(encounterId);
        LocalDateTime latestEnd = null;
        for (EncounterLocation row : rows) {
            if (row == null || !LocationForm.BED.getCode().equals(row.getFormEnum())) {
                continue;
            }
            if (!EncounterActivityStatus.COMPLETED.getCode().equals(row.getStatusEnum())) {
                continue;
            }
            LocalDateTime endTime = row.getEndTime();
            if (endTime == null) {
                continue;
            }
            if (latestEnd == null || endTime.isAfter(latestEnd)) {
                latestEnd = endTime;
            }
        }
        return latestEnd;
    }

    /**
     * 加载当前就诊活动位置信息。
     *
     * @param encounterId 就诊标识
     * @return 当前位置模型
     */
    public EncounterLocationInfoQueryModel getEncounterLocationInfo(Long encounterId) {
        // 先校验就诊存在，再取当前活动位置，避免对幽灵就诊组装位置信息。
        this.mustGetEncounterEntity(encounterId);
        List<EncounterLocation> activeRows = locationRepository
            .findAllByEncounterIdAndStatusEnum(encounterId, EncounterActivityStatus.ACTIVE.getCode());
        EncounterLocationInfoQueryModel model = new EncounterLocationInfoQueryModel();
        model.setEncounterId(encounterId);
        // 无激活位置记录时，返回空壳模型，由 UI 展示「未分诊/未入区」等状态。
        if (activeRows.isEmpty()) {
            return model;
        }
        EncounterLocation active = activeRows.get(0);
        model.setCurrentLocationId(active.getLocationId());
        model.setCurrentFormEnum(active.getFormEnum());
        model.setActiveSince(active.getStartTime());
        return model;
    }

    /**
     * 列出就诊关联的诊断子表行（EncounterDiagnosis，非 Condition 事实表全量）。
     *
     * @param encounterId 就诊标识
     * @return 诊断子表视图列表
     */
    public List<EncounterDiagnosisQueryModel> listEncounterDiagnoses(Long encounterId) {
        List<EncounterDiagnosis> rows = diagnosisRepository.findAllByEncounterId(encounterId);
        List<EncounterDiagnosisQueryModel> models = new ArrayList<>();
        // Loop：遍历诊断记录，装配查询模型
        for (EncounterDiagnosis row : rows) {
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            models.add(this.toEncounterDiagnosisQueryModel(row));
        }
        return models;
    }

    /**
     * 按主键集合批量装载就诊诊断读模型（跳过软删；不保证入参顺序）。
     *
     * @param diagnosisIds 就诊诊断关联主键集合
     * @return 读模型列表
     */
    public List<EncounterDiagnosisQueryModel> listEncounterDiagnosesByIds(Collection<Long> diagnosisIds) {
        if (diagnosisIds == null || diagnosisIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : diagnosisIds) {
            if (id != null) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            return List.of();
        }
        List<EncounterDiagnosis> rows = diagnosisRepository.findAllByIdIn(unique);
        List<EncounterDiagnosisQueryModel> models = new ArrayList<>(rows.size());
        for (EncounterDiagnosis row : rows) {
            if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            models.add(this.toEncounterDiagnosisQueryModel(row));
        }
        return models;
    }

    /**
     * 实体 → 就诊诊断读模型。
     *
     * @param row 关联实体
     * @return 读模型
     */
    private EncounterDiagnosisQueryModel toEncounterDiagnosisQueryModel(EncounterDiagnosis row) {
        EncounterDiagnosisQueryModel model = new EncounterDiagnosisQueryModel();
        model.setDiagnosisId(row.getId());
        model.setEncounterId(row.getEncounterId());
        model.setConditionDefinitionId(row.getConditionDefinitionId());
        model.setPrimaryFlag(row.getPrimaryFlag());
        model.setRank(row.getRank());
        model.setDescription(row.getDescription());
        model.setDiagTypeCode(row.getDiagTypeCode());
        model.setMedType(row.getMedType());
        model.setTcmSyndromeGroup(row.getTcmSyndromeGroup());
        model.setTcmFlag(row.getTcmFlag());
        return model;
    }

    /**
     * 开嘱写路径：校验 {@code encounter_diagnosis_ids} 属本就诊、未软删、同族。
     * 不强制绑定主诊；若绑定集合中包含主诊，则首位必须是该主诊。
     *
     * <p>空/空白 ids 串 视为不绑诊断，直接通过（住院可不绑）。仅本聚合诊断子表；跨聚合引用计数见
     * {@code application.shared.diagnosis.EncounterDiagnosisReferenceSupport}。</p>
     *
     * @param encounterId            当前就诊主键
     * @param encounterDiagnosisIds  有序 ids 串；可空
     * @throws BusinessException 校验失败
     */
    public void assertBindableEncounterDiagnosisIds(Long encounterId, String encounterDiagnosisIds) {
        // 前置：无绑定则跳过（与「住院可不传诊断」一致）
        if (StringUtils.isEmpty(encounterDiagnosisIds)) {
            return;
        }
        if (encounterId == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_DIAGNOSIS_BINDING_INVALID, "绑定诊断时就诊主键不能为空");
        }
        List<Long> orderedIds;
        try {
            orderedIds = EncounterDiagnosisIdsSupport.parseOrderedIds(encounterDiagnosisIds);
        } catch (NumberFormatException ex) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_DIAGNOSIS_BINDING_INVALID, "就诊诊断主键格式非法");
        }
        // 非空串却解析不出 id：视为脏数据拒绝
        if (orderedIds.isEmpty()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_DIAGNOSIS_BINDING_INVALID, "就诊诊断主键不能为空");
        }
        LinkedHashSet<Long> uniqueIds = new LinkedHashSet<>(orderedIds);
        List<EncounterDiagnosis> rows = diagnosisRepository.findAllByIdIn(uniqueIds);
        Map<Long, EncounterDiagnosis> byId = new HashMap<>(rows.size() * 2);
        for (EncounterDiagnosis row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            byId.put(row.getId(), row);
        }
        String expectedFamily = null;
        Long boundPrimaryDiagnosisId = null;
        // Loop：逐 id 校验归属、未软删、同族；并定位绑定集合内的主诊
        for (Long id : uniqueIds) {
            EncounterDiagnosis row = byId.get(id);
            if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_DIAGNOSIS_BINDING_INVALID, "就诊诊断不存在或已删除");
            }
            if (!encounterId.equals(row.getEncounterId())) {
                throw new BusinessException(ErrorCodeConstants.ENCOUNTER_DIAGNOSIS_BINDING_INVALID, "只能绑定本次就诊诊断");
            }
            String family = EncounterDiagnosisBindingConstants.resolveFamily(row.getMedType());
            if (expectedFamily == null) {
                expectedFamily = family;
            } else if (!expectedFamily.equals(family)) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_DIAGNOSIS_BINDING_FAMILY_MIXED,
                    "普通与慢病诊断不能绑在同一医嘱行");
            }
            if (Boolean.TRUE.equals(row.getPrimaryFlag())) {
                boundPrimaryDiagnosisId = row.getId();
            }
        }
        // 绑定集合含主诊时，首位必须是主诊；未包含主诊则不校验首位
        if (boundPrimaryDiagnosisId != null
            && !boundPrimaryDiagnosisId.equals(orderedIds.get(0))) {
            throw new BusinessException(
                ErrorCodeConstants.ENCOUNTER_DIAGNOSIS_BINDING_PRIMARY_REQUIRED,
                "有主诊断时，绑定诊断首位必须是主诊断");
        }
    }

    /**
     * @deprecated 跨聚合引用计数已迁至 {@code EncounterDiagnosisReferenceSupport}；保留方法签名避免旧调用编译失败，恒返回 0。
     */
    @Deprecated
    public int countAdviceOrChargeRefByEncounterDiagnosisId(Long encounterDiagnosisId) {
        if (encounterDiagnosisId == null) {
            return 0;
        }
        return 0;
    }

    /**
     * 列出就诊下有效中医诊断关联（{@code tcm_flag=true}），供中医 Tab 病证组聚合读侧使用。
     *
     * @param encounterId 就诊标识
     * @return 中医关联行；无数据时返回空列表
     */
    public List<com.zxplat.domain.administration.encounter.queries.model.TcmEncounterDiagnosisAssociationQueryModel>
        listTcmEncounterDiagnosisAssociations(Long encounterId) {
        if (encounterId == null) {
            return List.of();
        }
        List<EncounterDiagnosis> rows = diagnosisRepository.findAllByEncounterId(encounterId);
        List<com.zxplat.domain.administration.encounter.queries.model.TcmEncounterDiagnosisAssociationQueryModel> models =
            new ArrayList<>();
        // Loop：仅保留未软删且标记为中医的就诊诊断关联行。
        for (EncounterDiagnosis row : rows) {
            if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            if (!Boolean.TRUE.equals(row.getTcmFlag())) {
                continue;
            }
            com.zxplat.domain.administration.encounter.queries.model.TcmEncounterDiagnosisAssociationQueryModel model =
                new com.zxplat.domain.administration.encounter.queries.model.TcmEncounterDiagnosisAssociationQueryModel();
            model.setEncounterDiagnosisId(row.getId());
            model.setConditionDefinitionId(row.getConditionDefinitionId());
            model.setTcmSyndromeGroup(row.getTcmSyndromeGroup());
            model.setPrimaryFlag(row.getPrimaryFlag());
            model.setRank(row.getRank());
            model.setMedType(row.getMedType());
            model.setDescription(row.getDescription());
            models.add(model);
        }
        return models;
    }

    /**
     * 按诊断目录主键批量加载未软删的就诊诊断关联（患者历史中医病证组反查）。
     *
     * @param conditionDefinitionIds 诊断目录主键集合
     * @return 关联读模型列表；无命中时返回空列表
     */
    public List<EncounterDiagnosisQueryModel> listActiveAssociationsByConditionDefinitionIds(
        Collection<Long> conditionDefinitionIds) {
        if (conditionDefinitionIds == null || conditionDefinitionIds.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long conditionDefinitionId : conditionDefinitionIds) {
            if (conditionDefinitionId != null) {
                unique.add(conditionDefinitionId);
            }
        }
        if (unique.isEmpty()) {
            return List.of();
        }
        List<EncounterDiagnosis> rows = diagnosisRepository.findAllByConditionDefinitionIdIn(unique);
        List<EncounterDiagnosisQueryModel> models = new ArrayList<>();
        for (EncounterDiagnosis row : rows) {
            // 过滤软删关联，供历史病证组反查仅见有效行
            if (row == null || Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            EncounterDiagnosisQueryModel model = new EncounterDiagnosisQueryModel();
            model.setDiagnosisId(row.getId());
            model.setEncounterId(row.getEncounterId());
            model.setConditionDefinitionId(row.getConditionDefinitionId());
            model.setPrimaryFlag(row.getPrimaryFlag());
            model.setRank(row.getRank());
            model.setDescription(row.getDescription());
            model.setDiagTypeCode(row.getDiagTypeCode());
            model.setMedType(row.getMedType());
            model.setTcmSyndromeGroup(row.getTcmSyndromeGroup());
            model.setTcmFlag(row.getTcmFlag());
            models.add(model);
        }
        return models;
    }

    /**
     * 按 {@code adm_encounter_diagnosis.id} 批量解析医保/诊疗类别（{@code med_type}）。
     *
     * @param diagnosisIds 就诊诊断关联行主键集合
     * @return 关联主键 → med_type；未命中时不包含该键
     */
    public Map<Long, String> mapMedTypeByEncounterDiagnosisIds(Collection<Long> diagnosisIds) {
        if (diagnosisIds == null || diagnosisIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        // Loop：遍历入参集合，提取有效诊断主键
        for (Long id : diagnosisIds) {
            if (id != null) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyMap();
        }
        List<EncounterDiagnosis> rows = diagnosisRepository.findAllByIdIn(unique);
        Map<Long, String> out = new HashMap<>(rows.size() * 2);
        // Loop：遍历诊断记录，构建主键到医保诊疗类别的映射
        for (EncounterDiagnosis row : rows) {
            if (row != null && row.getId() != null) {
                out.put(row.getId(), row.getMedType());
            }
        }
        return out;
    }

    /**
     * 按就诊批量解析展示用医保医疗类别（主诊断优先，否则取首条非空 {@code med_type}）。
     *
     * <p>报表「跟着患者走」的 medType 列口径；不替代处方绑定诊断上的 med_type。</p>
     *
     * @param encounterIds 就诊主键集合
     * @return 就诊主键 → {@code YbMedType#getCode()}；无可用 med_type 时不含键
     */
    public Map<Long, String> mapPreferredMedTypeByEncounterIds(Collection<Long> encounterIds) {
        if (encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        // Loop：去重有效就诊主键
        for (Long encounterId : encounterIds) {
            if (encounterId != null) {
                unique.add(encounterId);
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyMap();
        }
        List<EncounterDiagnosis> rows = this.diagnosisRepository.findAllByEncounterIdIn(unique);
        // 无诊断行：整批无 med_type
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, String> primaryMedTypeByEncounter = new HashMap<>();
        Map<Long, String> firstMedTypeByEncounter = new HashMap<>();
        // Loop：主诊断优先落 primary；非主且尚未有首条时落 first
        for (EncounterDiagnosis row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            // 软删诊断不参与报表 med_type 展示
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            String medType = StringUtils.trimToNull(row.getMedType());
            if (medType == null) {
                continue;
            }
            Long encounterId = row.getEncounterId();
            // 主诊断：覆盖写入优先桶
            if (Boolean.TRUE.equals(row.getPrimaryFlag())) {
                primaryMedTypeByEncounter.put(encounterId, medType);
                continue;
            }
            // 非主：仅在尚无首条时记入回落桶
            if (!firstMedTypeByEncounter.containsKey(encounterId)) {
                firstMedTypeByEncounter.put(encounterId, medType);
            }
        }
        Map<Long, String> out = new HashMap<>(unique.size() * 2);
        // Loop：优先主诊断 med_type，否则回落首条非空
        for (Long encounterId : unique) {
            String primary = primaryMedTypeByEncounter.get(encounterId);
            if (primary != null) {
                out.put(encounterId, primary);
                continue;
            }
            String first = firstMedTypeByEncounter.get(encounterId);
            if (first != null) {
                out.put(encounterId, first);
            }
        }
        return out;
    }

    /**
     * 按医保医疗类别与就诊范围列出未软删就诊诊断行主键（AS-OP-PHA-005 医保类别筛预解析）。
     *
     * @param medTypeCode  {@code YbMedType#getCode()}；空则返回空列表
     * @param encounterIds 就诊主键集合（通常为 AMB 预解析范围）；空则返回空列表
     * @return 就诊诊断行主键去重列表
     */
    public List<Long> listEncounterDiagnosisIdsByMedTypeAndEncounterIdIn(
        String medTypeCode,
        Collection<Long> encounterIds) {
        if (StringUtils.isEmpty(medTypeCode) || encounterIds == null || encounterIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> uniqueEncounterIds = new LinkedHashSet<>();
        // Loop：去重有效就诊主键
        for (Long encounterId : encounterIds) {
            if (encounterId != null) {
                uniqueEncounterIds.add(encounterId);
            }
        }
        if (uniqueEncounterIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<EncounterDiagnosis> rows = this.diagnosisRepository.findAllByMedTypeAndEncounterIdIn(
            medTypeCode.trim(),
            uniqueEncounterIds);
        LinkedHashSet<Long> result = new LinkedHashSet<>();
        // Loop：排除软删行，收集诊断主键
        for (EncounterDiagnosis row : rows) {
            if (row == null || row.getId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            result.add(row.getId());
        }
        return new ArrayList<>(result);
    }

    /**
     * 按 {@code adm_encounter_diagnosis.id} 批量加载就诊诊断关联实体（adapter 读路径）。
     */
    public List<EncounterDiagnosis> listEncounterDiagnosisEntitiesByIds(Collection<Long> diagnosisIds) {
        if (diagnosisIds == null || diagnosisIds.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> unique = new LinkedHashSet<>();
        for (Long id : diagnosisIds) {
            if (id != null) {
                unique.add(id);
            }
        }
        if (unique.isEmpty()) {
            return Collections.emptyList();
        }
        return diagnosisRepository.findAllByIdIn(unique);
    }

    /**
     * 按业务单号加载就诊实体（adapter 读路径）；不存在或已软删时 empty。
     */
    public Optional<Encounter> findOptionalEncounterEntityByBusNo(String busNo) {
        if (StringUtils.isEmpty(busNo)) {
            return Optional.empty();
        }
        return encounterRepository.findByBusNo(busNo.trim())
            .filter(row -> !Boolean.TRUE.equals(row.getIsDeleted()));
    }

    /**
     * 分页查询就诊投影列表。
     *
     * @param query 分页查询入参
     * @return 分页结果
     */
    public Page<EncounterProjectionQueryModel> queryEncounterProjections(EncounterProjectionQuery query) {
        int pageNo = 0;
        int pageSize = 20;
        if (query != null && query.getPageNo() != null && query.getPageNo() > 0) {
            pageNo = query.getPageNo() - 1;
        }
        if (query != null && query.getPageSize() != null && query.getPageSize() > 0) {
            pageSize = query.getPageSize();
        }
        Pageable pageable = PageRequest.of(pageNo, pageSize, Sort.by(Sort.Order.desc(FieldNameConstants.CREATED_AT)));
        Specification<Encounter> specification = (root, criteriaQuery, criteriaBuilder) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (query != null) {
                if (query.getPatientId() != null) {
                    predicates.add(criteriaBuilder.equal(root.get(FieldNameConstants.PATIENT_ID), query.getPatientId()));
                }
                if (query.getClassEnum() != null) {
                    predicates.add(criteriaBuilder.equal(root.get(FieldNameConstants.CLASS_ENUM), query.getClassEnum()));
                }
                if (query.getStatusEnum() != null) {
                    predicates.add(criteriaBuilder.equal(root.get(FieldNameConstants.STATUS_ENUM), query.getStatusEnum()));
                }
                if (query.getOrganizationId() != null) {
                    predicates.add(criteriaBuilder.equal(root.get(FieldNameConstants.ORGANIZATION_ID), query.getOrganizationId()));
                }
            }
            return criteriaBuilder.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        Page<Encounter> page = encounterRepository.findAll(specification, pageable);
        List<EncounterProjectionQueryModel> rows = new ArrayList<>();
        // Loop：遍历结果集，逐条装配展示字段
        for (Encounter encounter : page.getContent()) {
            EncounterProjectionQueryModel model = new EncounterProjectionQueryModel();
            model.setEncounterId(encounter.getId());
            // DOC-CONFLICT: SDD 定义 patientName 来源 encounter_projection 投影表，当前领域层未引入该投影实体，暂以 patientId 字符串占位。
            model.setPatientName(String.valueOf(encounter.getPatientId()));
            model.setClassEnum(encounter.getClassEnum());
            model.setStatusEnum(encounter.getStatusEnum());
            model.setOrganizationId(encounter.getOrganizationId());
            rows.add(model);
        }
        return new PageImpl<>(rows, pageable, page.getTotalElements());
    }

    /**
     * 校验门诊是否可完诊。
     *
     * @param encounterId 就诊标识
     * @return 校验结果
     */
    public EncounterCompletionCheckQueryModel checkOutpatientCompletable(Long encounterId) {
        Encounter encounter = this.mustGetEncounterEntity(encounterId);
        EncounterCompletionCheckQueryModel result = new EncounterCompletionCheckQueryModel();
        result.setEncounterId(encounterId);
        result.setCompletable(Boolean.FALSE);
        // 完诊校验仅针对门诊类别；其他类别直接拒绝并提示。
        if (!EncounterClass.AMB.getCode().equals(encounter.getClassEnum())) {
            result.setFailureCode(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS);
            result.setFailureMessage("当前就诊类别不是门诊");
            return result;
        }
        // 仅「进行中/暂停」允许完诊，避免已结束就诊重复完诊。
        if (!EncounterStatus.IN_PROGRESS.getCode().equals(encounter.getStatusEnum())
            && !EncounterStatus.ON_HOLD.getCode().equals(encounter.getStatusEnum())) {
            result.setFailureCode(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS);
            result.setFailureMessage("当前状态不允许完诊");
            return result;
        }
        List<EncounterParticipant> activeDoctors = participantRepository
            .findAllByEncounterIdAndRoleCodeAndStatusEnum(
                encounterId, EncounterConstants.ROLE_ADMITTER, EncounterActivityStatus.ACTIVE.getCode());
        // 门诊完诊需有在班的接诊医生参与者，否则业务上无法闭环责任主体。
        if (activeDoctors.isEmpty()) {
            result.setFailureCode(ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT);
            result.setFailureMessage("缺少负责医生参与者");
            return result;
        }
        result.setCompletable(Boolean.TRUE);
        result.setFailureCode("OK");
        result.setFailureMessage("通过");
        return result;
    }

    /**
     * 校验门诊重复挂号。
     *
     * <p>规则：同一自然日、同一患者、同一目标科室下，若已存在占用名额的门诊就诊（含待挂号 {@link EncounterStatus#PLANNED} 及诊毕 {@link EncounterStatus#DISCHARGED} 等），则视为重复；
     * {@link EncounterStatus#CANCELLED}（退号或撤销待挂号）释放名额。</p>
     *
     * @param query 校验入参
     * @return 校验结果
     */
    public OutpatientDuplicateCheckQueryModel checkOutpatientDuplicateRegistration(OutpatientDuplicateCheckQuery query) {
        if (query == null || query.getPatientId() == null || query.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "患者标识和目标科室必填");
        }
        LocalDate registrationDay = query.getRegistrationDate();
        if (registrationDay == null) {
            registrationDay = LocalDate.now();
        }
        LocalDateTime dayStart = registrationDay.atStartOfDay();
        LocalDateTime dayEndExclusive = registrationDay.plusDays(1).atStartOfDay();
        // 待挂号 PLANNED 与已挂号/诊毕等同样占名额，仅 CANCELLED 退号或撤销后释放。
        List<String> blockingStatuses = EncounterStatus.outpatientDuplicateBlockingStatusCodes();
        List<String> outpatientClasses = Collections.singletonList(EncounterClass.AMB.getCode());
        boolean duplicated = existsPaidOutpatientEncounterForDepartmentOnCalendarDay(
            query.getPatientId(),
            query.getOrganizationId(),
            outpatientClasses,
            blockingStatuses,
            dayStart,
            dayEndExclusive);

        OutpatientDuplicateCheckQueryModel model = new OutpatientDuplicateCheckQueryModel();
        model.setDuplicated(duplicated);
        model.setPatientId(query.getPatientId());
        model.setOrganizationId(query.getOrganizationId());
        if (duplicated) {
            Specification<Encounter> specification = (root, cq, cb) -> cb.and(
                cb.equal(root.get(FieldNameConstants.PATIENT_ID), query.getPatientId()),
                cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), query.getOrganizationId()),
                root.get(FieldNameConstants.CLASS_ENUM).in(outpatientClasses),
                root.get(FieldNameConstants.STATUS_ENUM).in(blockingStatuses),
                cb.greaterThanOrEqualTo(root.get(FieldNameConstants.CREATED_AT), dayStart),
                cb.lessThan(root.get(FieldNameConstants.CREATED_AT), dayEndExclusive),
                cb.isFalse(root.get(FieldNameConstants.IS_DELETED))
            );
            List<Encounter> encounters = encounterRepository.findAll(specification);
            List<Long> encounterIds = new ArrayList<>();
            // Loop：提取重复就诊的主键列表
            for (Encounter encounter : encounters) {
                encounterIds.add(encounter.getId());
            }
            model.setEncounterIds(encounterIds);
            return model;
        }
        model.setEncounterIds(Collections.emptyList());
        return model;
    }

    /**
     * 是否存在满足「复诊0元号」资格的前次门诊就诊（BR-REG-020）。
     *
     * <p>口径：同一患者、同一挂号科室；状态为已接诊（在诊/暂离/完诊）；{@code reception_time} 非空且
     * {@code receptionTime <= asOf < receptionTime + 72h}；排除指定号源类型（复诊0元号自身，防连环续挂）。</p>
     *
     * @param query 资格查询入参
     * @return 存在至少一条符合条件就诊时 {@code true}
     */
    public boolean existsQualifyingHistoricalFollowUpPriorVisit(HistoricalFollowUpEligibilityQuery query) {
        if (query == null || query.getPatientId() == null || query.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_ARGUMENT, "患者标识和挂号科室必填");
        }
        LocalDateTime asOf = query.getAsOf();
        if (asOf == null) {
            asOf = LocalDateTime.now();
        }
        final LocalDateTime referenceAsOf = asOf;
        LocalDateTime windowStartInclusive = referenceAsOf.minusHours(HealthcareServiceConstants.HISTORICAL_FOLLOW_UP_ELIGIBILITY_HOURS);
        List<String> receptionStartedStatuses = EncounterStatus.outpatientReceptionStartedStatusCodes();
        List<String> outpatientClasses = Collections.singletonList(EncounterClass.AMB.getCode());
        Specification<Encounter> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get(FieldNameConstants.PATIENT_ID), query.getPatientId()));
            predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), query.getOrganizationId()));
            predicates.add(root.get(FieldNameConstants.CLASS_ENUM).in(outpatientClasses));
            predicates.add(root.get(FieldNameConstants.STATUS_ENUM).in(receptionStartedStatuses));
            predicates.add(cb.isNotNull(root.get(FieldNameConstants.RECEPTION_TIME)));
            predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.RECEPTION_TIME), referenceAsOf));
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.RECEPTION_TIME), windowStartInclusive));
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (query.getExcludedServiceTypeId() != null) {
                predicates.add(cb.notEqual(root.get(FieldNameConstants.SERVICE_TYPE_ID), query.getExcludedServiceTypeId()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return encounterRepository.count(specification) > 0;
    }

    /**
     * 同日同科室门诊防重：是否存在占用名额的就诊（与 {@link EncounterDomainService} 写侧断言口径一致）。
     *
     * @param patientId 患者主键
     * @param organizationId 挂号科室/机构主键
     * @param outpatientClassEnums 门诊类别 code 集合（如 OP/EM）
     * @param paidStatusEnums 视为已占用号源的状态 code 集合
     * @param dayStartInclusive 自然日起始（含）
     * @param dayEndExclusive 自然日结束（不含）
     * @return 存在至少一条匹配就诊时 {@code true}
     */
    public boolean existsPaidOutpatientEncounterForDepartmentOnCalendarDay(
        Long patientId,
        Long organizationId,
        Collection<String> outpatientClassEnums,
        Collection<String> paidStatusEnums,
        LocalDateTime dayStartInclusive,
        LocalDateTime dayEndExclusive) {
        Specification<Encounter> specification = buildPaidOutpatientDaySpec(
            patientId, organizationId, outpatientClassEnums, paidStatusEnums, dayStartInclusive, dayEndExclusive);
        return encounterRepository.count(specification) > 0;
    }

    /**
     * 构造「同日同科室已占用门诊号源」的 JPA 规约（供 count 防重，B.2 禁仓储 @Query）。
     */
    private static Specification<Encounter> buildPaidOutpatientDaySpec(
        Long patientId,
        Long organizationId,
        Collection<String> outpatientClassEnums,
        Collection<String> paidStatusEnums,
        LocalDateTime dayStartInclusive,
        LocalDateTime dayEndExclusive) {
        return (root, query, cb) -> cb.and(
            cb.equal(root.get(FieldNameConstants.PATIENT_ID), patientId),
            cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId),
            root.get(FieldNameConstants.CLASS_ENUM).in(outpatientClassEnums),
            root.get(FieldNameConstants.STATUS_ENUM).in(paidStatusEnums),
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.greaterThanOrEqualTo(root.get(FieldNameConstants.CREATED_AT), dayStartInclusive),
            cb.lessThan(root.get(FieldNameConstants.CREATED_AT), dayEndExclusive));
    }

    /**
     * 按门诊锚点查询未取消的住院线（最新一条）。
     *
     * @param ambEncounterId 源门诊就诊标识
     * @return 查询结果；无命中时 {@link OpenInpatientLineQuery#isExists()} 为 false
     */
    public OpenInpatientLineQuery findOpenInpatientLine(Long ambEncounterId) {
        OpenInpatientLineQuery result = new OpenInpatientLineQuery();
        result.setExists(false);
        if (ambEncounterId == null) {
            return result;
        }
        List<String> excluded = Collections.singletonList(EncounterStatus.CANCELLED.getCode());
        Optional<Encounter> row = encounterRepository.findTopByAmbEncounterIdAndClassEnumAndStatusEnumNotInOrderByCreatedAtDesc(
            ambEncounterId, EncounterClass.IMP.getCode(), excluded);
        if (row.isEmpty()) {
            return result;
        }
        Encounter inpatient = row.get();
        result.setExists(true);
        result.setEncounterId(inpatient.getId());
        result.setBusNo(inpatient.getBusNo());
        result.setStatusEnum(inpatient.getStatusEnum());
        return result;
    }

    /**
     * 判断门诊锚点下是否已有未取消住院线。
     *
     * @param ambEncounterId 源就诊标识
     * @return 已存在未取消住院线时 true
     */
    public boolean existsOpenInpatientLine(Long ambEncounterId) {
        return this.findOpenInpatientLine(ambEncounterId).isExists();
    }

    /**
     * 解析当前就诊与其门诊/住院关联对端的就诊 ID 并集（病历历史互通用）。
     *
     * <p>规则：结果始终含当前 ID；住院线追加 {@code amb_encounter_id}；门诊线追加未取消住院线（若有）。</p>
     *
     * @param encounterId 当前就诊标识；{@code null} 时返回空列表
     * @return 去重后的就诊 ID 列表（当前优先）
     */
    public List<Long> resolveLinkedEncounterIds(Long encounterId) {
        List<Long> result = new ArrayList<>();
        if (encounterId == null) {
            return result;
        }
        result.add(encounterId);
        Optional<Encounter> encounterOpt = this.findOptionalEncounterEntityById(encounterId);
        if (encounterOpt.isEmpty()) {
            return result;
        }
        Encounter encounter = encounterOpt.get();
        if (EncounterClass.IMP.getCode().equals(encounter.getClassEnum())) {
            Long ambId = encounter.getAmbEncounterId();
            if (ambId != null && !ambId.equals(encounterId)) {
                result.add(ambId);
            }
            return result;
        }
        if (EncounterClass.AMB.getCode().equals(encounter.getClassEnum())) {
            OpenInpatientLineQuery inpatientLine = this.findOpenInpatientLine(encounterId);
            if (inpatientLine.isExists()
                    && inpatientLine.getEncounterId() != null
                    && !inpatientLine.getEncounterId().equals(encounterId)) {
                result.add(inpatientLine.getEncounterId());
            }
        }
        return result;
    }

    /**
     * 断言源就诊可从门诊发起住院申请（BR-IP-REQ-001）。
     *
     * @param ambEncounterId 源门诊就诊标识
     * @throws BusinessException 就诊不存在、类别不符、已终态或状态不允许
     */
    public void assertAmbForInpatientReq(Long ambEncounterId) {
        Encounter amb = this.mustGetEncounterEntity(ambEncounterId);
        String classCode = amb.getClassEnum();
        if (!EncounterClass.AMB.getCode().equals(classCode)) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "仅门诊就诊可发起住院申请");
        }
        EncounterStatus current = EnumUtil.fromCode(EncounterStatus.class, amb.getStatusEnum());
        if (current.isTerminal()) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "源就诊已结束，无法办理住院");
        }
        List<String> eligible = Arrays.asList(
            EncounterStatus.REGISTERED.getCode(),
            EncounterStatus.TRIAGED.getCode(),
            EncounterStatus.IN_PROGRESS.getCode(),
            EncounterStatus.ON_HOLD.getCode()
        );
        if (!eligible.contains(amb.getStatusEnum())) {
            throw new BusinessException(ErrorCodeConstants.ENCOUNTER_INVALID_STATUS, "源就诊当前状态不允许办理住院");
        }
    }

    /**
     * 加载就诊聚合根；不存在时抛出业务异常，供读侧强依赖主档的方法复用。
     *
     * @param encounterId 就诊标识
     * @return 非空就诊实体
     * @throws BusinessException 主键无对应有效记录时
     */
    private Encounter mustGetEncounterEntity(Long encounterId) {
        return encounterRepository.findById(encounterId)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "就诊记录不存在"));
    }

    /**
     * 将就诊主档字段拷贝到详情查询模型（不含参与者/位置等子资源，需另行接口加载）。
     *
     * @param encounter 就诊实体
     * @return 详情模型
     */
    private EncounterDetailQueryModel toDetailModel(Encounter encounter) {
        EncounterDetailQueryModel model = new EncounterDetailQueryModel();
        model.setId(encounter.getId());
        model.setBusNo(encounter.getBusNo());
        model.setPatientId(encounter.getPatientId());
        model.setClassEnum(encounter.getClassEnum());
        model.setStatusEnum(encounter.getStatusEnum());
        model.setOrganizationId(encounter.getOrganizationId());
        model.setStartTime(encounter.getStartTime());
        model.setEndTime(encounter.getEndTime());
        model.setReceptionTime(encounter.getReceptionTime());
        model.setAdmitSourceCode(encounter.getAdmitSourceCode());
        model.setPriorityEnum(encounter.getPriorityEnum());
        model.setChargeIntent(encounter.getChargeIntent());
        model.setAmbEncounterId(encounter.getAmbEncounterId());
        return model;
    }

    /**
     * 批量解析住院就诊主键 → 源门诊就诊主键（{@code amb_encounter_id}）。
     *
     * <p>用于登记列表/弹窗展示：有医生申请转住院时须带回门诊锚点，无申请直登则无映射项。</p>
     *
     * @param encounterIds 住院就诊主键集合；{@code null} 或空时返回空映射
     * @return 住院就诊 ID → 源门诊就诊 ID（仅 {@code ambEncounterId} 非空的行）
     */
    public Map<Long, Long> mapAmbEncounterIdByEncounterIds(Collection<Long> encounterIds) {
        Map<Long, Long> result = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return result;
        }
        // 住院登记列表仅解析 IMP 线，避免误将门诊就诊 ID 带入映射。
        List<Encounter> rows =
            encounterRepository.findAllByIdInAndClassEnum(distinct, EncounterClass.IMP.getCode());
        for (Encounter row : rows) {
            if (row.getAmbEncounterId() != null) {
                result.put(row.getId(), row.getAmbEncounterId());
            }
        }
        return result;
    }

    /**
     * 批量解析就诊主键 → 就诊业务号（{@code bus_no}；门诊/住院不限 {@code class_enum}）。
     *
     * <p>护士工作量等跨 AMB/IMP 读侧 enrich 经本方法回填；住院-only 列表仍用 {@link #mapBusNoByEncounterIds}。</p>
     *
     * @param encounterIds 就诊主键集合；{@code null} 或空时返回空映射
     * @return 就诊 ID → 就诊号
     */
    public Map<Long, String> mapEncounterBusNoByIds(Collection<Long> encounterIds) {
        Map<Long, String> result = new HashMap<>();
        List<Encounter> rows = this.listEncounterEntitiesByIds(encounterIds);
        for (Encounter row : rows) {
            if (row.getId() != null && StringUtils.isNotEmpty(row.getBusNo())) {
                result.put(row.getId(), row.getBusNo());
            }
        }
        return result;
    }

    /**
     * 批量解析住院就诊主键 → 住院业务号（{@code bus_no}）。
     *
     * <p>读模型 {@code encounter_bus_no} 未投影时，登记/预交金列表经本方法回填。</p>
     *
     * @param encounterIds 住院就诊主键集合；{@code null} 或空时返回空映射
     * @return 住院就诊 ID → 住院号
     */
    public Map<Long, String> mapBusNoByEncounterIds(Collection<Long> encounterIds) {
        Map<Long, String> result = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return result;
        }
        List<Encounter> rows =
            encounterRepository.findAllByIdInAndClassEnum(distinct, EncounterClass.IMP.getCode());
        for (Encounter row : rows) {
            if (row.getId() != null && StringUtils.isNotEmpty(row.getBusNo())) {
                result.put(row.getId(), row.getBusNo());
            }
        }
        return result;
    }

    /**
     * 批量解析住院就诊主键 → 患者主键。
     *
     * <p>读模型 {@code patient_id} 未投影时，预交金/登记列表经本方法回填，避免患者-就诊一致性门控误判。</p>
     *
     * @param encounterIds 住院就诊主键集合；{@code null} 或空时返回空映射
     * @return 住院就诊 ID → 患者 ID
     */
    public Map<Long, Long> mapPatientIdByEncounterIds(Collection<Long> encounterIds) {
        Map<Long, Long> result = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return result;
        }
        List<Encounter> rows =
            encounterRepository.findAllByIdInAndClassEnum(distinct, EncounterClass.IMP.getCode());
        for (Encounter row : rows) {
            if (row.getId() != null && row.getPatientId() != null) {
                result.put(row.getId(), row.getPatientId());
            }
        }
        return result;
    }

    /**
     * 按住院就诊主键批量解析 {@code priority_enum}（护士站列表卡片，AS-IP-NUR-001 IF-IP-NUR-02）。
     *
     * <p>读模型未投影病情优先级时，应用层经本方法批量回填，避免逐条 N+1。</p>
     *
     * @param encounterIds 住院就诊主键集合；{@code null} 或空时返回空映射
     * @return 住院就诊 ID → {@code adm_encounter.priority_enum}；无值则不写入该键
     */
    public Map<Long, String> mapPriorityEnumByEncounterIds(Collection<Long> encounterIds) {
        Map<Long, String> result = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return result;
        }
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        for (Long id : encounterIds) {
            if (id != null) {
                distinct.add(id);
            }
        }
        if (distinct.isEmpty()) {
            return result;
        }
        List<Encounter> rows =
            encounterRepository.findAllByIdInAndClassEnum(distinct, EncounterClass.IMP.getCode());
        for (Encounter row : rows) {
            if (row.getId() != null && StringUtils.isNotEmpty(row.getPriorityEnum())) {
                result.put(row.getId(), row.getPriorityEnum().trim());
            }
        }
        return result;
    }

    /**
     * 按床位主键批量解析活动占床就诊（AS-IP-NUR-002 ATD-01，DM-ADM-002 §6.2.12）。
     *
     * @param locationIds 床位级 {@code adm_location.id} 集合
     * @return 床位 id → 占床就诊 id；无活动占床则不包含该键
     * @throws BusinessException 同一床位存在多条活动占床时抛出
     */
    public Map<Long, Long> mapActiveBedEncounterIdByLocationIds(Collection<Long> locationIds) {
        if (locationIds == null || locationIds.isEmpty()) {
            return Collections.emptyMap();
        }
        LinkedHashSet<Long> distinctBedIds = new LinkedHashSet<>();
        for (Long bedId : locationIds) {
            if (bedId != null) {
                distinctBedIds.add(bedId);
            }
        }
        if (distinctBedIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<EncounterLocation> rows = locationRepository.findAllByLocationIdInAndFormEnumAndStatusEnum(
            distinctBedIds,
            LocationForm.BED.getCode(),
            EncounterActivityStatus.ACTIVE.getCode());
        Map<Long, Long> bedIdToEncounterId = new HashMap<>(rows.size() * 2);
        for (EncounterLocation row : rows) {
            if (row == null || row.getLocationId() == null || row.getEncounterId() == null) {
                continue;
            }
            if (bedIdToEncounterId.containsKey(row.getLocationId())) {
                throw new BusinessException(
                    ErrorCodeConstants.ENCOUNTER_RULE_CONFLICT,
                    "同一床位存在多条活动占床记录");
            }
            bedIdToEncounterId.put(row.getLocationId(), row.getEncounterId());
        }
        return bedIdToEncounterId;
    }

    /**
     * 解析就诊当前唯一活动位置 Location 主键（DM-ADM-002 §6.2.13；AS-IP-NUR-002 ATD-02 Pre）。
     *
     * <p>在全部 {@code status_enum=ACTIVE} 轨迹中按 {@code form_enum} 筛选；调用方传入错误码与文案
     * （与 {@link #mustGetEncounterOfClass} 一致，应用层与写侧可各用各码）。</p>
     *
     * @param encounterId 就诊主键
     * @param formEnum 位置形态 code（如 {@link LocationForm#BED}、{@link LocationForm#HOUSE}）
     * @param notFoundErrorCode 无匹配活动行时的业务错误码
     * @param notFoundMessage 无匹配活动行时的提示
     * @param duplicateErrorCode 多条匹配活动行时的业务错误码
     * @param duplicateMessage 多条匹配活动行时的提示
     * @return 活动位置 locationId
     * @throws BusinessException 无匹配或重复时抛出
     */
    public Long requireActiveLocationId(
        Long encounterId,
        String formEnum,
        String notFoundErrorCode,
        String notFoundMessage,
        String duplicateErrorCode,
        String duplicateMessage) {
        List<EncounterLocation> activeLocations = locationRepository.findAllByEncounterIdAndStatusEnum(
            encounterId, EncounterActivityStatus.ACTIVE.getCode());
        Long matchedLocationId = null;
        for (EncounterLocation location : activeLocations) {
            if (location == null || !formEnum.equals(location.getFormEnum())) {
                continue;
            }
            if (matchedLocationId != null) {
                throw new BusinessException(duplicateErrorCode, duplicateMessage);
            }
            matchedLocationId = location.getLocationId();
        }
        if (matchedLocationId == null) {
            throw new BusinessException(notFoundErrorCode, notFoundMessage);
        }
        return matchedLocationId;
    }

    /**
     * 读侧 Criteria 分页（querysupport Loader 收口；Specification 仍由应用层按场景构建）。
     *
     * @param specification JPA 规约
     * @param pageable      分页与排序
     * @return 就诊实体页
     */
    public Page<Encounter> pageEncounterEntities(Specification<Encounter> specification, Pageable pageable) {
        return encounterRepository.findAll(specification, pageable);
    }

    /**
     * 医技工作站患者队列分页（仅 Encounter 实体页；人口学由应用层上下文组装）。
     *
     * <p>对齐 AS-MT-EXE-001 IF-MT-EXE-01：按登录 TECH 与门诊/住院 scope 过滤本站可见诊疗有活；
     * 可选在诊疗有活之上再收窄仅待发耗材（仅耗材无诊疗不进队）；
     * 可选 {@code executionQueueFilter} 待执行/已执行分桶（门诊项目+耗材，住院仅项目，BR-MT-EXE-023）；
     * 规约见 {@link EncounterContextCriteriaSupport#medicalTechWorkstationSpecification}。</p>
     *
     * @param query 医技队列入参；null 时返回空页
     * @return 就诊实体分页
     */
    public Page<Encounter> pageMedicalTechWorkstationQueue(MedicalTechWorkstationQueueQuery query) {
        // Gate：空入参直接空页，避免缺 TECH 科扫全库
        if (query == null) {
            return Page.empty();
        }
        int pageIndex = query.springPageIndex();
        int pageSize = 20;
        if (query.getPageSize() != null && query.getPageSize() > 0) {
            pageSize = query.getPageSize();
        }
        if (pageSize > 100) {
            pageSize = 100;
        }
        Sort sort = Sort.by(
            Sort.Order.desc(FieldNameConstants.START_TIME),
            Sort.Order.desc(FieldNameConstants.ID));
        // B.2.1：关键字非空时无 Sort，保留 Spec 精确优先 ORDER BY
        Pageable pageable;
        if (StringUtils.isNotEmpty(query.getKeyword())) {
            pageable = PageRequest.of(pageIndex, pageSize);
        } else {
            pageable = PageRequest.of(pageIndex, pageSize, sort);
        }
        return this.pageEncounterEntities(
            this.encounterContextCriteriaSupport.medicalTechWorkstationSpecification(query), pageable);
    }

    /**
     * 读侧 Criteria 计数（querysupport Loader 收口）。
     */
    public long countEncounterEntities(Specification<Encounter> specification) {
        return encounterRepository.count(specification);
    }

    /**
     * 读侧 Criteria 按住院号去重计数（结算已结算队列分页 total 口径）。
     *
     * @param specification JPA 规约
     * @return 不重复 {@code bus_no} 数量
     */
    public long countDistinctBusNoEncounterEntities(Specification<Encounter> specification) {
        CriteriaBuilder cb = this.entityManager.getCriteriaBuilder();
        CriteriaQuery<Long> cq = cb.createQuery(Long.class);
        Root<Encounter> root = cq.from(Encounter.class);
        cq.select(cb.countDistinct(root.get(FieldNameConstants.BUS_NO)));
        if (specification != null) {
            Predicate predicate = specification.toPredicate(root, cq, cb);
            if (predicate != null) {
                cq.where(predicate);
            }
        }
        Long totalRow = this.entityManager.createQuery(cq).getSingleResult();
        if (totalRow == null) {
            return 0L;
        }
        return totalRow.longValue();
    }

    /**
     * 读侧 Criteria 列表（无分页）。
     *
     * @param specification JPA 规约
     * @param sort          排序
     * @return 就诊实体列表
     */
    public List<Encounter> listEncounterEntities(Specification<Encounter> specification, Sort sort) {
        return encounterRepository.findAll(specification, sort);
    }

    /**
     * 按主键批量加载就诊实体（保持调用方 id 集合语义，过滤 null）。
     *
     * <p>主键过多时按 {@link com.zxplat.domain.share.constants.GeneralSharedConstants#ID_IN_QUERY_CHUNK_SIZE} 分批 IN。</p>
     *
     * @param encounterIds 就诊主键集合
     * @return 命中实体列表
     */
    public List<Encounter> listEncounterEntitiesByIds(Collection<Long> encounterIds) {
        LinkedHashSet<Long> distinct = this.distinctNonNullIds(encounterIds);
        if (distinct.isEmpty()) {
            return Collections.emptyList();
        }
        return IdInQueryChunkSupport.loadByIdInChunks(
            distinct,
            chunkIds -> encounterRepository.findAllByIdIn(new LinkedHashSet<>(chunkIds)));
    }

    /**
     * 按主键与 IMP 类别加载住院就诊实体。
     *
     * @param encounterId 就诊主键
     * @return 住院实体；不存在或非 IMP 时 empty
     */
    public Optional<Encounter> findInpatientEncounterEntityById(Long encounterId) {
        if (encounterId == null) {
            return Optional.empty();
        }
        return encounterRepository.findByIdAndClassEnum(encounterId, EncounterClass.IMP.getCode());
    }

    /**
     * 按主键批量加载 IMP 类别就诊实体。
     *
     * <p>主键过多时按 {@link com.zxplat.domain.share.constants.GeneralSharedConstants#ID_IN_QUERY_CHUNK_SIZE} 分批 IN。</p>
     *
     * @param encounterIds 就诊主键集合
     * @return 住院实体列表
     */
    public List<Encounter> listInpatientEncounterEntitiesByIds(Collection<Long> encounterIds) {
        LinkedHashSet<Long> distinct = this.distinctNonNullIds(encounterIds);
        if (distinct.isEmpty()) {
            return Collections.emptyList();
        }
        return IdInQueryChunkSupport.loadByIdInChunks(
            distinct,
            chunkIds -> encounterRepository.findAllByIdInAndClassEnum(
                new LinkedHashSet<>(chunkIds), EncounterClass.IMP.getCode()));
    }

    /**
     * 批量加载活动态就诊参与者实体（写前门禁 / 滚费责任判定等须真实在岗）。
     *
     * <p>展示组装见 {@link #listDisplayParticipantEntitiesByEncounterIds}。</p>
     *
     * @param encounterIds 就诊主键集合
     * @return 参与者实体列表
     */
    public List<EncounterParticipant> listActiveParticipantEntitiesByEncounterIds(Collection<Long> encounterIds) {
        LinkedHashSet<Long> distinct = this.distinctNonNullIds(encounterIds);
        if (distinct.isEmpty()) {
            return Collections.emptyList();
        }
        return participantRepository.findAllByEncounterIdInAndStatusEnum(
            distinct, EncounterActivityStatus.ACTIVE.getCode());
    }

    /**
     * 批量加载展示用参与者实体：同角色优先 {@code ACTIVE}，否则取 {@code COMPLETED} 且 {@code end_time} 最大行。
     *
     * <p>仅本聚合 {@code EncounterParticipantRepository}。供住院/门诊上下文、结算队列、病案自动取值等
     * 读侧展示；不得用于「当前必须有活动责任人」类写前门禁。</p>
     *
     * @param encounterIds 就诊主键集合
     * @return 参与者实体列表（每 encounter+role 至多一条）
     */
    public List<EncounterParticipant> listDisplayParticipantEntitiesByEncounterIds(Collection<Long> encounterIds) {
        LinkedHashSet<Long> distinct = this.distinctNonNullIds(encounterIds);
        if (distinct.isEmpty()) {
            return Collections.emptyList();
        }
        List<EncounterParticipant> activeRows = participantRepository.findAllByEncounterIdInAndStatusEnum(
            distinct, EncounterActivityStatus.ACTIVE.getCode());
        Set<String> activeRoleKeys = new HashSet<>();
        for (EncounterParticipant row : activeRows) {
            if (row == null || row.getEncounterId() == null || StringUtils.isEmpty(row.getRoleCode())) {
                continue;
            }
            activeRoleKeys.add(this.participantRoleKey(row.getEncounterId(), row.getRoleCode()));
        }
        List<EncounterParticipant> completedRows = participantRepository.findAllByEncounterIdInAndStatusEnum(
            distinct, EncounterActivityStatus.COMPLETED.getCode());
        Map<String, EncounterParticipant> lastCompletedByRoleKey = new HashMap<>();
        for (EncounterParticipant row : completedRows) {
            if (row == null || row.getEncounterId() == null || StringUtils.isEmpty(row.getRoleCode())) {
                continue;
            }
            if (row.getPractitionerId() == null) {
                continue;
            }
            String roleKey = this.participantRoleKey(row.getEncounterId(), row.getRoleCode());
            // 同角色已有活动行则不再用历史 COMPLETED 覆盖。
            if (activeRoleKeys.contains(roleKey)) {
                continue;
            }
            EncounterParticipant previous = lastCompletedByRoleKey.get(roleKey);
            if (previous == null) {
                lastCompletedByRoleKey.put(roleKey, row);
                continue;
            }
            // 多条已完成同角色取 end_time 最大者为末次责任人。
            LocalDateTime rowEnd = row.getEndTime();
            LocalDateTime previousEnd = previous.getEndTime();
            if (rowEnd == null) {
                continue;
            }
            if (previousEnd == null || rowEnd.isAfter(previousEnd)) {
                lastCompletedByRoleKey.put(roleKey, row);
            }
        }
        List<EncounterParticipant> displayRows = new ArrayList<>(activeRows.size() + lastCompletedByRoleKey.size());
        displayRows.addAll(activeRows);
        displayRows.addAll(lastCompletedByRoleKey.values());
        return displayRows;
    }

    /**
     * 批量加载活动态就诊位置实体（占床/活动病区判定与写侧 Pre）。
     *
     * <p>列表展示无活动床/病区时另由上下文回落末次 COMPLETED（见
     * {@link #mapLastCompletedWardLocationIdByEncounterIds} /
     * {@link #mapLastCompletedBedLocationIdByEncounterIds}）。</p>
     *
     * @param encounterIds 就诊主键集合
     * @return 就诊位置实体列表
     */
    public List<EncounterLocation> listActiveEncounterLocationEntitiesByEncounterIds(Collection<Long> encounterIds) {
        LinkedHashSet<Long> distinct = this.distinctNonNullIds(encounterIds);
        if (distinct.isEmpty()) {
            return Collections.emptyList();
        }
        return locationRepository.findAllByEncounterIdInAndStatusEnum(
            distinct, EncounterActivityStatus.ACTIVE.getCode());
    }

    /**
     * 展示用病区位置：优先活动 WARD，否则末次已完成 WARD。
     *
     * @param encounterIds 就诊主键集合
     * @return encounterId → 病区 location_id
     */
    public Map<Long, Long> mapDisplayWardLocationIdByEncounterIds(Collection<Long> encounterIds) {
        LinkedHashSet<Long> distinct = this.distinctNonNullIds(encounterIds);
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> wardByEncounterId = new HashMap<>();
        List<EncounterLocation> activeRows = locationRepository.findAllByEncounterIdInAndStatusEnum(
            distinct, EncounterActivityStatus.ACTIVE.getCode());
        for (EncounterLocation row : activeRows) {
            if (row == null || row.getEncounterId() == null || row.getLocationId() == null) {
                continue;
            }
            // 仅活动病区形态计入当前展示。
            if (!LocationForm.WARD.getCode().equals(row.getFormEnum())) {
                continue;
            }
            wardByEncounterId.putIfAbsent(row.getEncounterId(), row.getLocationId());
        }
        LinkedHashSet<Long> missingEncounterIds = new LinkedHashSet<>();
        for (Long encounterId : distinct) {
            if (!wardByEncounterId.containsKey(encounterId)) {
                missingEncounterIds.add(encounterId);
            }
        }
        if (missingEncounterIds.isEmpty()) {
            return wardByEncounterId;
        }
        // 无活动病区（清床/已离院）：回落末次已完成 WARD。
        Map<Long, Long> lastCompletedWard =
            this.mapLastCompletedWardLocationIdByEncounterIds(missingEncounterIds);
        for (Map.Entry<Long, Long> entry : lastCompletedWard.entrySet()) {
            wardByEncounterId.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return wardByEncounterId;
    }

    /**
     * 展示用床位位置：优先活动 BED，否则末次已完成 BED（不含「未占床」文案装配）。
     *
     * @param encounterIds 就诊主键集合
     * @return encounterId → 床位 location_id
     */
    public Map<Long, Long> mapDisplayBedLocationIdByEncounterIds(Collection<Long> encounterIds) {
        LinkedHashSet<Long> distinct = this.distinctNonNullIds(encounterIds);
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Long> bedByEncounterId = new HashMap<>();
        List<EncounterLocation> activeRows = locationRepository.findAllByEncounterIdInAndStatusEnum(
            distinct, EncounterActivityStatus.ACTIVE.getCode());
        for (EncounterLocation row : activeRows) {
            if (row == null || row.getEncounterId() == null || row.getLocationId() == null) {
                continue;
            }
            // 仅活动床形态计入当前展示。
            if (!LocationForm.BED.getCode().equals(row.getFormEnum())) {
                continue;
            }
            bedByEncounterId.putIfAbsent(row.getEncounterId(), row.getLocationId());
        }
        LinkedHashSet<Long> missingEncounterIds = new LinkedHashSet<>();
        for (Long encounterId : distinct) {
            if (!bedByEncounterId.containsKey(encounterId)) {
                missingEncounterIds.add(encounterId);
            }
        }
        if (missingEncounterIds.isEmpty()) {
            return bedByEncounterId;
        }
        // 无活动床（召回未占床/已清床）：回落末次已完成 BED。
        Map<Long, Long> lastCompletedBed =
            this.mapLastCompletedBedLocationIdByEncounterIds(missingEncounterIds);
        for (Map.Entry<Long, Long> entry : lastCompletedBed.entrySet()) {
            bedByEncounterId.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return bedByEncounterId;
    }

    /**
     * 参与者实体 → 读模型列表。
     *
     * @param rows 参与者实体
     * @return QueryModel 列表
     */
    private List<EncounterParticipantQueryModel> toParticipantQueryModels(List<EncounterParticipant> rows) {
        List<EncounterParticipantQueryModel> models = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return models;
        }
        for (EncounterParticipant row : rows) {
            if (row == null) {
                continue;
            }
            EncounterParticipantQueryModel model = new EncounterParticipantQueryModel();
            model.setParticipantId(row.getId());
            model.setEncounterId(row.getEncounterId());
            model.setRoleCode(row.getRoleCode());
            model.setPractitionerId(row.getPractitionerId());
            model.setStatusEnum(row.getStatusEnum());
            model.setStartTime(row.getStartTime());
            model.setEndTime(row.getEndTime());
            models.add(model);
        }
        return models;
    }

    /**
     * 拼装 encounterId + roleCode 唯一键，供展示参与者按角色去重。
     *
     * @param encounterId 就诊主键
     * @param roleCode    角色编码
     * @return 去重键
     */
    private String participantRoleKey(Long encounterId, String roleCode) {
        return encounterId + "|" + roleCode;
    }

    /**
     * 查询就诊末次已完成病区位置主键（DM-ADM-002 §6.1.25 Pre，AS-IP-NUR-002 ATD-07 已清床轨）。
     *
     * <p>在 {@code adm_encounter_location} 中按 {@code form_enum=WARD}、{@code status_enum=COMPLETED}、
     * {@code end_time} 最大的一条取 {@code location_id}；无匹配时返回空。</p>
     *
     * @param encounterId 住院就诊主键
     * @return 末次已完成病区位置主键
     */
    public Optional<Long> findLastCompletedWardLocationIdByEncounterId(Long encounterId) {
        if (encounterId == null) {
            return Optional.empty();
        }
        Long wardLocationId = this.mapLastCompletedWardLocationIdByEncounterIds(List.of(encounterId)).get(encounterId);
        if (wardLocationId == null) {
            return Optional.empty();
        }
        return Optional.of(wardLocationId);
    }

    /**
     * 批量解析就诊末次已完成病区位置主键（AS-IP-NUR-001 已清床召回列表 QueryModel 投影）。
     *
     * @param encounterIds 住院就诊主键集合
     * @return encounterId → 末次已完成 WARD 位置主键
     */
    public Map<Long, Long> mapLastCompletedWardLocationIdByEncounterIds(Collection<Long> encounterIds) {
        LinkedHashSet<Long> distinct = this.distinctNonNullIds(encounterIds);
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        List<EncounterLocation> rows = locationRepository.findAllByEncounterIdInAndStatusEnum(
            distinct, EncounterActivityStatus.COMPLETED.getCode());
        Map<Long, EncounterLocation> lastCompletedWardByEncounterId = new HashMap<>();
        for (EncounterLocation row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            // 仅统计病区形态轨迹，忽略 HOUSE/BED 层。
            if (!LocationForm.WARD.getCode().equals(row.getFormEnum())) {
                continue;
            }
            if (row.getLocationId() == null) {
                continue;
            }
            EncounterLocation previous = lastCompletedWardByEncounterId.get(row.getEncounterId());
            if (previous == null) {
                lastCompletedWardByEncounterId.put(row.getEncounterId(), row);
                continue;
            }
            // 在多条已完成 WARD 中取 end_time 最大者为末次病区。
            LocalDateTime rowEnd = row.getEndTime();
            LocalDateTime previousEnd = previous.getEndTime();
            if (rowEnd == null) {
                continue;
            }
            if (previousEnd == null || rowEnd.isAfter(previousEnd)) {
                lastCompletedWardByEncounterId.put(row.getEncounterId(), row);
            }
        }
        Map<Long, Long> wardLocationIdByEncounterId = new HashMap<>(lastCompletedWardByEncounterId.size() * 2);
        for (Map.Entry<Long, EncounterLocation> entry : lastCompletedWardByEncounterId.entrySet()) {
            wardLocationIdByEncounterId.put(entry.getKey(), entry.getValue().getLocationId());
        }
        return wardLocationIdByEncounterId;
    }

    /**
     * 批量解析就诊末次已完成床位位置主键（召回/清床未占床轨列表展示「原房-床（未占床）」）。
     *
     * <p>仅本聚合 {@code EncounterLocationRepository}；在 {@code COMPLETED} 轨迹中取
     * {@code form_enum=BED} 且 {@code end_time} 最大的一行。展示文案由
     * {@code EncounterContextQueryService} 再拼病房名。</p>
     *
     * @param encounterIds 住院就诊主键集合
     * @return encounterId → 末次已完成 BED 位置主键
     */
    public Map<Long, Long> mapLastCompletedBedLocationIdByEncounterIds(Collection<Long> encounterIds) {
        LinkedHashSet<Long> distinct = this.distinctNonNullIds(encounterIds);
        if (distinct.isEmpty()) {
            return Collections.emptyMap();
        }
        List<EncounterLocation> rows = locationRepository.findAllByEncounterIdInAndStatusEnum(
            distinct, EncounterActivityStatus.COMPLETED.getCode());
        Map<Long, EncounterLocation> lastCompletedBedByEncounterId = new HashMap<>();
        for (EncounterLocation row : rows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            // 仅统计床位形态轨迹。
            if (!LocationForm.BED.getCode().equals(row.getFormEnum())) {
                continue;
            }
            if (row.getLocationId() == null) {
                continue;
            }
            EncounterLocation previous = lastCompletedBedByEncounterId.get(row.getEncounterId());
            if (previous == null) {
                lastCompletedBedByEncounterId.put(row.getEncounterId(), row);
                continue;
            }
            // 在多条已完成 BED 中取 end_time 最大者为末次归属床。
            LocalDateTime rowEnd = row.getEndTime();
            LocalDateTime previousEnd = previous.getEndTime();
            if (rowEnd == null) {
                continue;
            }
            if (previousEnd == null || rowEnd.isAfter(previousEnd)) {
                lastCompletedBedByEncounterId.put(row.getEncounterId(), row);
            }
        }
        Map<Long, Long> bedLocationIdByEncounterId = new HashMap<>(lastCompletedBedByEncounterId.size() * 2);
        for (Map.Entry<Long, EncounterLocation> entry : lastCompletedBedByEncounterId.entrySet()) {
            bedLocationIdByEncounterId.put(entry.getKey(), entry.getValue().getLocationId());
        }
        return bedLocationIdByEncounterId;
    }

    /**
     * 去重非 null 主键；保持输入迭代顺序。
     *
     * @param ids 原始 id 集合
     * @return LinkedHashSet
     */
    private LinkedHashSet<Long> distinctNonNullIds(Collection<Long> ids) {
        LinkedHashSet<Long> distinct = new LinkedHashSet<>();
        if (ids == null) {
            return distinct;
        }
        for (Long id : ids) {
            if (id != null) {
                distinct.add(id);
            }
        }
        return distinct;
    }

    /**
     * 按主键与就诊类别加载聚合根；不存在或类别不符时区分抛出业务异常。
     *
     * @param encounterId 就诊标识
     * @param classEnum 期望类别编码
     * @param wrongClassErrorCode 类别不符错误码
     * @param wrongClassMessage 类别不符提示
     * @return 非空就诊实体
     * @throws BusinessException 记录不存在或类别不符
     */
    private Encounter mustGetEncounterEntityOfClass(
            Long encounterId,
            String classEnum,
            String wrongClassErrorCode,
            String wrongClassMessage) {
        Optional<Encounter> matched = encounterRepository.findByIdAndClassEnum(encounterId, classEnum);
        if (matched.isPresent()) {
            return matched.get();
        }
        // 区分「无此就诊」与「有就诊但类别不对」，避免住院登记误操作门诊线。
        Optional<Encounter> anyRow = encounterRepository.findById(encounterId);
        if (anyRow.isPresent()) {
            throw new BusinessException(wrongClassErrorCode, wrongClassMessage);
        }
        throw new BusinessException(ErrorCodeConstants.ENCOUNTER_NOT_FOUND, "就诊记录不存在");
    }

    /**
     * 滚费任务：列出已分床且未清床的 IMP 就诊上下文（AS-IP-NUR-013 BR-IP-ARG-008；DM-ADM-002 §6.2.21）。
     *
     * <p>状态含 {@link EncounterStatus#IN_PROGRESS}、{@link EncounterStatus#ON_HOLD}、
     * {@link EncounterStatus#COMPLETED}（已结算待清床）；排除 {@link EncounterStatus#DISCHARGED}。
     * 须存在激活床位轨迹（{@code form_enum=BED}）。读模型含 {@code endTime} 供应用层记进不计出 Gate。
     * 账户 id 由 querysupport 经 Account QS 补齐。</p>
     *
     * @return 滚费候选就诊列表（不含 accountId）；无命中时空列表
     */
    public List<InpatientAutoRollEncounterQueryModel> listInpatientAutoRollEncounterBaseContexts() {
        // Pre：加载未清床 IMP 候选（含已结算待清床的 COMPLETED）。
        Specification<Encounter> specification = (root, criteriaQuery, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.IMP.getCode()),
            root.get(FieldNameConstants.STATUS_ENUM).in(
                EncounterStatus.IN_PROGRESS.getCode(),
                EncounterStatus.ON_HOLD.getCode(),
                EncounterStatus.COMPLETED.getCode()));
        List<Encounter> candidates = this.encounterRepository.findAll(specification);
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }
        LinkedHashSet<Long> candidateIds = new LinkedHashSet<>();
        Map<Long, Encounter> encounterById = new HashMap<>();
        for (Encounter row : candidates) {
            if (row == null || row.getId() == null) {
                continue;
            }
            candidateIds.add(row.getId());
            encounterById.put(row.getId(), row);
        }
        if (candidateIds.isEmpty()) {
            return Collections.emptyList();
        }
        // Pre：收窄至存在激活床位轨迹的就诊（分床后，BR-IP-ARG-008）。
        List<EncounterLocation> activeLocations = this.locationRepository.findAllByEncounterIdInAndStatusEnum(
            candidateIds, EncounterActivityStatus.ACTIVE.getCode());
        Map<Long, Long> bedLocationByEncounterId = new HashMap<>();
        for (EncounterLocation locationRow : activeLocations) {
            if (locationRow == null || locationRow.getEncounterId() == null) {
                continue;
            }
            if (!LocationForm.BED.getCode().equals(locationRow.getFormEnum())) {
                continue;
            }
            if (!bedLocationByEncounterId.containsKey(locationRow.getEncounterId())) {
                bedLocationByEncounterId.put(locationRow.getEncounterId(), locationRow.getLocationId());
            }
        }
        // Pre：首次分床时刻取全部 BED 轨迹最早 start_time（转床不改就诊 start_time，亦不替代本锚点）。
        Map<Long, LocalDateTime> bedAssignedAtByEncounterId = new HashMap<>();
        List<EncounterLocation> allLocations = this.locationRepository.findAllByEncounterIdIn(
            bedLocationByEncounterId.keySet());
        for (EncounterLocation locationRow : allLocations) {
            if (locationRow == null || locationRow.getEncounterId() == null) {
                continue;
            }
            if (!LocationForm.BED.getCode().equals(locationRow.getFormEnum())) {
                continue;
            }
            LocalDateTime startTime = locationRow.getStartTime();
            if (startTime == null) {
                continue;
            }
            Long encounterId = locationRow.getEncounterId();
            LocalDateTime existing = bedAssignedAtByEncounterId.get(encounterId);
            if (existing == null || startTime.isBefore(existing)) {
                bedAssignedAtByEncounterId.put(encounterId, startTime);
            }
        }
        if (bedLocationByEncounterId.isEmpty()) {
            return Collections.emptyList();
        }
        List<InpatientAutoRollEncounterQueryModel> models = new ArrayList<>();
        for (Long encounterId : bedLocationByEncounterId.keySet()) {
            Encounter encounter = encounterById.get(encounterId);
            if (encounter == null) {
                continue;
            }
            InpatientAutoRollEncounterQueryModel model = new InpatientAutoRollEncounterQueryModel();
            model.setEncounterId(encounterId);
            model.setPatientId(encounter.getPatientId());
            model.setOrganizationId(encounter.getOrganizationId());
            model.setStartTime(encounter.getStartTime());
            // 出院日门控（记进不计出）：应用层按 end_time 自然日与 billDate 比较
            model.setEndTime(encounter.getEndTime());
            model.setBedAssignedAt(bedAssignedAtByEncounterId.get(encounterId));
            model.setStatusEnum(encounter.getStatusEnum());
            model.setBedLocationId(bedLocationByEncounterId.get(encounterId));
            models.add(model);
        }
        return models;
    }

    /**
     * 工作量统计：按就诊类别与挂号科室集合解析候选就诊 id（DM-ADM-002 §6.2.30）。
     *
     * <p>无日期窗时仍按历史全量科室就诊返回；报表读侧应优先调用带业务日窗的重载，避免无界 IN。</p>
     *
     * @param encounterClassCode 就诊类别存储码（AMB / IMP）
     * @param organizationIds    挂号科室 id 集合
     * @return 就诊主键列表；参数无效时返回空列表
     */
    public List<Long> listEncounterIdsForPractitionerWorkloadScope(
        String encounterClassCode,
        Collection<Long> organizationIds) {
        return this.listEncounterIdsForPractitionerWorkloadScope(
            encounterClassCode, organizationIds, null, null);
    }

    /**
     * 工作量统计：按就诊类别、挂号科室与业务日窗解析候选就诊 id（仅本聚合 Repository）。
     *
     * <p>日窗语义为就诊时段与 {@code [windowFrom, windowTo]} 相交：
     * {@code startTime <= windowTo} 且（{@code endTime} 为空或 {@code endTime >= windowFrom}）。
     * 任一日窗参数为空时不加时段谓词（兼容旧调用）。</p>
     *
     * @param encounterClassCode 就诊类别存储码（AMB / IMP）
     * @param organizationIds    挂号科室 id 集合
     * @param windowFrom         业务窗起（含）；可空
     * @param windowTo           业务窗止（含）；可空
     * @return 就诊主键列表；参数无效时返回空列表
     */
    public List<Long> listEncounterIdsForPractitionerWorkloadScope(
        String encounterClassCode,
        Collection<Long> organizationIds,
        LocalDateTime windowFrom,
        LocalDateTime windowTo) {
        if (StringUtils.isEmpty(encounterClassCode) || organizationIds == null || organizationIds.isEmpty()) {
            return Collections.emptyList();
        }
        Specification<Encounter> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.CLASS_ENUM), encounterClassCode));
            predicates.add(root.get(FieldNameConstants.ORGANIZATION_ID).in(organizationIds));
            this.appendEncounterActiveWindowPredicates(predicates, root, cb, windowFrom, windowTo);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<Long> encounterIds = new ArrayList<>();
        for (Encounter encounter : encounterRepository.findAll(specification)) {
            if (encounter != null && encounter.getId() != null) {
                encounterIds.add(encounter.getId());
            }
        }
        return encounterIds;
    }

    /**
     * 护士工作量：按就诊类别解析该域就诊 id（无科室收窄；DM-ADM-002 §6.2.32；AS-RPT-002）。
     *
     * <p>无日期窗时仍全历史返回；报表读侧应优先调用带业务日窗的重载。</p>
     *
     * @param encounterClassCode 就诊类别存储码（AMB / IMP）
     * @return 就诊主键列表；参数无效时返回空列表
     */
    public List<Long> listEncounterIdsForNurseWorkloadScope(String encounterClassCode) {
        return this.listEncounterIdsForNurseWorkloadScope(encounterClassCode, null, null);
    }

    /**
     * 护士/耗材报表：按就诊类别与业务日窗解析候选就诊 id（仅本聚合 Repository）。
     *
     * <p>日窗语义同 {@link #listEncounterIdsForPractitionerWorkloadScope} 相交口径；
     * 供 AS-RPT-002 / AS-RPT-005/006 避免按 class 全表扫历史就诊。</p>
     *
     * @param encounterClassCode 就诊类别存储码（AMB / IMP）
     * @param windowFrom         业务窗起（含）；可空
     * @param windowTo           业务窗止（含）；可空
     * @return 就诊主键列表；参数无效时返回空列表
     */
    public List<Long> listEncounterIdsForNurseWorkloadScope(
        String encounterClassCode,
        LocalDateTime windowFrom,
        LocalDateTime windowTo) {
        if (StringUtils.isEmpty(encounterClassCode)) {
            return Collections.emptyList();
        }
        Specification<Encounter> specification = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.CLASS_ENUM), encounterClassCode));
            this.appendEncounterActiveWindowPredicates(predicates, root, cb, windowFrom, windowTo);
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        List<Long> encounterIds = new ArrayList<>();
        for (Encounter encounter : encounterRepository.findAll(specification)) {
            if (encounter != null && encounter.getId() != null) {
                encounterIds.add(encounter.getId());
            }
        }
        return encounterIds;
    }

    /**
     * 追加就诊与业务日窗相交谓词（start ≤ to 且 end 空或 end ≥ from）。
     *
     * @param predicates 已有谓词列表
     * @param root       Encounter root
     * @param cb         CriteriaBuilder
     * @param windowFrom 窗起；空则不加
     * @param windowTo   窗止；空则不加
     */
    private void appendEncounterActiveWindowPredicates(
        List<Predicate> predicates,
        Root<Encounter> root,
        CriteriaBuilder cb,
        LocalDateTime windowFrom,
        LocalDateTime windowTo) {
        if (windowFrom == null || windowTo == null) {
            return;
        }
        // Pre：startTime 空的脏就诊不参与报表 scope，避免无法判定相交
        predicates.add(cb.isNotNull(root.get(FieldNameConstants.START_TIME)));
        predicates.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.START_TIME), windowTo));
        // endTime 空视为仍在院/未结，与窗起之后仍可能有业务
        predicates.add(cb.or(
            cb.isNull(root.get(FieldNameConstants.END_TIME)),
            cb.greaterThanOrEqualTo(root.get(FieldNameConstants.END_TIME), windowFrom)));
    }

    /**
     * 批量加载工作量挂号号源分组所需的就诊上下文（DM-ADM-002 §6.2.31）。
     *
     * @param encounterIds 就诊主键集合
     * @return encounterId → 上下文；未命中 id 不包含在 map 中
     */
    public Map<Long, PractitionerWorkloadEncounterContextQueryModel> mapPractitionerWorkloadEncounterContextByIds(
        Collection<Long> encounterIds) {
        Map<Long, PractitionerWorkloadEncounterContextQueryModel> result = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return result;
        }
        Set<Long> distinctIds = new LinkedHashSet<>();
        for (Long encounterId : encounterIds) {
            if (encounterId != null) {
                distinctIds.add(encounterId);
            }
        }
        if (distinctIds.isEmpty()) {
            return result;
        }
        for (Encounter encounter : encounterRepository.findAllById(distinctIds)) {
            if (encounter == null || encounter.getId() == null || Boolean.TRUE.equals(encounter.getIsDeleted())) {
                continue;
            }
            PractitionerWorkloadEncounterContextQueryModel model = new PractitionerWorkloadEncounterContextQueryModel();
            model.setEncounterId(encounter.getId());
            model.setOrganizationId(encounter.getOrganizationId());
            model.setServiceTypeId(encounter.getServiceTypeId());
            model.setStatusEnum(encounter.getStatusEnum());
            model.setClassEnum(encounter.getClassEnum());
            result.put(encounter.getId(), model);
        }
        return result;
    }

    /**
     * 在候选就诊中筛选活动 {@code REGISTRATION_DOCTOR} 为指定医师的就诊（AS-RPT-001 BR-RPT-WKL-005）。
     *
     * @param candidateEncounterIds 候选就诊
     * @param practitionerId        医师主键
     * @return 命中就诊 id 集合；{@code practitionerId} 为空时返回空集合
     */
    public Set<Long> filterEncounterIdsByActiveRegistrationDoctor(
        Collection<Long> candidateEncounterIds,
        Long practitionerId) {
        if (practitionerId == null || candidateEncounterIds == null || candidateEncounterIds.isEmpty()) {
            return Collections.emptySet();
        }
        Map<Long, Long> doctorByEncounter =
            this.mapActiveRegistrationDoctorByEncounterIds(candidateEncounterIds);
        Set<Long> matched = new HashSet<>();
        for (Map.Entry<Long, Long> entry : doctorByEncounter.entrySet()) {
            if (practitionerId.equals(entry.getValue())) {
                matched.add(entry.getKey());
            }
        }
        return matched;
    }

    /**
     * 批量解析活动 {@code REGISTRATION_DOCTOR}（工作量医生卡片一次分桶；BR-RPT-WKL-005）。
     *
     * @param candidateEncounterIds 候选就诊
     * @return encounterId → 挂号医生主键；无命中不含键
     */
    public Map<Long, Long> mapActiveRegistrationDoctorByEncounterIds(
        Collection<Long> candidateEncounterIds) {
        Map<Long, Long> doctorByEncounter = new HashMap<>();
        if (candidateEncounterIds == null || candidateEncounterIds.isEmpty()) {
            return doctorByEncounter;
        }
        List<EncounterParticipant> participants = participantRepository.findAllByEncounterIdInAndStatusEnum(
            candidateEncounterIds, EncounterActivityStatus.ACTIVE.getCode());
        String registrationDoctorRole = EncounterParticipantRole.REGISTRATION_DOCTOR.getCode();
        for (EncounterParticipant participant : participants) {
            if (participant == null || participant.getEncounterId() == null) {
                continue;
            }
            // 仅 REGISTRATION_DOCTOR；同就诊多条时保留首次（与挂号明细医生维同口径）
            if (!registrationDoctorRole.equals(participant.getRoleCode())) {
                continue;
            }
            if (participant.getPractitionerId() == null) {
                continue;
            }
            if (!doctorByEncounter.containsKey(participant.getEncounterId())) {
                doctorByEncounter.put(participant.getEncounterId(), participant.getPractitionerId());
            }
        }
        return doctorByEncounter;
    }

    /**
     * 在候选就诊中筛选活动接诊医生（{@code ADMITTER}）为指定医师的就诊（AS-RPT-007）。
     *
     * <p>明细医生列与筛选仅认接诊医生；挂到科室未接诊时无 ADMITTER，选该大夫不命中。
     * 工作量挂号人次与本方法同口径（ADMITTER），与门诊挂号明细筛「接诊医生」对齐。</p>
     *
     * @param candidateEncounterIds 候选就诊
     * @param practitionerId        医师主键
     * @return 命中就诊 id 集合；{@code practitionerId} 为空时返回空集合
     */
    public Set<Long> filterEncounterIdsByActiveAdmitterDoctor(
        Collection<Long> candidateEncounterIds,
        Long practitionerId) {
        if (practitionerId == null || candidateEncounterIds == null || candidateEncounterIds.isEmpty()) {
            return Collections.emptySet();
        }
        List<EncounterParticipant> participants = participantRepository.findAllByEncounterIdInAndStatusEnum(
            candidateEncounterIds, EncounterActivityStatus.ACTIVE.getCode());
        Set<Long> matched = new HashSet<>();
        String admitterRole = EncounterParticipantRole.ADMITTER.getCode();
        for (EncounterParticipant participant : participants) {
            if (participant == null || participant.getEncounterId() == null) {
                continue;
            }
            // 仅活动 ADMITTER 参与接诊医生收窄
            if (!admitterRole.equals(participant.getRoleCode())) {
                continue;
            }
            if (practitionerId.equals(participant.getPractitionerId())) {
                matched.add(participant.getEncounterId());
            }
        }
        return matched;
    }

    /**
     * 门诊挂号明细报表明细分页（AS-RPT-007；DM-ADM-002 §6.2.34）。
     *
     * <p>仅 AMB；时间轴 coalesce(startTime, createdAt)；费用性质由应用层 enrich。
     * 仅本聚合 Encounter / Participant 仓储。医生/关键字收窄须预写 {@code resolvedEncounterIds}；
     * 可选 {@code statusEnum} 单值收窄就诊状态。</p>
     *
     * @param query 分页与筛选
     * @return 明细分页；挂号时间倒序
     */
    public Page<OutpatientRegistrationReportLineQueryModel> pageOutpatientRegistrationDetailReport(
        OutpatientRegistrationReportPageQuery query) {
        if (query == null) {
            query = new OutpatientRegistrationReportPageQuery();
        }
        int pageNo = query.springPageIndex();
        int pageSize = query.getBoundedPageSize();
        // Gate：空预解析集合表示关键字/医生无命中，拒查防全表扫
        if (query.getResolvedEncounterIds() != null && query.getResolvedEncounterIds().isEmpty()) {
            Pageable emptyPageable = PageRequest.of(pageNo, pageSize);
            return new PageImpl<>(Collections.emptyList(), emptyPageable, 0L);
        }
        if (query.getResolvedOrganizationIds() != null && query.getResolvedOrganizationIds().isEmpty()) {
            Pageable emptyPageable = PageRequest.of(pageNo, pageSize);
            return new PageImpl<>(Collections.emptyList(), emptyPageable, 0L);
        }
        // Pre：规约过滤 + 页内批量挂挂号医生（展示列，非筛选）
        Specification<Encounter> specification = this.buildOutpatientRegistrationReportSpecification(query);
        Sort sort = Sort.by(
            Sort.Order.desc(FieldNameConstants.START_TIME),
            Sort.Order.desc(FieldNameConstants.ID));
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        Page<Encounter> page = encounterRepository.findAll(specification, pageable);
        Map<Long, Long> doctorByEncounter = this.mapActiveRegistrationDoctorByEncounterIds(
            this.collectEncounterIds(page.getContent()));
        // Asm：本聚合字段直映；医保/自费等由应用层 enrich
        List<OutpatientRegistrationReportLineQueryModel> models = new ArrayList<>();
        for (Encounter row : page.getContent()) {
            models.add(this.toOutpatientRegistrationReportLine(row, doctorByEncounter));
        }
        return new PageImpl<>(models, pageable, page.getTotalElements());
    }

    /**
     * 门诊挂号明细报表明细页脚挂/退统计（AS-RPT-007；对齐门诊挂号当日统计口径）。
     *
     * <p>与明细分页同 AMB/业务日/科室/resolvedEncounterIds/statusEnum 过滤；
     * 挂号排除 {@code PLANNED}/{@code ENTERED_IN_ERROR}；已退仅 {@code CANCELLED}；
     * 有效 = 挂号 − 已退（下限 0）。仅本聚合 Encounter 仓储。</p>
     *
     * @param query 筛选（分页字段忽略）
     * @return 挂号/已退/有效笔数
     */
    public OutpatientRegistrationDetailStatsQueryModel countOutpatientRegistrationDetailReportStats(
        OutpatientRegistrationReportPageQuery query) {
        OutpatientRegistrationDetailStatsQueryModel result = new OutpatientRegistrationDetailStatsQueryModel();
        result.setRegistrationCount(0L);
        result.setCancelledCount(0L);
        result.setActiveCount(0L);
        if (query == null) {
            query = new OutpatientRegistrationReportPageQuery();
        }
        // Gate：空预解析集合表示关键字/医生无命中，拒查 → 统计为 0
        if (query.getResolvedEncounterIds() != null && query.getResolvedEncounterIds().isEmpty()) {
            return result;
        }
        if (query.getResolvedOrganizationIds() != null && query.getResolvedOrganizationIds().isEmpty()) {
            return result;
        }
        Specification<Encounter> baseSpec = this.buildOutpatientRegistrationReportSpecification(query);
        String planned = EncounterStatus.PLANNED.getCode();
        String enteredInError = EncounterStatus.ENTERED_IN_ERROR.getCode();
        String cancelled = EncounterStatus.CANCELLED.getCode();
        // 挂号：排除待挂号与未收费取消，避免冲高「有效」（对齐 getTodayRegisterSummary）
        Specification<Encounter> registrationSpec = baseSpec.and((root, criteriaQuery, cb) -> cb.and(
            cb.notEqual(root.get(FieldNameConstants.STATUS_ENUM), planned),
            cb.notEqual(root.get(FieldNameConstants.STATUS_ENUM), enteredInError)));
        long registrationCount = encounterRepository.count(registrationSpec);
        // 已退：仅退号完成（CANCELLED）
        Specification<Encounter> cancelledSpec = baseSpec.and((root, criteriaQuery, cb) ->
            cb.equal(root.get(FieldNameConstants.STATUS_ENUM), cancelled));
        long cancelledCount = encounterRepository.count(cancelledSpec);
        result.setRegistrationCount(registrationCount);
        result.setCancelledCount(cancelledCount);
        result.setActiveCount(Math.max(0L, registrationCount - cancelledCount));
        return result;
    }

    /**
     * 工作量挂号净人次：按挂号科室 × 号源汇总（AS-RPT-001 **BR-RPT-WKL-009**）。
     *
     * <p>时间轴与净人次对齐 **AS-RPT-007**：{@code coalesce(startTime, createdAt)} 落在业务日闭区间；
     * 有效挂号 +1，{@code CANCELLED}/{@code PLANNED}/{@code ENTERED_IN_ERROR} 不计入。
     * 仅本聚合 {@link EncounterRepository}；禁止经 ChargeItem/Payment 计次。</p>
     *
     * @param encounterClassCode   就诊类别存储码（AMB / IMP）
     * @param organizationIds      挂号科室集合；空则返回空列表
     * @param businessTimeFrom     业务窗起（含）
     * @param businessTimeTo       业务窗止（含）
     * @param resolvedEncounterIds 医生筛预解析就诊；{@code null} 不收窄；空集合拒查返回空
     * @return 科室 × 号源净汇总行
     */
    public List<OutpatientRegistrationReportSummaryRow> summarizePractitionerWorkloadRegistrationByOrgAndServiceType(
        String encounterClassCode,
        Collection<Long> organizationIds,
        LocalDateTime businessTimeFrom,
        LocalDateTime businessTimeTo,
        Collection<Long> resolvedEncounterIds) {
        // Gate：类别与科室为空无法划定 scope
        if (StringUtils.isEmpty(encounterClassCode)
            || organizationIds == null
            || organizationIds.isEmpty()
            || businessTimeFrom == null
            || businessTimeTo == null) {
            return Collections.emptyList();
        }
        // Gate：空预解析集合表示医生筛无命中，拒查防全表扫
        if (resolvedEncounterIds != null && resolvedEncounterIds.isEmpty()) {
            return Collections.emptyList();
        }
        // Pre：与挂号明细同 coalesce 时间轴 + 科室 IN + 可选就诊收窄
        Specification<Encounter> specification = this.buildPractitionerWorkloadRegistrationSpecification(
            encounterClassCode, organizationIds, businessTimeFrom, businessTimeTo, resolvedEncounterIds);
        List<Encounter> rows = encounterRepository.findAll(specification);
        // Asm：按挂号科室 × 号源分桶累加净人次
        Map<String, OutpatientRegistrationReportSummaryRow> bucketByKey = new LinkedHashMap<>();
        for (Encounter row : rows) {
            if (row == null) {
                continue;
            }
            int delta = this.resolveOutpatientRegistrationNetDelta(row.getStatusEnum());
            // 待挂号/退号/错误态不参与净汇总
            if (delta == 0) {
                continue;
            }
            Long organizationId = row.getOrganizationId();
            // 无挂号科室无法落入方案 C 桶
            if (organizationId == null) {
                continue;
            }
            Long serviceTypeId = row.getServiceTypeId();
            String serviceTypeKey = "null";
            if (serviceTypeId != null) {
                serviceTypeKey = String.valueOf(serviceTypeId);
            }
            String bucketKey = organizationId + ":" + serviceTypeKey;
            OutpatientRegistrationReportSummaryRow summary = bucketByKey.get(bucketKey);
            if (summary == null) {
                summary = new OutpatientRegistrationReportSummaryRow();
                summary.setDimensionKey(bucketKey);
                summary.setSummaryDimension(
                    OutpatientRegistrationReportConstants.SUMMARY_DIMENSION_ORGANIZATION);
                summary.setOrganizationId(organizationId);
                summary.setServiceTypeId(serviceTypeId);
                summary.setNetRegistrationCount(0L);
                bucketByKey.put(bucketKey, summary);
            }
            summary.setNetRegistrationCount(summary.getNetRegistrationCount() + delta);
            if (row.getId() != null) {
                summary.getContributingEncounterIds().add(row.getId());
            }
        }
        return new ArrayList<>(bucketByKey.values());
    }

    /**
     * 工作量挂号医生筛候选：按类别 / 挂号科室 / 业务日窗列出就诊主键（对齐 AS-RPT-007 时间轴）。
     *
     * <p>仅本聚合 Encounter 仓储；供应用层再叠 {@link #filterEncounterIdsByActiveRegistrationDoctor}。
     * 可选 {@code statusEnum} 单值收窄，避免接诊医生筛先拉全状态候选再丢弃。</p>
     *
     * @param encounterClassCode 就诊类别存储码（AMB / IMP）
     * @param organizationIds    挂号科室集合
     * @param businessTimeFrom   业务窗起（含）
     * @param businessTimeTo     业务窗止（含）
     * @return 候选就诊主键；参数无效时返回空列表
     */
    public List<Long> listEncounterIdsForRegistrationBusinessWindow(
        String encounterClassCode,
        Collection<Long> organizationIds,
        LocalDateTime businessTimeFrom,
        LocalDateTime businessTimeTo) {
        return this.listEncounterIdsForRegistrationBusinessWindow(
            encounterClassCode, organizationIds, businessTimeFrom, businessTimeTo, null);
    }

    /**
     * 同 {@link #listEncounterIdsForRegistrationBusinessWindow(String, Collection, LocalDateTime, LocalDateTime)}，
     * 并可选按就诊状态单值收窄。
     *
     * @param encounterClassCode 就诊类别存储码（AMB / IMP）
     * @param organizationIds    挂号科室集合
     * @param businessTimeFrom   业务窗起（含）
     * @param businessTimeTo     业务窗止（含）
     * @param statusEnum         就诊状态 code；空不按状态收窄
     * @return 候选就诊主键；参数无效时返回空列表
     */
    public List<Long> listEncounterIdsForRegistrationBusinessWindow(
        String encounterClassCode,
        Collection<Long> organizationIds,
        LocalDateTime businessTimeFrom,
        LocalDateTime businessTimeTo,
        String statusEnum) {
        // Gate：类别与科室为空无法划定候选
        if (StringUtils.isEmpty(encounterClassCode)
            || organizationIds == null
            || organizationIds.isEmpty()
            || businessTimeFrom == null
            || businessTimeTo == null) {
            return Collections.emptyList();
        }
        Specification<Encounter> specification = this.buildPractitionerWorkloadRegistrationSpecification(
            encounterClassCode, organizationIds, businessTimeFrom, businessTimeTo, null);
        String trimmedStatus = StringUtils.trimToNull(statusEnum);
        // 状态单值：与报表明细规约同 equal，收窄医生筛候选体积
        if (trimmedStatus != null) {
            specification = specification.and((root, criteriaQuery, cb) ->
                cb.equal(root.get(FieldNameConstants.STATUS_ENUM), trimmedStatus));
        }
        List<Long> encounterIds = new ArrayList<>();
        for (Encounter encounter : encounterRepository.findAll(specification)) {
            if (encounter != null && encounter.getId() != null) {
                encounterIds.add(encounter.getId());
            }
        }
        return encounterIds;
    }

    /**
     * 构建工作量挂号统计规约：类别 + coalesce 业务日窗 + 科室 IN + 可选就诊收窄。
     *
     * @param encounterClassCode   就诊类别存储码
     * @param organizationIds      挂号科室集合（非空）
     * @param businessTimeFrom     业务窗起（含）
     * @param businessTimeTo       业务窗止（含）
     * @param resolvedEncounterIds 可选就诊主键收窄；null 不加 IN
     * @return JPA 规约
     */
    private Specification<Encounter> buildPractitionerWorkloadRegistrationSpecification(
        String encounterClassCode,
        Collection<Long> organizationIds,
        LocalDateTime businessTimeFrom,
        LocalDateTime businessTimeTo,
        Collection<Long> resolvedEncounterIds) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.CLASS_ENUM), encounterClassCode));
            // 与 AS-RPT-007 同：挂号发生时刻 = coalesce(startTime, createdAt)
            jakarta.persistence.criteria.Expression<LocalDateTime> occurredAt = cb.coalesce(
                root.get(FieldNameConstants.START_TIME),
                root.get(FieldNameConstants.CREATED_AT));
            predicates.add(cb.greaterThanOrEqualTo(occurredAt, businessTimeFrom));
            predicates.add(cb.lessThanOrEqualTo(occurredAt, businessTimeTo));
            predicates.add(root.get(FieldNameConstants.ORGANIZATION_ID).in(organizationIds));
            if (resolvedEncounterIds != null) {
                predicates.add(root.get(FieldNameConstants.ID).in(resolvedEncounterIds));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 门诊挂号明细报表净汇总（按科室或医生 × 号源；DM-ADM-002 §6.2.35）。
     *
     * <p>有效挂号计入净笔数；CANCELLED / PLANNED / ENTERED_IN_ERROR 不计入
     * （退号后状态变为 CANCELLED，不再进入净额，等价冲减）。</p>
     *
     * @param query 筛选 + summaryDimension
     * @return 净汇总行
     */
    public List<OutpatientRegistrationReportSummaryRow> summarizeOutpatientRegistrationByOrgOrDoctorAndServiceType(
        OutpatientRegistrationReportPageQuery query) {
        if (query == null) {
            query = new OutpatientRegistrationReportPageQuery();
        }
        // Gate：空预解析集合拒查
        if (query.getResolvedEncounterIds() != null && query.getResolvedEncounterIds().isEmpty()) {
            return Collections.emptyList();
        }
        if (query.getResolvedOrganizationIds() != null && query.getResolvedOrganizationIds().isEmpty()) {
            return Collections.emptyList();
        }
        // Pre：加载命中就诊；医生维须参与者映射
        Specification<Encounter> specification = this.buildOutpatientRegistrationReportSpecification(query);
        List<Encounter> rows = encounterRepository.findAll(specification);
        String dimension = StringUtils.trimToNull(query.getSummaryDimension());
        if (dimension == null) {
            dimension = OutpatientRegistrationReportConstants.SUMMARY_DIMENSION_ORGANIZATION;
        }
        Map<Long, Long> doctorByEncounter = this.mapActiveAdmitterPractitionerIdByEncounterIds(
            this.collectEncounterIds(rows));
        // Asm：按科室或接诊医生 × 号源分桶累加净笔数
        Map<String, OutpatientRegistrationReportSummaryRow> bucketByKey = new LinkedHashMap<>();
        for (Encounter row : rows) {
            if (row == null) {
                continue;
            }
            int delta = this.resolveOutpatientRegistrationNetDelta(row.getStatusEnum());
            // 待挂号/退号/错误态不参与净汇总
            if (delta == 0) {
                continue;
            }
            String bucketKey = this.resolveOutpatientRegistrationBucketKey(
                row, dimension, doctorByEncounter);
            OutpatientRegistrationReportSummaryRow summary = bucketByKey.get(bucketKey);
            if (summary == null) {
                summary = new OutpatientRegistrationReportSummaryRow();
                summary.setDimensionKey(bucketKey);
                summary.setSummaryDimension(dimension);
                summary.setServiceTypeId(row.getServiceTypeId());
                summary.setNetRegistrationCount(0L);
                // 医生维回填接诊医生；科室维回填 organization_id
                if (OutpatientRegistrationReportConstants.SUMMARY_DIMENSION_DOCTOR.equals(dimension)) {
                    summary.setPractitionerId(doctorByEncounter.get(row.getId()));
                } else {
                    summary.setOrganizationId(row.getOrganizationId());
                }
                bucketByKey.put(bucketKey, summary);
            }
            summary.setNetRegistrationCount(summary.getNetRegistrationCount() + delta);
            // 记下计入净额的就诊，供应用层按号源 ChargeItem 汇总净挂号费
            if (row.getId() != null) {
                summary.getContributingEncounterIds().add(row.getId());
            }
        }
        return new ArrayList<>(bucketByKey.values());
    }

    /**
     * 构建门诊挂号报表规约（AMB + 时间窗 + 科室/就诊 scope + 可选状态）。
     *
     * @param query 筛选；医生收窄须已写入 resolvedEncounterIds；statusEnum 空则不按状态收窄
     * @return JPA 规约
     */
    private Specification<Encounter> buildOutpatientRegistrationReportSpecification(
        OutpatientRegistrationReportPageQuery query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.AMB.getCode()));
            jakarta.persistence.criteria.Expression<LocalDateTime> occurredAt = cb.coalesce(
                root.get(FieldNameConstants.START_TIME),
                root.get(FieldNameConstants.CREATED_AT));
            if (query.getBusinessTimeFrom() != null) {
                predicates.add(cb.greaterThanOrEqualTo(occurredAt, query.getBusinessTimeFrom()));
            }
            if (query.getBusinessTimeTo() != null) {
                predicates.add(cb.lessThanOrEqualTo(occurredAt, query.getBusinessTimeTo()));
            }
            if (query.getOrganizationId() != null) {
                predicates.add(cb.equal(
                    root.get(FieldNameConstants.ORGANIZATION_ID), query.getOrganizationId()));
            } else if (query.getResolvedOrganizationIds() != null) {
                predicates.add(
                    root.get(FieldNameConstants.ORGANIZATION_ID).in(query.getResolvedOrganizationIds()));
            }
            if (query.getResolvedEncounterIds() != null) {
                predicates.add(root.get(FieldNameConstants.ID).in(query.getResolvedEncounterIds()));
            }
            // 就诊状态单值筛选（AS-RPT-007 类型/状态下拉；空不收窄）
            String statusEnum = StringUtils.trimToNull(query.getStatusEnum());
            if (statusEnum != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), statusEnum));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 从就诊实体列表收集主键（供参与者批量查询）。
     */
    private List<Long> collectEncounterIds(List<Encounter> rows) {
        List<Long> ids = new ArrayList<>();
        if (rows == null) {
            return ids;
        }
        for (Encounter row : rows) {
            if (row != null && row.getId() != null) {
                ids.add(row.getId());
            }
        }
        return ids;
    }

    /**
     * 就诊 → 挂号报表明细行；挂号时间与 clinic 列表同 coalesce 口径。
     */
    private OutpatientRegistrationReportLineQueryModel toOutpatientRegistrationReportLine(
        Encounter row,
        Map<Long, Long> doctorByEncounter) {
        OutpatientRegistrationReportLineQueryModel model = new OutpatientRegistrationReportLineQueryModel();
        model.setEncounterId(row.getId());
        model.setPatientId(row.getPatientId());
        model.setBusNo(row.getBusNo());
        model.setOrganizationId(row.getOrganizationId());
        model.setServiceTypeId(row.getServiceTypeId());
        model.setStatusEnum(row.getStatusEnum());
        LocalDateTime occurredAt = row.getStartTime();
        if (occurredAt == null) {
            occurredAt = row.getCreatedAt();
        }
        model.setRegistrationTime(occurredAt);
        model.setRegistrationDoctorId(doctorByEncounter.get(row.getId()));
        return model;
    }

    /**
     * 汇总分桶键：医生维或科室维，均拼接号源类型。
     */
    private String resolveOutpatientRegistrationBucketKey(
        Encounter row,
        String dimension,
        Map<Long, Long> doctorByEncounter) {
        String serviceKey = "null";
        if (row.getServiceTypeId() != null) {
            serviceKey = String.valueOf(row.getServiceTypeId());
        }
        if (OutpatientRegistrationReportConstants.SUMMARY_DIMENSION_DOCTOR.equals(dimension)) {
            Long doctorId = doctorByEncounter.get(row.getId());
            String doctorKey = "null";
            if (doctorId != null) {
                doctorKey = String.valueOf(doctorId);
            }
            return "DOCTOR:" + doctorKey + ":SERVICE:" + serviceKey;
        }
        Long orgId = row.getOrganizationId();
        String orgKey = "null";
        if (orgId != null) {
            orgKey = String.valueOf(orgId);
        }
        return "ORG:" + orgKey + ":SERVICE:" + serviceKey;
    }

    /**
     * 净笔数增量：有效挂号 +1；退号/待挂号/错误态不计
     * （CANCELLED 不入净额，对应「退号冲减」）。
     *
     * @param statusEnum 就诊状态码
     * @return 1 或 0
     */
    private int resolveOutpatientRegistrationNetDelta(String statusEnum) {
        if (StringUtils.isEmpty(statusEnum)) {
            return 0;
        }
        if (EncounterStatus.CANCELLED.getCode().equals(statusEnum)
            || EncounterStatus.PLANNED.getCode().equals(statusEnum)
            || EncounterStatus.ENTERED_IN_ERROR.getCode().equals(statusEnum)) {
            return 0;
        }
        return 1;
    }

    /**
     * 住院患者入出明细报表明细分页（DM-ADM-002 §6.2.36；AS-RPT-010）。
     *
     * <p>仅 IMP；排除 PLANNED/CANCELLED；按入院日/出院日窗与入出状态过滤。
     * 入院病区=最早 WARD；入院科室筛选依赖应用层预写 {@code resolvedAdmissionWardLocationIds}（B.6.4）。
     * 仅本聚合 Encounter / EncounterLocation 仓储。</p>
     *
     * @param query 分页与筛选
     * @return 明细分页；入院日倒序
     */
    public Page<InpatientInOutDetailReportLineQueryModel> pageInpatientInOutDetailReport(
        InpatientInOutDetailReportPageQuery query) {
        if (query == null) {
            query = new InpatientInOutDetailReportPageQuery();
        }
        int pageNo = query.springPageIndex();
        int pageSize = query.getBoundedPageSize();
        Pageable emptyPageable = PageRequest.of(pageNo, pageSize);
        // Gate：空预解析集合表示科室病区/关键字无命中，拒查防全表扫
        if (this.isEmptyResolvedScope(query)) {
            return new PageImpl<>(Collections.emptyList(), emptyPageable, 0L);
        }
        // Pre：按最早 WARD / 未分床科室解析就诊 scope（禁止跨根 Location 表）
        Set<Long> locationScopedEncounterIds = this.resolveInpatientInOutLocationScopedEncounterIds(query);
        if (locationScopedEncounterIds != null && locationScopedEncounterIds.isEmpty()) {
            return new PageImpl<>(Collections.emptyList(), emptyPageable, 0L);
        }
        Specification<Encounter> specification =
            this.buildInpatientInOutDetailReportSpecification(query, locationScopedEncounterIds, true);
        Sort sort = Sort.by(
            Sort.Order.desc(FieldNameConstants.START_TIME),
            Sort.Order.desc(FieldNameConstants.ID));
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);
        Page<Encounter> page = encounterRepository.findAll(specification, pageable);
        // Asm：挂位置链与转科投影
        List<InpatientInOutDetailReportLineQueryModel> models =
            this.toInpatientInOutDetailReportLines(page.getContent());
        return new PageImpl<>(models, pageable, page.getTotalElements());
    }

    /**
     * 住院患者入出明细报表人次计数（DM-ADM-002 §6.2.37；AS-RPT-010）。
     *
     * <p>与明细分页同科室/病区/日期窗；<strong>忽略</strong> {@code movementStatus}，
     * 入院人次=start_time∈窗，出院人次=end_time∈窗，可重叠计。</p>
     *
     * @param query 筛选（分页字段忽略）
     * @return 两侧人次
     */
    public InpatientInOutDetailPersonTimesQueryModel countInpatientInOutDetailPersonTimes(
        InpatientInOutDetailReportPageQuery query) {
        InpatientInOutDetailPersonTimesQueryModel result = new InpatientInOutDetailPersonTimesQueryModel();
        result.setAdmissionPersonTimes(0L);
        result.setDischargePersonTimes(0L);
        if (query == null) {
            query = new InpatientInOutDetailReportPageQuery();
        }
        // Gate：空预解析拒查 → 人次为 0
        if (this.isEmptyResolvedScope(query)) {
            return result;
        }
        Set<Long> locationScopedEncounterIds = this.resolveInpatientInOutLocationScopedEncounterIds(query);
        if (locationScopedEncounterIds != null && locationScopedEncounterIds.isEmpty()) {
            return result;
        }
        // Pre：入院侧 — 强制按 start_time 窗计
        InpatientInOutDetailReportPageQuery admissionQuery = this.copyInpatientInOutQueryForPersonTimes(
            query, InpatientInOutDetailReportConstants.MOVEMENT_STATUS_ADMISSION);
        Specification<Encounter> admissionSpec =
            this.buildInpatientInOutDetailReportSpecification(
                admissionQuery, locationScopedEncounterIds, true);
        long admissionCount = encounterRepository.count(admissionSpec);
        // Pre：出院侧 — 强制按 end_time 窗计
        InpatientInOutDetailReportPageQuery dischargeQuery = this.copyInpatientInOutQueryForPersonTimes(
            query, InpatientInOutDetailReportConstants.MOVEMENT_STATUS_DISCHARGE);
        Specification<Encounter> dischargeSpec =
            this.buildInpatientInOutDetailReportSpecification(
                dischargeQuery, locationScopedEncounterIds, true);
        long dischargeCount = encounterRepository.count(dischargeSpec);
        result.setAdmissionPersonTimes(admissionCount);
        result.setDischargePersonTimes(dischargeCount);
        return result;
    }

    /**
     * 预解析集合是否为空拒查（非 null 且 empty）。
     *
     * @param query 筛选
     * @return true 表示应直接空结果
     */
    private boolean isEmptyResolvedScope(InpatientInOutDetailReportPageQuery query) {
        if (query.getResolvedEncounterIds() != null && query.getResolvedEncounterIds().isEmpty()) {
            return true;
        }
        if (query.getResolvedAdmissionWardLocationIds() != null
            && query.getResolvedAdmissionWardLocationIds().isEmpty()) {
            return true;
        }
        return false;
    }

    /**
     * 复制筛选并覆盖入出状态（人次两侧独立计数）。
     *
     * @param source 原筛选
     * @param movementStatus 目标状态
     * @return 浅拷贝查询
     */
    private InpatientInOutDetailReportPageQuery copyInpatientInOutQueryForPersonTimes(
        InpatientInOutDetailReportPageQuery source,
        String movementStatus) {
        InpatientInOutDetailReportPageQuery copy = new InpatientInOutDetailReportPageQuery();
        copy.setDateFrom(source.getDateFrom());
        copy.setDateTo(source.getDateTo());
        copy.setMovementStatus(movementStatus);
        copy.setOrganizationId(source.getOrganizationId());
        copy.setWardLocationId(source.getWardLocationId());
        copy.setResolvedAdmissionWardLocationIds(source.getResolvedAdmissionWardLocationIds());
        copy.setResolvedEncounterIds(source.getResolvedEncounterIds());
        return copy;
    }

    /**
     * 按入院病区/预解析病区集合解析就诊 id scope；无需位置收窄时返回 null。
     *
     * @param query 筛选
     * @return null=不按位置收窄；空集=无命中；非空=IN 列表
     */
    private Set<Long> resolveInpatientInOutLocationScopedEncounterIds(InpatientInOutDetailReportPageQuery query) {
        Set<Long> targetWardLocationIds = new LinkedHashSet<>();
        List<Long> resolvedWards = query.getResolvedAdmissionWardLocationIds();
        Long singleWardId = query.getWardLocationId();
        if (singleWardId != null && resolvedWards != null) {
            // 科室 + 病区同时选：须交集（病区属于该科）
            boolean wardInResolved = false;
            for (Long wardId : resolvedWards) {
                if (singleWardId.equals(wardId)) {
                    wardInResolved = true;
                    break;
                }
            }
            if (wardInResolved) {
                targetWardLocationIds.add(singleWardId);
            }
        } else if (singleWardId != null) {
            targetWardLocationIds.add(singleWardId);
        } else if (resolvedWards != null) {
            for (Long wardId : resolvedWards) {
                if (wardId != null) {
                    targetWardLocationIds.add(wardId);
                }
            }
        }
        // 未指定病区 scope：不按位置收窄（可仅靠主档 organization_id）
        if (targetWardLocationIds.isEmpty()) {
            // 已选科室预解析病区但与单病区无交集 → 无命中
            if (singleWardId != null && resolvedWards != null) {
                return Collections.emptySet();
            }
            // 仅 organizationId、无病区预解析：返回 null，交由主档 organization_id 谓词
            if (resolvedWards == null && singleWardId == null) {
                return null;
            }
            // resolved 非 null 但上面未填入任何 id（理论上空列表已在 Gate 拒查）
            return Collections.emptySet();
        }
        // Pre：命中目标病区轨迹的就诊候选
        List<EncounterLocation> hitWardRows = locationRepository.findAllByLocationIdInAndFormEnum(
            targetWardLocationIds, LocationForm.WARD.getCode());
        Set<Long> candidateEncounterIds = new LinkedHashSet<>();
        for (EncounterLocation row : hitWardRows) {
            if (row == null || row.getEncounterId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            candidateEncounterIds.add(row.getEncounterId());
        }
        Set<Long> allowed = new LinkedHashSet<>();
        if (!candidateEncounterIds.isEmpty()) {
            Map<Long, Long> earliestWardByEncounter =
                this.mapEarliestWardLocationIdByEncounterIds(candidateEncounterIds);
            for (Map.Entry<Long, Long> entry : earliestWardByEncounter.entrySet()) {
                // 仅最早入院病区命中目标集合才算入院病区筛中
                if (entry.getValue() != null && targetWardLocationIds.contains(entry.getValue())) {
                    allowed.add(entry.getKey());
                }
            }
        }
        // 入院科室 + 未分床：无 WARD 轨迹且主档 organization_id 匹配（未再收窄到具体病区时）
        if (query.getOrganizationId() != null && singleWardId == null) {
            Set<Long> bedlessIds = this.listBedlessInpatientEncounterIdsMatchingOrg(query);
            allowed.addAll(bedlessIds);
        }
        return allowed;
    }

    /**
     * 批量解析各就诊最早 WARD 的 location_id（按 start_time，再 id）。
     *
     * @param encounterIds 就诊主键
     * @return encounterId → 最早病区 location_id
     */
    private Map<Long, Long> mapEarliestWardLocationIdByEncounterIds(Collection<Long> encounterIds) {
        Map<Long, Long> earliestWardId = new HashMap<>();
        Map<Long, LocalDateTime> earliestStart = new HashMap<>();
        Map<Long, Long> earliestRowId = new HashMap<>();
        if (encounterIds == null || encounterIds.isEmpty()) {
            return earliestWardId;
        }
        List<EncounterLocation> allRows = locationRepository.findAllByEncounterIdIn(encounterIds);
        String wardForm = LocationForm.WARD.getCode();
        for (EncounterLocation row : allRows) {
            if (row == null || row.getEncounterId() == null || row.getLocationId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(row.getIsDeleted())) {
                continue;
            }
            if (!wardForm.equals(row.getFormEnum())) {
                continue;
            }
            Long encounterId = row.getEncounterId();
            LocalDateTime start = row.getStartTime();
            Long existingStartKey = earliestRowId.get(encounterId);
            if (existingStartKey == null) {
                earliestWardId.put(encounterId, row.getLocationId());
                earliestStart.put(encounterId, start);
                earliestRowId.put(encounterId, row.getId());
                continue;
            }
            LocalDateTime previousStart = earliestStart.get(encounterId);
            boolean replace = false;
            // 更早 start_time 优先；同刻按轨迹行 id 更小
            if (start != null && previousStart == null) {
                replace = true;
            } else if (start != null && previousStart != null && start.isBefore(previousStart)) {
                replace = true;
            } else if (start != null && previousStart != null && start.equals(previousStart)) {
                if (row.getId() != null && row.getId() < existingStartKey) {
                    replace = true;
                }
            } else if (start == null && previousStart == null) {
                if (row.getId() != null && row.getId() < existingStartKey) {
                    replace = true;
                }
            }
            if (replace) {
                earliestWardId.put(encounterId, row.getLocationId());
                earliestStart.put(encounterId, start);
                earliestRowId.put(encounterId, row.getId());
            }
        }
        return earliestWardId;
    }

    /**
     * 列出时间窗内、指定入院科室、尚无 WARD 轨迹的住院就诊（待入科未分床）。
     *
     * @param query 含 organizationId 与日期窗
     * @return 就诊主键集合
     */
    private Set<Long> listBedlessInpatientEncounterIdsMatchingOrg(InpatientInOutDetailReportPageQuery query) {
        Set<Long> result = new LinkedHashSet<>();
        if (query.getOrganizationId() == null) {
            return result;
        }
        // Pre：先按主档科室 + 入出时间窗拉候选，再剔除已有 WARD 者
        Specification<Encounter> specification =
            this.buildInpatientInOutDetailReportSpecification(query, null, true);
        List<Encounter> candidates = encounterRepository.findAll(specification);
        if (candidates.isEmpty()) {
            return result;
        }
        List<Long> candidateIds = this.collectEncounterIds(candidates);
        Map<Long, Long> earliestWard = this.mapEarliestWardLocationIdByEncounterIds(candidateIds);
        for (Encounter row : candidates) {
            if (row == null || row.getId() == null) {
                continue;
            }
            // 主档科室须等于入院科室筛选项
            if (!query.getOrganizationId().equals(row.getOrganizationId())) {
                continue;
            }
            if (earliestWard.containsKey(row.getId())) {
                continue;
            }
            result.add(row.getId());
        }
        return result;
    }

    /**
     * 构建住院入出明细规约。
     *
     * @param query 筛选
     * @param locationScopedEncounterIds 位置 scope；null 表示不追加
     * @param applyMovementStatus 是否应用 movementStatus 时间谓词
     * @return JPA 规约
     */
    private Specification<Encounter> buildInpatientInOutDetailReportSpecification(
        InpatientInOutDetailReportPageQuery query,
        Set<Long> locationScopedEncounterIds,
        boolean applyMovementStatus) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.CLASS_ENUM), EncounterClass.IMP.getCode()));
            // 排除未登记与取消登记
            predicates.add(cb.notEqual(
                root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.PLANNED.getCode()));
            predicates.add(cb.notEqual(
                root.get(FieldNameConstants.STATUS_ENUM), EncounterStatus.CANCELLED.getCode()));
            if (applyMovementStatus) {
                predicates.add(this.buildInpatientInOutMovementPredicate(root, cb, query));
            }
            // 无病区预解析时：可直接按主档科室筛（从未转科/待入科）
            if (locationScopedEncounterIds == null && query.getOrganizationId() != null) {
                predicates.add(cb.equal(
                    root.get(FieldNameConstants.ORGANIZATION_ID), query.getOrganizationId()));
            }
            if (locationScopedEncounterIds != null) {
                predicates.add(root.get(FieldNameConstants.ID).in(locationScopedEncounterIds));
            }
            if (query.getResolvedEncounterIds() != null) {
                predicates.add(root.get(FieldNameConstants.ID).in(query.getResolvedEncounterIds()));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    /**
     * 入出状态时间谓词：入院/出院/全部并集。
     *
     * @param root 根
     * @param cb 构造器
     * @param query 筛选
     * @return 谓词
     */
    private Predicate buildInpatientInOutMovementPredicate(
        Root<Encounter> root,
        CriteriaBuilder cb,
        InpatientInOutDetailReportPageQuery query) {
        String status = StringUtils.trimToNull(query.getMovementStatus());
        if (status == null) {
            status = InpatientInOutDetailReportConstants.MOVEMENT_STATUS_ALL;
        }
        Predicate admissionWindow = this.buildStartTimeWindowPredicate(root, cb, query);
        Predicate dischargeWindow = this.buildEndTimeWindowPredicate(root, cb, query);
        if (InpatientInOutDetailReportConstants.MOVEMENT_STATUS_ADMISSION.equals(status)) {
            return admissionWindow;
        }
        if (InpatientInOutDetailReportConstants.MOVEMENT_STATUS_DISCHARGE.equals(status)) {
            return dischargeWindow;
        }
        // 全部：入院窗 ∪ 出院窗
        return cb.or(admissionWindow, dischargeWindow);
    }

    /**
     * 入院日闭区间谓词（start_time）。
     *
     * @param root 根
     * @param cb 构造器
     * @param query 日期窗
     * @return 谓词；未给日期时恒真
     */
    private Predicate buildStartTimeWindowPredicate(
        Root<Encounter> root,
        CriteriaBuilder cb,
        InpatientInOutDetailReportPageQuery query) {
        List<Predicate> parts = new ArrayList<>();
        parts.add(cb.isNotNull(root.get(FieldNameConstants.START_TIME)));
        if (query.getDateFrom() != null) {
            parts.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.START_TIME), query.getDateFrom()));
        }
        if (query.getDateTo() != null) {
            parts.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.START_TIME), query.getDateTo()));
        }
        return cb.and(parts.toArray(new Predicate[0]));
    }

    /**
     * 出院日闭区间谓词（end_time）。
     *
     * @param root 根
     * @param cb 构造器
     * @param query 日期窗
     * @return 谓词；未给日期时仅要求 end_time 非空
     */
    private Predicate buildEndTimeWindowPredicate(
        Root<Encounter> root,
        CriteriaBuilder cb,
        InpatientInOutDetailReportPageQuery query) {
        List<Predicate> parts = new ArrayList<>();
        parts.add(cb.isNotNull(root.get(FieldNameConstants.END_TIME)));
        if (query.getDateFrom() != null) {
            parts.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.END_TIME), query.getDateFrom()));
        }
        if (query.getDateTo() != null) {
            parts.add(cb.lessThanOrEqualTo(root.get(FieldNameConstants.END_TIME), query.getDateTo()));
        }
        return cb.and(parts.toArray(new Predicate[0]));
    }

    /**
     * 就诊列表装配入出明细行（位置链 + 转科）。
     *
     * @param rows 就诊实体
     * @return 行投影
     */
    private List<InpatientInOutDetailReportLineQueryModel> toInpatientInOutDetailReportLines(
        List<Encounter> rows) {
        List<InpatientInOutDetailReportLineQueryModel> models = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            return models;
        }
        List<Long> encounterIds = this.collectEncounterIds(rows);
        List<EncounterLocation> allLocations = locationRepository.findAllByEncounterIdIn(encounterIds);
        Map<Long, List<EncounterLocation>> locationsByEncounter = new HashMap<>();
        for (EncounterLocation location : allLocations) {
            if (location == null || location.getEncounterId() == null) {
                continue;
            }
            if (Boolean.TRUE.equals(location.getIsDeleted())) {
                continue;
            }
            locationsByEncounter
                .computeIfAbsent(location.getEncounterId(), ignored -> new ArrayList<>())
                .add(location);
        }
        Map<Long, Long> earliestWardByEncounter = this.mapEarliestWardLocationIdByEncounterIds(encounterIds);
        for (Encounter row : rows) {
            if (row == null) {
                continue;
            }
            List<EncounterLocation> locationRows = locationsByEncounter.get(row.getId());
            if (locationRows == null) {
                locationRows = Collections.emptyList();
            }
            models.add(this.toInpatientInOutDetailReportLine(
                row, locationRows, earliestWardByEncounter.get(row.getId())));
        }
        return models;
    }

    /**
     * 单行装配：入院病区、当前/末次床位链、转科后病区序列。
     *
     * @param row 就诊
     * @param locationRows 该就诊位置轨迹
     * @param admissionWardLocationId 最早病区
     * @return 行模型
     */
    private InpatientInOutDetailReportLineQueryModel toInpatientInOutDetailReportLine(
        Encounter row,
        List<EncounterLocation> locationRows,
        Long admissionWardLocationId) {
        InpatientInOutDetailReportLineQueryModel model = new InpatientInOutDetailReportLineQueryModel();
        model.setEncounterId(row.getId());
        model.setBusNo(row.getBusNo());
        model.setPatientId(row.getPatientId());
        model.setStatusEnum(row.getStatusEnum());
        model.setStartTime(row.getStartTime());
        model.setEndTime(row.getEndTime());
        model.setAdmissionOrganizationId(row.getOrganizationId());
        model.setAdmissionWardLocationId(admissionWardLocationId);
        model.setCurrentWardLocationId(
            this.resolveCurrentOrLastLocationId(locationRows, LocationForm.WARD.getCode()));
        model.setCurrentHouseLocationId(
            this.resolveCurrentOrLastLocationId(locationRows, LocationForm.HOUSE.getCode()));
        model.setCurrentBedLocationId(
            this.resolveCurrentOrLastLocationId(locationRows, LocationForm.BED.getCode()));
        List<Long> transferTargets = this.resolveTransferTargetWardLocationIds(
            locationRows, admissionWardLocationId);
        model.setTransferTargetWardLocationIds(transferTargets);
        model.setTransferred(!transferTargets.isEmpty());
        return model;
    }

    /**
     * 解析当前活动或末次已收口位置 id。
     *
     * @param locationRows 轨迹
     * @param formEnum 形态
     * @return location_id；无则 null
     */
    private Long resolveCurrentOrLastLocationId(List<EncounterLocation> locationRows, String formEnum) {
        EncounterLocation active = null;
        EncounterLocation lastCompleted = null;
        for (EncounterLocation row : locationRows) {
            if (row == null || !formEnum.equals(row.getFormEnum())) {
                continue;
            }
            if (EncounterActivityStatus.ACTIVE.getCode().equals(row.getStatusEnum())) {
                active = row;
                break;
            }
            if (EncounterActivityStatus.COMPLETED.getCode().equals(row.getStatusEnum())) {
                if (lastCompleted == null) {
                    lastCompleted = row;
                    continue;
                }
                LocalDateTime previousEnd = lastCompleted.getEndTime();
                LocalDateTime currentEnd = row.getEndTime();
                if (currentEnd != null && previousEnd == null) {
                    lastCompleted = row;
                } else if (currentEnd != null && previousEnd != null && currentEnd.isAfter(previousEnd)) {
                    lastCompleted = row;
                } else if (currentEnd != null && previousEnd != null && currentEnd.equals(previousEnd)) {
                    if (row.getId() != null && lastCompleted.getId() != null
                        && row.getId() > lastCompleted.getId()) {
                        lastCompleted = row;
                    }
                }
            }
        }
        if (active != null) {
            return active.getLocationId();
        }
        if (lastCompleted != null) {
            return lastCompleted.getLocationId();
        }
        return null;
    }

    /**
     * 转科后病区序列：按 start_time 升序的 WARD location_id，去掉最早入院病区后的其余去重保序。
     *
     * @param locationRows 轨迹
     * @param admissionWardLocationId 最早入院病区
     * @return 转往病区 id 列表
     */
    private List<Long> resolveTransferTargetWardLocationIds(
        List<EncounterLocation> locationRows,
        Long admissionWardLocationId) {
        // 无入院病区基线则无法判定转科后病区
        if (admissionWardLocationId == null) {
            return new ArrayList<>();
        }
        List<EncounterLocation> wardRows = new ArrayList<>();
        String wardForm = LocationForm.WARD.getCode();
        for (EncounterLocation row : locationRows) {
            if (row == null || row.getLocationId() == null) {
                continue;
            }
            if (!wardForm.equals(row.getFormEnum())) {
                continue;
            }
            wardRows.add(row);
        }
        wardRows.sort((left, right) -> {
            LocalDateTime leftStart = left.getStartTime();
            LocalDateTime rightStart = right.getStartTime();
            if (leftStart == null && rightStart == null) {
                Long leftId = left.getId();
                Long rightId = right.getId();
                if (leftId == null || rightId == null) {
                    return 0;
                }
                return Long.compare(leftId, rightId);
            }
            if (leftStart == null) {
                return 1;
            }
            if (rightStart == null) {
                return -1;
            }
            int cmp = leftStart.compareTo(rightStart);
            if (cmp != 0) {
                return cmp;
            }
            Long leftId = left.getId();
            Long rightId = right.getId();
            if (leftId == null || rightId == null) {
                return 0;
            }
            return Long.compare(leftId, rightId);
        });
        List<Long> targets = new ArrayList<>();
        boolean skippedAdmission = false;
        for (EncounterLocation row : wardRows) {
            Long locationId = row.getLocationId();
            if (!skippedAdmission) {
                if (admissionWardLocationId.equals(locationId)) {
                    skippedAdmission = true;
                }
                continue;
            }
            if (targets.contains(locationId)) {
                continue;
            }
            // 与入院病区相同的再次出现不计转科目标
            if (admissionWardLocationId.equals(locationId)) {
                continue;
            }
            targets.add(locationId);
        }
        return targets;
    }
}
