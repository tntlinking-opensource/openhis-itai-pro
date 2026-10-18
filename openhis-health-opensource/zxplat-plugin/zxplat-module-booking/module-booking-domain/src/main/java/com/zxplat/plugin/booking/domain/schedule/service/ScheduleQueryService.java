/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.schedule.service;


import com.zxplat.domain.share.constants.FieldNameConstants;
import com.zxplat.domain.share.utils.KeywordExactFirstOrderSupport;
import com.zxplat.domain.share.utils.SearchCodeUtil;
import com.zxplat.domain.share.constants.ErrorCodeConstants;
import com.zxplat.domain.share.enums.ScheduleStatus;
import com.zxplat.plugin.booking.domain.schedule.entity.Schedule;
import com.zxplat.plugin.booking.domain.schedule.entity.ScheduleActor;
import com.zxplat.domain.share.enums.ScheduleActorKind;
import com.zxplat.plugin.booking.domain.schedule.queries.CalendarScheduleQuery;
import com.zxplat.plugin.booking.domain.schedule.queries.SchedulePageQuery;
import com.zxplat.plugin.booking.domain.schedule.queries.model.ScheduleActorDateModel;
import com.zxplat.plugin.booking.domain.schedule.queries.model.ScheduleActorModel;
import com.zxplat.plugin.booking.domain.schedule.queries.model.ScheduleCalendarItemModel;
import com.zxplat.plugin.booking.domain.schedule.queries.model.ScheduleDetailModel;
import com.zxplat.plugin.booking.domain.schedule.queries.model.ScheduleSubstituteInfoModel;
import com.zxplat.plugin.booking.domain.schedule.repository.ScheduleActorRepository;
import com.zxplat.plugin.booking.domain.schedule.repository.ScheduleRepository;
import com.zxplat.plugin.booking.domain.slot.entity.Slot;
import com.zxplat.plugin.booking.domain.slot.repository.SlotRepository;
import com.zxplat.framework.core.application.exception.BusinessException;
import com.zxplat.framework.core.application.util.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 号源日程读侧查询服务（DM-WKF-009 §6.2）。
 *
 * <p>聚合边界：只读 {@link Schedule}、{@link ScheduleActor}、{@link Slot}，不写库、不发领域事件。</p>
 * <p>职责：详情/日历列表、按参与者解析当日日程、替诊信息批量解析、机构下启用日程分页。</p>
 * <p>过滤口径：软删行排除；日历须同时满足规划窗口与起止均非空（无窗口草稿不占格，SCH-01）。</p>
 */
