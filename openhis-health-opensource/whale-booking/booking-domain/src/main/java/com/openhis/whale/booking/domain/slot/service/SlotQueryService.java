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

package com.openhis.whale.booking.domain.slot.service;


import com.openhis.health.domain.share.constants.FieldNameConstants;
import com.openhis.health.domain.share.constants.ErrorCodeConstants;
import com.openhis.health.domain.share.enums.ScheduleStatus;
import com.openhis.health.domain.share.enums.SlotStatus;
import com.openhis.whale.booking.domain.schedule.entity.Schedule;
import com.openhis.whale.booking.domain.schedule.repository.ScheduleRepository;
import com.openhis.whale.booking.domain.stopclinic.service.StopClinicSlotFreezeDomainService;
import com.openhis.whale.booking.domain.slot.entity.Slot;
import com.openhis.whale.booking.domain.slot.queries.FindBookableSlotsQuery;
import com.openhis.whale.booking.domain.slot.queries.FindSlotsByScheduleAndTimeRangeQuery;
import com.openhis.whale.booking.domain.slot.queries.PageSlotsByOrganizationAndDateQuery;
import com.openhis.whale.booking.domain.slot.queries.model.BookableSlotModel;
import com.openhis.whale.booking.domain.slot.queries.model.SlotDetailModel;
import com.openhis.whale.booking.domain.slot.repository.SlotRepository;
import com.openhis.whale.core.application.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;

/**
 * 号源时段读侧查询服务（DM-WKF-010）。
 *
 * <p>职责：日程下时段检索、可预约筛选（状态与容量）、机构某日分页排班；参数校验失败抛业务异常，不写库。</p>
 * <p>跨聚合读：可约查询按 B.6.4 校验所属 {@code wkf_schedule.status_enum}；机构日分页经 Schedule 解析当日日程主键再查 Slot。</p>
 */
@Service
@RequiredArgsConstructor
public class SlotQueryService {

    private final SlotRepository slotRepository;
    private final ScheduleRepository scheduleRepository;
    private final StopClinicSlotFreezeDomainService stopClinicSlotFreezeDomainService;

