package com.openhis.whale.booking.application.support;

import com.openhis.health.domain.share.enums.ScheduleActorKind;
import com.openhis.whale.booking.domain.schedule.queries.CalendarScheduleQuery;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleCalendarItemModel;
import com.openhis.whale.booking.domain.schedule.service.ScheduleQueryService;
import com.openhis.whale.booking.domain.share.StopClinicConstants;
import com.openhis.whale.booking.domain.slot.queries.FindBookableSlotsQuery;
import com.openhis.whale.booking.domain.slot.queries.model.BookableSlotModel;
import com.openhis.whale.booking.domain.slot.queries.model.SlotDetailModel;
import com.openhis.whale.booking.domain.slot.service.SlotQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 停诊转交目标号源匹配（07 §4.3 BR-STOP-001）。
 */
@Component
@RequiredArgsConstructor
public class StopClinicTransferMatcher {

    private final ScheduleQueryService scheduleQueryService;
    private final SlotQueryService slotQueryService;

    /**
     * 为源号源匹配可转交目标号源。
     *
     * @param organizationId        科室主键
     * @param healthcareServiceId   号源类型
     * @param targetPractitionerId  目标医师
     * @param sourceSlot            源号源
     * @param excludeScheduleId     排除的源日程
     * @param matchRule             匹配规则
     * @param requireSameService    是否要求同号源类型
     * @return 目标号源主键
     */
    public Optional<Long> matchTargetSlotId(
        Long organizationId,
        Long healthcareServiceId,
        Long targetPractitionerId,
        SlotDetailModel sourceSlot,
        Long excludeScheduleId,
        String matchRule,
        boolean requireSameService) {
        if (organizationId == null || targetPractitionerId == null || sourceSlot == null || sourceSlot.getStartTime() == null) {
            return Optional.empty();
        }
        LocalDate bookingDate = sourceSlot.getStartTime().toLocalDate();
        List<ScheduleCalendarItemModel> candidateSchedules = this.listTargetSchedules(
            organizationId, bookingDate, targetPractitionerId, healthcareServiceId, requireSameService);
        String effectiveRule = matchRule;
        if (effectiveRule == null || effectiveRule.isBlank()) {
            effectiveRule = StopClinicConstants.MATCH_SAME_SERVICE_SAME_SLOT;
        }
        if (StopClinicConstants.MATCH_SAME_SERVICE_SAME_SLOT.equals(effectiveRule)) {
            return this.findSameTimeSlot(candidateSchedules, excludeScheduleId, sourceSlot);
        }
        if (StopClinicConstants.MATCH_SAME_SERVICE_NEAREST.equals(effectiveRule)) {
            return this.findNearestSlot(candidateSchedules, excludeScheduleId, sourceSlot, requireSameService, healthcareServiceId);
        }
        if (StopClinicConstants.MATCH_SAME_DEPT_ANY.equals(effectiveRule)) {
            return this.findNearestSlot(candidateSchedules, excludeScheduleId, sourceSlot, false, null);
        }
        return this.findSameTimeSlot(candidateSchedules, excludeScheduleId, sourceSlot);
    }

    private List<ScheduleCalendarItemModel> listTargetSchedules(
        Long organizationId,
        LocalDate bookingDate,
        Long targetPractitionerId,
        Long healthcareServiceId,
        boolean requireSameService) {
        CalendarScheduleQuery query = new CalendarScheduleQuery();
        query.setOrganizationId(organizationId);
        query.setRangeStart(bookingDate);
        query.setRangeEnd(bookingDate);
        query.setPractitionerId(targetPractitionerId);
        if (requireSameService) {
            query.setHealthcareServiceId(healthcareServiceId);
        }
        List<ScheduleCalendarItemModel> schedules = scheduleQueryService.listSchedulesForCalendar(query);
        if (schedules == null) {
            return new ArrayList<>();
        }
        return schedules;
    }

    private Optional<Long> findSameTimeSlot(
        List<ScheduleCalendarItemModel> schedules,
        Long excludeScheduleId,
        SlotDetailModel sourceSlot) {
        LocalDateTime sourceStart = sourceSlot.getStartTime();
        LocalDateTime sourceEnd = sourceSlot.getEndTime();
        for (ScheduleCalendarItemModel schedule : schedules) {
            if (schedule.getId() == null) {
                continue;
            }
            if (excludeScheduleId != null && excludeScheduleId.equals(schedule.getId())) {
                continue;
            }
            List<BookableSlotModel> slots = this.listBookableSlotsForDay(schedule.getId(), sourceStart.toLocalDate());
            for (BookableSlotModel slot : slots) {
                if (Boolean.TRUE.equals(slot.getIsFull())) {
                    continue;
                }
                if (this.sameInstant(slot.getStartTime(), sourceStart) && this.sameInstant(slot.getEndTime(), sourceEnd)) {
                    return Optional.of(slot.getSlotId());
                }
            }
        }
        return Optional.empty();
    }

    private Optional<Long> findNearestSlot(
        List<ScheduleCalendarItemModel> schedules,
        Long excludeScheduleId,
        SlotDetailModel sourceSlot,
        boolean requireSameService,
        Long healthcareServiceId) {
        LocalDateTime sourceStart = sourceSlot.getStartTime();
        Long bestSlotId = null;
        long bestDistance = Long.MAX_VALUE;
        for (ScheduleCalendarItemModel schedule : schedules) {
            if (schedule.getId() == null) {
                continue;
            }
            if (excludeScheduleId != null && excludeScheduleId.equals(schedule.getId())) {
                continue;
            }
            if (requireSameService && healthcareServiceId != null && schedule.getHealthcareServiceId() != null) {
                if (!healthcareServiceId.equals(schedule.getHealthcareServiceId())) {
                    continue;
                }
            }
            List<BookableSlotModel> slots = this.listBookableSlotsForDay(schedule.getId(), sourceStart.toLocalDate());
            for (BookableSlotModel slot : slots) {
                if (Boolean.TRUE.equals(slot.getIsFull())) {
                    continue;
                }
                if (slot.getStartTime() == null) {
                    continue;
                }
                long distance = Math.abs(Duration.between(sourceStart, slot.getStartTime()).toMinutes());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    bestSlotId = slot.getSlotId();
                }
            }
        }
        if (bestSlotId == null) {
            return Optional.empty();
        }
        return Optional.of(bestSlotId);
    }

    private List<BookableSlotModel> listBookableSlotsForDay(Long scheduleId, LocalDate date) {
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        FindBookableSlotsQuery query = new FindBookableSlotsQuery();
        query.setScheduleId(scheduleId);
        query.setStartTime(dayStart);
        query.setEndTime(dayEnd);
        return slotQueryService.findBookableSlots(query);
    }

    private boolean sameInstant(LocalDateTime left, LocalDateTime right) {
        if (left == null && right == null) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.equals(right);
    }

    /**
     * 从日程参与者解析医师主键。
     *
     * @param schedule 日程读模型
     * @return 医师主键
     */
    public Long resolvePractitionerId(ScheduleCalendarItemModel schedule) {
        if (schedule == null || schedule.getActors() == null) {
            return null;
        }
        Integer practitionerType = ScheduleActorKind.PRACTITIONER.getSortOrder().intValue();
        for (com.openhis.whale.booking.domain.schedule.queries.model.ScheduleActorModel actor : schedule.getActors()) {
            if (practitionerType.equals(actor.getActorType())) {
                return actor.getActorId();
            }
        }
        return null;
    }
}