@Service
@RequiredArgsConstructor
public class ScheduleQueryService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleActorRepository scheduleActorRepository;
    private final SlotRepository slotRepository;


    /**
     * 按主键加载日程及参与人列表；不存在抛错。
     *
     * @param id 日程主键
     * @return 详情模型
     * @throws BusinessException 日程不存在
     */
    public ScheduleDetailModel loadScheduleById(Long id) {
        Schedule schedule = scheduleRepository.findById(id).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.SCHEDULE_NOT_FOUND, "号源日程不存在"));
        List<ScheduleActor> actors = scheduleActorRepository.findByScheduleIdOrderBySortNoAsc(schedule.getId());
        return this.toDetailModel(schedule, actors);
    }

    /**
     * 按主键加载日历项读模型（含规划窗口与参与者）。
     */
    public ScheduleCalendarItemModel loadCalendarItemById(Long scheduleId) {
        if (scheduleId == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        Schedule schedule = scheduleRepository.findById(scheduleId).orElseThrow(
            () -> new BusinessException(ErrorCodeConstants.SCHEDULE_NOT_FOUND, "号源日程不存在"));
        List<ScheduleActor> actors = scheduleActorRepository.findByScheduleIdOrderBySortNoAsc(schedule.getId());
        return this.toCalendarItemModel(schedule, actors);
    }

    /**
     * 解析某执业者在指定日历日、指定机构下可排班的日程集合（规划窗口与该自然日有重叠即可）。
     *
     * <p>医师维度：Actor 原出诊医师与 {@code substitutePractitionerId} 顶班替诊医师均命中；
     * 替诊不改 Actor，若只按 Actor 查，挂号台按有效接诊医师筛号会空结果。</p>
     *
     * @param actorType 参与者类型编码
     * @param actorId 参与者主键（医护人员 adm_practitioner.id，非 userId）
     * @param date 日历日
     * @param organizationId 机构主键；非空时仅保留该科室下的日程，与号源分页机构口径一致
     * @return 日程摘要列表；入参不完整或无参与/替诊关系时为空
     */
    public List<ScheduleActorDateModel> findSchedulesByActorAndDate(
        Integer actorType, Long actorId, LocalDate date, Long organizationId) {
        // 入参不完整时返回空表，避免误查全量。
        if (actorType == null || actorId == null || date == null) {
            return new ArrayList<>();
        }
        List<ScheduleActor> actorRows = scheduleActorRepository.findByActorTypeAndActorIdOrderByScheduleIdAsc(actorType, actorId);

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        // 去重保序：先 Actor 关联，再并入「该医师为顶班替诊」的日程。
        Set<Long> scheduleIdSet = new LinkedHashSet<>();
        for (ScheduleActor row : actorRows) {
            if (row.getScheduleId() != null) {
                scheduleIdSet.add(row.getScheduleId());
            }
        }
        // 医师筛号：替诊只写顶班字段，须把 substitute 命中日程纳入，否则放号后点替诊医师看不到号。
        if (this.isPractitionerActorType(actorType)) {
            scheduleIdSet.addAll(this.findScheduleIdsBySubstitutePractitioner(actorId));
        }
        if (scheduleIdSet.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> scheduleIds = new ArrayList<>(scheduleIdSet);

        Specification<Schedule> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(root.get(FieldNameConstants.ID).in(scheduleIds));
            // 规划窗口与该自然日有重叠（与周历 listSchedulesForCalendar 口径一致；不要求覆盖全天）。
            jakarta.persistence.criteria.Predicate horizonOverlaps = cb.and(
                cb.or(cb.isNull(root.get(FieldNameConstants.PLANNING_HORIZON_START)),
                    cb.lessThan(root.get(FieldNameConstants.PLANNING_HORIZON_START), dayEnd)),
                cb.or(cb.isNull(root.get(FieldNameConstants.PLANNING_HORIZON_END)),
                    cb.greaterThan(root.get(FieldNameConstants.PLANNING_HORIZON_END), dayStart))
            );
            predicates.add(horizonOverlaps);
            // 号源日历按登录科室查：医师参与者可能挂多科室日程，须与 organizationId 求交。
            if (organizationId != null) {
                predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        List<Schedule> schedules = scheduleRepository.findAll(specification);

        List<ScheduleActorDateModel> result = new ArrayList<>();
        for (Schedule schedule : schedules) {
            ScheduleActorDateModel model = new ScheduleActorDateModel();
            model.setScheduleId(schedule.getId());
            model.setActorType(String.valueOf(actorType));
            model.setActorId(actorId);
            result.add(model);
        }
        return result;
    }

    /**
     * 按日历可见区间列出机构下日程（含草稿/启用/停用），支持参与者组合收窄。
     *
     * @param query 日历查询条件
     * @return 日程列表；无匹配时为空表
     * @throws BusinessException 机构或日期区间非法时
     */
    public List<ScheduleCalendarItemModel> listSchedulesForCalendar(CalendarScheduleQuery query) {
        // Gate：机构与日期区间必填，否则无法构造有效日历窗口。
        if (query == null || query.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        if (query.getRangeStart() == null || query.getRangeEnd() == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "rangeStart/rangeEnd 不能为空");
        }
        if (query.getRangeEnd().isBefore(query.getRangeStart())) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "rangeEnd 不能早于 rangeStart");
        }

        LocalDateTime windowStart = query.getRangeStart().atStartOfDay();
        LocalDateTime windowEndExclusive = query.getRangeEnd().plusDays(1).atStartOfDay();
        // Pre：按医师/诊室/号源类型组合求交集，无筛选时返回 null 表示不追加 IN。
        Set<Long> actorFilteredScheduleIds = this.resolveActorFilteredScheduleIds(query);

        Specification<Schedule> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), query.getOrganizationId()));
            predicates.add(cb.notEqual(root.get(FieldNameConstants.STATUS_ENUM), ScheduleStatus.RETIRED.getCode()));
            // 规划窗口与查询区间有重叠即可出现在日历。
            jakarta.persistence.criteria.Predicate horizonOverlaps = cb.and(
                cb.or(cb.isNull(root.get(FieldNameConstants.PLANNING_HORIZON_START)),
                    cb.lessThan(root.get(FieldNameConstants.PLANNING_HORIZON_START), windowEndExclusive)),
                cb.or(cb.isNull(root.get(FieldNameConstants.PLANNING_HORIZON_END)),
                    cb.greaterThan(root.get(FieldNameConstants.PLANNING_HORIZON_END), windowStart))
            );
            predicates.add(horizonOverlaps);
            // 无规划窗口的草稿不占周历格（起止均须非空）。
            predicates.add(cb.or(
                cb.and(
                    cb.isNotNull(root.get(FieldNameConstants.PLANNING_HORIZON_START)),
                    cb.isNotNull(root.get(FieldNameConstants.PLANNING_HORIZON_END))
                )
            ));
            if (actorFilteredScheduleIds != null) {
                // 参与者组合无交集时强制空结果。
                if (actorFilteredScheduleIds.isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get(FieldNameConstants.ID).in(actorFilteredScheduleIds));
                }
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        List<Schedule> schedules = scheduleRepository.findAll(specification);
        // Asm：逐条加载参与者并映射为日历读模型。
        List<ScheduleCalendarItemModel> result = new ArrayList<>();
        for (Schedule schedule : schedules) {
            List<ScheduleActor> actors = scheduleActorRepository.findByScheduleIdOrderBySortNoAsc(schedule.getId());
            result.add(this.toCalendarItemModel(schedule, actors));
        }
        return result;
    }

    /**
     * 指定日期是否已存在同 T2 来源且未废弃的正式日程（用于模板批量生成去重）。
     *
     * @param organizationId 机构主键
     * @param sourceScheduleTemplateId 来源 T2 主键
     * @param date 目标日期
     * @return 已存在则 true
     */
    public boolean existsNonRetiredScheduleForTemplateOnDay(
        Long organizationId, Long sourceScheduleTemplateId, LocalDate date) {
        if (organizationId == null || sourceScheduleTemplateId == null || date == null) {
            return false;
        }
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEndExclusive = date.plusDays(1).atStartOfDay();
        Specification<Schedule> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId));
            predicates.add(cb.equal(root.get("sourceScheduleTemplateId"), sourceScheduleTemplateId));
            predicates.add(cb.notEqual(root.get(FieldNameConstants.STATUS_ENUM), ScheduleStatus.RETIRED.getCode()));
            jakarta.persistence.criteria.Predicate horizonOverlaps = cb.and(
                cb.or(cb.isNull(root.get(FieldNameConstants.PLANNING_HORIZON_START)),
                    cb.lessThan(root.get(FieldNameConstants.PLANNING_HORIZON_START), dayEndExclusive)),
                cb.or(cb.isNull(root.get(FieldNameConstants.PLANNING_HORIZON_END)),
                    cb.greaterThan(root.get(FieldNameConstants.PLANNING_HORIZON_END), dayStart))
            );
            predicates.add(horizonOverlaps);
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return scheduleRepository.count(specification) > 0;
    }

    /**
     * 机构维度分页查询「启用」状态号源日程，可按名称关键字过滤。
     *
     * @param organizationId 机构标识
     * @param query 可选关键字条件
     * @param pageable 分页
     * @return 分页详情（每条带参与人）
     */
    public Page<ScheduleDetailModel> pageActiveSchedulesByOrganization(Long organizationId, SchedulePageQuery query, Pageable pageable) {
        if (organizationId == null) {
            throw new BusinessException(ErrorCodeConstants.SCHEDULE_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        // 日程名称关键字：空白不做模糊；非空时 Spec 精确优先（B.2.1）
        String trimmedKeyword = null;
        if (query != null && StringUtils.isNotEmpty(query.getKeyword())) {
            trimmedKeyword = query.getKeyword().trim();
        }
        String keywordForSpec = trimmedKeyword;
        // 关键字非空时剥掉 Pageable Sort，保留 Spec 内精确优先 ORDER BY
        Pageable effectivePageable = this.unsortPageableIfKeyword(StringUtils.isNotEmpty(trimmedKeyword), pageable);
        Specification<Schedule> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId));
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), ScheduleStatus.ACTIVE.getCode()));
            // 按日程名称模糊；全等名称须排在仅子串命中之前
            if (StringUtils.isNotEmpty(keywordForSpec)) {
                String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(keywordForSpec);
                predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc));
                KeywordExactFirstOrderSupport.applyExactFirstOrder(
                    cq,
                    cb,
                    keywordForSpec,
                    List.of(cb.desc(root.get(FieldNameConstants.ID))),
                    root.get(FieldNameConstants.NAME));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return scheduleRepository.findAll(specification, effectivePageable).map(s -> {
            List<ScheduleActor> actors = scheduleActorRepository.findByScheduleIdOrderBySortNoAsc(s.getId());
            return this.toDetailModel(s, actors);
        });
    }

    /**
     * 关键字非空且 Pageable 带 Sort 时剥掉 Sort，避免覆盖 Specification 精确优先 ORDER BY。
     *
     * @param keywordPresent 是否带非空关键字
     * @param pageable       原始分页
     * @return 关键字场景下的无 Sort Pageable；否则原 pageable
     */
    private Pageable unsortPageableIfKeyword(boolean keywordPresent, Pageable pageable) {
        if (!keywordPresent || pageable == null) {
            return pageable;
        }
        if (!pageable.getSort().isSorted()) {
            return pageable;
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    /**
     * 解析参与者组合过滤后的日程主键集合；无参与者过滤时返回 {@code null} 表示不追加 IN 条件。
     */
    private Set<Long> resolveActorFilteredScheduleIds(CalendarScheduleQuery query) {
        List<Set<Long>> actorSets = new ArrayList<>();
        if (query.getPractitionerId() != null) {
            actorSets.add(this.scheduleIdsForActor(ScheduleActorKind.PRACTITIONER, query.getPractitionerId()));
        }
        if (query.getLocationId() != null) {
            actorSets.add(this.scheduleIdsForActor(ScheduleActorKind.LOCATION, query.getLocationId()));
        }
        if (query.getHealthcareServiceId() != null) {
            actorSets.add(this.scheduleIdsForActor(ScheduleActorKind.HEALTHCARE_SERVICE, query.getHealthcareServiceId()));
        }
        // 未选任何参与者维度时不追加 IN 过滤。
        if (actorSets.isEmpty()) {
            return null;
        }
        // 多维度取交集：须同时满足医师+诊室+号源类型（若均指定）。
        Set<Long> intersection = new HashSet<>(actorSets.get(0));
        for (int i = 1; i < actorSets.size(); i++) {
            intersection.retainAll(actorSets.get(i));
        }
        return intersection;
    }

    /**
     * 按号源批量解析替诊信息（队列/大屏展示）。
     */
    public Map<Long, ScheduleSubstituteInfoModel> findSubstituteInfoBySlotIds(Collection<Long> slotIds) {
        Map<Long, ScheduleSubstituteInfoModel> result = new HashMap<>();
        if (slotIds == null || slotIds.isEmpty()) {
            return result;
        }
        List<Slot> slots = slotRepository.findAllById(slotIds);
        Set<Long> scheduleIds = new HashSet<>();
        Map<Long, Long> slotToScheduleId = new HashMap<>();
        for (Slot slot : slots) {
            if (slot == null || slot.getId() == null || Boolean.TRUE.equals(slot.getIsDeleted())) {
                continue;
            }
            if (slot.getScheduleId() != null) {
                scheduleIds.add(slot.getScheduleId());
                slotToScheduleId.put(slot.getId(), slot.getScheduleId());
            }
        }
        if (scheduleIds.isEmpty()) {
            return result;
        }
        Map<Long, ScheduleSubstituteInfoModel> scheduleInfo = this.findSubstituteInfoByScheduleIds(scheduleIds);
        for (Map.Entry<Long, Long> entry : slotToScheduleId.entrySet()) {
            ScheduleSubstituteInfoModel info = scheduleInfo.get(entry.getValue());
            if (info != null) {
                result.put(entry.getKey(), info);
            }
        }
        return result;
    }

    /**
     * 按日程解析替诊信息。
     */
    public ScheduleSubstituteInfoModel findSubstituteInfoByScheduleId(Long scheduleId) {
        if (scheduleId == null) {
            return null;
        }
        Map<Long, ScheduleSubstituteInfoModel> map = this.findSubstituteInfoByScheduleIds(List.of(scheduleId));
        return map.get(scheduleId);
    }

    private Map<Long, ScheduleSubstituteInfoModel> findSubstituteInfoByScheduleIds(Collection<Long> scheduleIds) {
        Map<Long, ScheduleSubstituteInfoModel> result = new HashMap<>();
        if (scheduleIds == null || scheduleIds.isEmpty()) {
            return result;
        }
        List<Schedule> schedules = scheduleRepository.findAllById(scheduleIds);
        int practitionerType = ScheduleActorKind.PRACTITIONER.getSortOrder().intValue();
        for (Schedule schedule : schedules) {
            if (schedule == null || schedule.getId() == null || schedule.getSubstitutePractitionerId() == null) {
                continue;
            }
            List<ScheduleActor> actors = scheduleActorRepository.findByScheduleIdOrderBySortNoAsc(schedule.getId());
            Long originalPractitionerId = null;
            for (ScheduleActor actor : actors) {
                if (actor == null || Boolean.TRUE.equals(actor.getIsDeleted())) {
                    continue;
                }
                if (practitionerType == actor.getActorType()) {
                    originalPractitionerId = actor.getActorId();
                    break;
                }
            }
            ScheduleSubstituteInfoModel info = new ScheduleSubstituteInfoModel();
            info.setOriginalPractitionerId(originalPractitionerId);
            info.setSubstitutePractitionerId(schedule.getSubstitutePractitionerId());
            info.setSubstituteReason(schedule.getSubstituteReason());
            result.put(schedule.getId(), info);
        }
        return result;
    }

    /**
     * 按参与者类型与主键收集关联日程主键。
     *
     * <p>医师：并集 Actor 行与 {@code substitutePractitionerId} 顶班行（替诊不改 Actor）。</p>
     */
    private Set<Long> scheduleIdsForActor(ScheduleActorKind actorKind, Long actorId) {
        Set<Long> scheduleIds = new HashSet<>();
        Integer actorType = actorKind.getSortOrder().intValue();
        List<ScheduleActor> rows = scheduleActorRepository.findByActorTypeAndActorIdOrderByScheduleIdAsc(actorType, actorId);
        for (ScheduleActor row : rows) {
            if (row.getScheduleId() != null) {
                scheduleIds.add(row.getScheduleId());
            }
        }
        // 日历/可约号按医师收窄时，替诊顶班医师也必须能看见该日程下已放号。
        if (ScheduleActorKind.PRACTITIONER == actorKind) {
            scheduleIds.addAll(this.findScheduleIdsBySubstitutePractitioner(actorId));
        }
        return scheduleIds;
    }

    /**
     * 收集「顶班替诊医师 = 指定医师」且未软删的日程主键。
     *
     * @param practitionerId 替诊医师主键
     * @return 日程主键集合；入参空时为空集
     */
    private Set<Long> findScheduleIdsBySubstitutePractitioner(Long practitionerId) {
        Set<Long> scheduleIds = new HashSet<>();
        if (practitionerId == null) {
            return scheduleIds;
        }
        Specification<Schedule> specification = (root, cq, cb) -> cb.and(
            cb.isFalse(root.get(FieldNameConstants.IS_DELETED)),
            cb.equal(root.get("substitutePractitionerId"), practitionerId)
        );
        List<Schedule> schedules = scheduleRepository.findAll(specification);
        for (Schedule schedule : schedules) {
            if (schedule != null && schedule.getId() != null) {
                scheduleIds.add(schedule.getId());
            }
        }
        return scheduleIds;
    }

    /**
     * 是否医师类参与者类型（与 {@link ScheduleActorKind#PRACTITIONER} 的 sortOrder 一致）。
     *
     * @param actorType 参与者类型编码
     * @return 医师类型为 true
     */
    private boolean isPractitionerActorType(Integer actorType) {
        if (actorType == null) {
            return false;
        }
        return ScheduleActorKind.PRACTITIONER.getSortOrder().intValue() == actorType;
    }

    /**
     * 组装日历项读模型。
     */
    private ScheduleCalendarItemModel toCalendarItemModel(Schedule schedule, List<ScheduleActor> actorRows) {
        ScheduleCalendarItemModel model = new ScheduleCalendarItemModel();
        model.setId(schedule.getId());
        model.setOrganizationId(schedule.getOrganizationId());
        model.setName(schedule.getName());
        model.setStatusEnum(schedule.getStatusEnum());
        model.setPlanningHorizonStart(schedule.getPlanningHorizonStart());
        model.setPlanningHorizonEnd(schedule.getPlanningHorizonEnd());
        model.setHealthcareServiceId(schedule.getHealthcareServiceId());
        model.setSubstitutePractitionerId(schedule.getSubstitutePractitionerId());
        model.setSubstituteReason(schedule.getSubstituteReason());
        model.setSourceScheduleTemplateId(schedule.getSourceScheduleTemplateId());
        model.setSlotProvisionTemplateId(schedule.getSlotProvisionTemplateId());
        List<ScheduleActorModel> actors = new ArrayList<>();
        for (ScheduleActor row : actorRows) {
            ScheduleActorModel actor = new ScheduleActorModel();
            actor.setActorType(row.getActorType());
            actor.setActorId(row.getActorId());
            actor.setSortNo(row.getSortNo());
            actors.add(actor);
        }
        model.setActors(actors);
        return model;
    }

    /**
     * 组装日程详情：头信息 + 有序参与人列表。
     *
     * @param schedule 日程实体
     * @param actorRows 参与人行
     * @return 详情模型
     */
    private ScheduleDetailModel toDetailModel(Schedule schedule, List<ScheduleActor> actorRows) {
        ScheduleDetailModel model = new ScheduleDetailModel();
        model.setId(schedule.getId());
        model.setStatusEnum(schedule.getStatusEnum());
        List<ScheduleActorModel> actors = new ArrayList<>();
        for (ScheduleActor row : actorRows) {
            ScheduleActorModel a = new ScheduleActorModel();
            a.setActorType(row.getActorType());
            a.setActorId(row.getActorId());
            a.setSortNo(row.getSortNo());
            actors.add(a);
        }
        model.setActors(actors);
        return model;
    }
}