    /**
     * 按主键加载时段。
     *
     * @param id 时段标识
     * @return 明细模型
     * @throws BusinessException id 为空或时段不存在
     */
    public SlotDetailModel loadSlotById(Long id) {
        if (id == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "id 不能为空");
        }
        Slot slot = slotRepository.findById(id)
            .orElseThrow(() -> new BusinessException(ErrorCodeConstants.SLOT_NOT_FOUND, "Slot 不存在"));
        return this.toDetailModel(slot);
    }

    /**
     * 按日程与时间窗查询时段。
     *
     * @param query 查询入参
     * @return 列表
     * @throws BusinessException 必填字段缺失
     */
    public List<SlotDetailModel> findSlotsByScheduleAndTimeRange(FindSlotsByScheduleAndTimeRangeQuery query) {
        if (query == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "query 不能为空");
        }
        if (query.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        if (query.getStartTime() == null || query.getEndTime() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "startTime/endTime 不能为空");
        }

        List<Slot> slots;
        if (query.getStatusCodeList() != null && !query.getStatusCodeList().isEmpty()) {
            slots = slotRepository.findByScheduleIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqualAndStatusCodeIn(
                query.getScheduleId(), query.getStartTime(), query.getEndTime(), query.getStatusCodeList());
        } else {
            slots = slotRepository.findByScheduleIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqual(
                query.getScheduleId(), query.getStartTime(), query.getEndTime());
        }

        List<SlotDetailModel> models = new ArrayList<>();
        for (Slot slot : slots) {
            models.add(this.toDetailModel(slot));
        }
        return models;
    }

    /**
     * 查询可预约时段。
     *
     * @param query 查询入参
     * @return 列表
     * @throws BusinessException 必填字段缺失
     */
    public List<BookableSlotModel> findBookableSlots(FindBookableSlotsQuery query) {
        if (query == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "query 不能为空");
        }
        if (query.getScheduleId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "scheduleId 不能为空");
        }
        if (query.getStartTime() == null || query.getEndTime() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "startTime/endTime 不能为空");
        }
        // B.6.4 跨聚合读：可约查询须校验所属日程仍为 active（停诊后 slot 可能仍为 free）
        if (!this.isScheduleBookable(query.getScheduleId())) {
            return List.of();
        }

        List<String> statusCandidates = query.getStatusCodeList();
        if (statusCandidates == null || statusCandidates.isEmpty()) {
            statusCandidates = List.of(SlotStatus.FREE.getCode(), SlotStatus.BUSY.getCode());
        }

        List<Slot> slots = slotRepository.findByScheduleIdAndStartTimeGreaterThanEqualAndEndTimeLessThanEqualAndStatusCodeIn(
            query.getScheduleId(), query.getStartTime(), query.getEndTime(), statusCandidates);

        Set<Long> frozenSlotIds = stopClinicSlotFreezeDomainService.findActiveFrozenSlotIdsByScheduleId(
            query.getScheduleId());

        List<BookableSlotModel> models = new ArrayList<>();
        for (Slot slot : slots) {
            if (frozenSlotIds.contains(slot.getId())) {
                continue;
            }
            // 录入错误态时段不向患者开放预约
            if (SlotStatus.ENTERED_IN_ERROR.getCode().equals(slot.getStatusCode())) {
                continue;
            }
            // 忙且不可约的时段跳过
            if (SlotStatus.BUSY_UNAVAILABLE.getCode().equals(slot.getStatusCode())) {
                continue;
            }

            Boolean isFull = false;
            if (slot.getCapacity() != null && slot.getBookedCount() != null) {
                if (slot.getBookedCount() >= slot.getCapacity()) {
                    isFull = true;
                }
            }

            BookableSlotModel model = new BookableSlotModel();
            model.setSlotId(slot.getId());
            model.setScheduleId(slot.getScheduleId());
            model.setStartTime(slot.getStartTime());
            model.setEndTime(slot.getEndTime());
            model.setIsFull(isFull);
            models.add(model);
        }
        return models;
    }

    /**
     * 按机构与日期分页查询时段。
     *
     * @param query 查询入参
     * @return 分页结果
     * @throws BusinessException 分页或主键参数非法
     */
    public Page<SlotDetailModel> pageSlotsByOrganizationAndDate(PageSlotsByOrganizationAndDateQuery query) {
        if (query == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "query 不能为空");
        }
        if (query.getOrganizationId() == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "organizationId 不能为空");
        }
        LocalDate date = query.getDate();
        if (date == null) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "date 不能为空");
        }
        Integer pageNo = query.getPageNo();
        Integer pageSize = query.getPageSize();
        if (pageNo == null || pageNo <= 0) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "pageNo 非法");
        }
        if (pageSize == null || pageSize <= 0) {
            throw new BusinessException(ErrorCodeConstants.SLOT_INVALID_ARGUMENT, "pageSize 非法");
        }

        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        Sort sort = Sort.by(Sort.Direction.ASC, FieldNameConstants.START_TIME);
        Pageable pageable = PageRequest.of(pageNo - 1, pageSize, sort);

        // Pre：先按机构+日历日解析有效日程主键，再查时段（与排班周历口径一致，避免仅依赖 Slot 冗余 organizationId）。
        Set<Long> scheduleIdScope = this.resolveScheduleIdsForOrganizationAndDate(query.getOrganizationId(), date);
        boolean useScheduleScope = !scheduleIdScope.isEmpty();
        if (query.getScheduleIds() != null) {
            // 医师等参与者筛选：与机构当日日程求交；空列表表示无匹配日程。
            if (query.getScheduleIds().isEmpty()) {
                return new PageImpl<>(List.of(), pageable, 0);
            }
            if (useScheduleScope) {
                Set<Long> practitionerScope = new HashSet<>(query.getScheduleIds());
                scheduleIdScope.retainAll(practitionerScope);
                if (scheduleIdScope.isEmpty()) {
                    return new PageImpl<>(List.of(), pageable, 0);
                }
            } else {
                scheduleIdScope = new HashSet<>(query.getScheduleIds());
                useScheduleScope = true;
            }
        }

        final boolean filterByScheduleScope = useScheduleScope;
        final Set<Long> finalScheduleIdScope = scheduleIdScope;
        Specification<Slot> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            if (filterByScheduleScope) {
                predicates.add(root.get("scheduleId").in(finalScheduleIdScope));
            } else {
                // 无当日日程规划窗口时回退 Slot.organization_id，兼容已放号但窗口未维护的数据。
                predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), query.getOrganizationId()));
            }
            predicates.add(cb.greaterThanOrEqualTo(root.get(FieldNameConstants.START_TIME), from));
            predicates.add(cb.lessThan(root.get(FieldNameConstants.START_TIME), to));
            if (query.getScheduleId() != null) {
                predicates.add(cb.equal(root.get("scheduleId"), query.getScheduleId()));
            }
            if (query.getStatusCode() != null && !query.getStatusCode().trim().isEmpty()) {
                predicates.add(cb.equal(root.get("statusCode"), query.getStatusCode().trim()));
            }
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        Page<Slot> page = slotRepository.findAll(specification, pageable);
        return page.map(this::toDetailModel);
    }

    /**
     * 解析机构在指定自然日有规划窗口重叠的日程主键集合。
     *
     * @param organizationId 机构主键
     * @param date 日历日
     * @return 日程主键集合；无匹配时为空集
     */
    private Set<Long> resolveScheduleIdsForOrganizationAndDate(Long organizationId, LocalDate date) {
        if (organizationId == null || date == null) {
            return Set.of();
        }
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        // B.6.4 跨聚合读：机构当日日程范围经 Schedule 仓储解析，再下推 Slot IN 条件。
        Specification<Schedule> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get(FieldNameConstants.ORGANIZATION_ID), organizationId));
            jakarta.persistence.criteria.Predicate horizonOverlaps = cb.and(
                cb.or(cb.isNull(root.get(FieldNameConstants.PLANNING_HORIZON_START)),
                    cb.lessThan(root.get(FieldNameConstants.PLANNING_HORIZON_START), dayEnd)),
                cb.or(cb.isNull(root.get(FieldNameConstants.PLANNING_HORIZON_END)),
                    cb.greaterThan(root.get(FieldNameConstants.PLANNING_HORIZON_END), dayStart))
            );
            predicates.add(horizonOverlaps);
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        List<Schedule> schedules = scheduleRepository.findAll(specification);
        Set<Long> scheduleIds = new HashSet<>();
        for (Schedule schedule : schedules) {
            if (schedule.getId() != null) {
                scheduleIds.add(schedule.getId());
            }
        }
        return scheduleIds;
    }

    /**
     * 统计日程下仍有预约占用的号源段数（bookedCount &gt; 0，作废日程前门禁）。
     *
     * @param scheduleId 日程主键
     * @return 占用段数；scheduleId 为空时返回 0
     */
    public long countBookedSlotsByScheduleId(Long scheduleId) {
        if (scheduleId == null) {
            return 0;
        }
        Specification<Slot> specification = (root, cq, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isFalse(root.get(FieldNameConstants.IS_DELETED)));
            predicates.add(cb.equal(root.get("scheduleId"), scheduleId));
            predicates.add(cb.greaterThan(root.get("bookedCount"), 0));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };
        return slotRepository.count(specification);
    }

    /**
     * 聚合根到读侧明细模型的映射。
     *
     * @param slot 聚合根
     * @return 明细模型
     */
    private SlotDetailModel toDetailModel(Slot slot) {
        SlotDetailModel model = new SlotDetailModel();
        model.setId(slot.getId());
        model.setOrganizationId(slot.getOrganizationId());
        model.setScheduleId(slot.getScheduleId());
        model.setStartTime(slot.getStartTime());
        model.setEndTime(slot.getEndTime());
        model.setStatusCode(slot.getStatusCode());
        model.setCapacity(slot.getCapacity());
        model.setBookedCount(slot.getBookedCount());
        model.setOverbookedFlag(slot.getOverbookedFlag());
        model.setServiceCategoryJson(slot.getServiceCategoryJson());
        model.setServiceTypeJson(slot.getServiceTypeJson());
        model.setSpecialtyJson(slot.getSpecialtyJson());
        model.setAppointmentTypeJson(slot.getAppointmentTypeJson());
        model.setCommentText(slot.getCommentText());
        return model;
    }

    /**
     * 判断日程是否仍可向患者开放预约（仅 active 且未软删）。
     *
     * @param scheduleId 日程主键
     * @return 可约时 true
     */
    private boolean isScheduleBookable(Long scheduleId) {
        if (scheduleId == null) {
            return false;
        }
        Schedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null || Boolean.TRUE.equals(schedule.getIsDeleted())) {
            return false;
        }
        if (!ScheduleStatus.ACTIVE.getCode().equals(schedule.getStatusEnum())) {
            return false;
        }
        return Boolean.TRUE.equals(schedule.getActiveFlag());
    }
}
