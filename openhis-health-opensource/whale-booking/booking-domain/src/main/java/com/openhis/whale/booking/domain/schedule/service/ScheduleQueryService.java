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

package com.openhis.whale.booking.domain.schedule.service;


import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.utils.SearchCodeUtil;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.ScheduleStatus;
import com.openhis.whale.booking.domain.schedule.entity.Schedule;
import com.openhis.whale.booking.domain.schedule.entity.ScheduleActor;
import com.openhis.health.domain.share.enums.ScheduleActorKind;
import com.openhis.whale.booking.domain.schedule.queries.CalendarScheduleQuery;
import com.openhis.whale.booking.domain.schedule.queries.SchedulePageQuery;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleActorDateModel;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleActorModel;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleCalendarItemModel;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleDetailModel;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleSubstituteInfoModel;
import com.openhis.whale.booking.domain.schedule.repository.ScheduleActorRepository;
import com.openhis.whale.booking.domain.schedule.repository.ScheduleRepository;
import com.openhis.whale.booking.domain.slot.entity.Slot;
import com.openhis.whale.booking.domain.slot.repository.SlotRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
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
     * @param actorType 参与者类型编码
     * @param actorId 参与者主键（医护人员 adm_practitioner.id，非 userId）
     * @param date 日历日
     * @param organizationId 机构主键；非空时仅保留该科室下的日程，与号源分页机构口径一致
     * @return 日程摘要列表；入参不完整或无参与关系时为空
     */
    public List<ScheduleActorDateModel> findSchedulesByActorAndDate(
        Integer actorType, Long actorId, LocalDate date, Long organizationId) {
        // 入参不完整时返回空表，避免误查全量。
        if (actorType == null || actorId == null || date == null) {
            return new ArrayList<>();
        }
        List<ScheduleActor> actorRows = scheduleActorRepository.findByActorTypeAndActorIdOrderByScheduleIdAsc(actorType, actorId);
        // 该参与者未关联任何日程。
        if (actorRows.isEmpty()) {
            return new ArrayList<>();
        }

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();

        List<Long> scheduleIds = new ArrayList<>();
        for (ScheduleActor row : actorRows) {
            if (row.getScheduleId() != null) {
                scheduleIds.add(row.getScheduleId());
            }
        }
        if (scheduleIds.isEmpty()) {
            return new ArrayList<>();
        }

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
        Specification<Schedule> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId));
            predicates.add(cb.equal(root.get(FieldNameConstants.STATUS_ENUM), ScheduleStatus.ACTIVE.getCode()));
            if (query != null && StringUtils.isNotBlank(query.getKeyword())) {
                String likeIc = SearchCodeUtil.toCaseInsensitiveLikePattern(query.getKeyword().trim());
                predicates.add(cb.like(cb.lower(root.get(FieldNameConstants.NAME)), likeIc));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return scheduleRepository.findAll(specification, pageable).map(s -> {
            List<ScheduleActor> actors = scheduleActorRepository.findByScheduleIdOrderBySortNoAsc(s.getId());
            return this.toDetailModel(s, actors);
        });
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
        return scheduleIds;
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
