package com.openhis.whale.booking.adapter.support;

import com.openhis.health.domain.share.enums.ScheduleStatus;
import com.openhis.whale.booking.domain.schedule.entity.Schedule;
import com.openhis.whale.booking.domain.schedule.queries.CalendarScheduleQuery;
import com.openhis.whale.booking.domain.schedule.queries.model.ScheduleCalendarItemModel;
import com.openhis.whale.booking.domain.schedule.repository.ScheduleRepository;
import com.openhis.whale.booking.domain.schedule.service.ScheduleQueryService;
import com.openhis.whale.booking.domain.slot.queries.FindBookableSlotsQuery;
import com.openhis.whale.booking.domain.slot.queries.model.BookableSlotModel;
import com.openhis.whale.booking.domain.slot.service.SlotQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 挂号/预约共用的号源检索支撑（adapter 内复用，不依赖 booking-application）。
 */
@Component
@RequiredArgsConstructor
public class BookingSlotLookupSupport {

    private final ScheduleQueryService scheduleQueryService;
    private final ScheduleRepository scheduleRepository;
    private final SlotQueryService slotQueryService;

    /**
     * 解析当日可约日程主键集合。
     */
    public List<Long> resolveScheduleIds(
        Long organizationId,
        LocalDate date,
        Long practitionerId,
        Long healthcareServiceId) {
        if (organizationId == null || date == null) {
            return new ArrayList<>();
        }
        CalendarScheduleQuery query = new CalendarScheduleQuery();
        query.setOrganizationId(organizationId);
        query.setRangeStart(date);
        query.setRangeEnd(date);
        query.setPractitionerId(practitionerId);
        query.setHealthcareServiceId(healthcareServiceId);

        List<ScheduleCalendarItemModel> schedules = scheduleQueryService.listSchedulesForCalendar(query);
        List<Long> scheduleIds = new ArrayList<>();
        for (ScheduleCalendarItemModel schedule : schedules) {
            if (schedule == null || schedule.getId() == null) {
                continue;
            }
            if (!ScheduleStatus.ACTIVE.getCode().equals(schedule.getStatusEnum())) {
                continue;
            }
            scheduleIds.add(schedule.getId());
        }
        return scheduleIds;
    }

    /**
     * 查询当日非满可约号源。
     */
    public List<BookableSlotModel> findAvailableSlots(
        Long organizationId,
        LocalDate date,
        Long practitionerId,
        Long healthcareServiceId) {
        List<Long> scheduleIds = this.resolveScheduleIds(organizationId, date, practitionerId, healthcareServiceId);
        if (scheduleIds.isEmpty()) {
            return new ArrayList<>();
        }
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        List<BookableSlotModel> merged = new ArrayList<>();
        for (Long scheduleId : scheduleIds) {
            FindBookableSlotsQuery slotQuery = new FindBookableSlotsQuery();
            slotQuery.setScheduleId(scheduleId);
            slotQuery.setStartTime(dayStart);
            slotQuery.setEndTime(dayEnd);
            List<BookableSlotModel> bookable = slotQueryService.findBookableSlots(slotQuery);
            for (BookableSlotModel model : bookable) {
                if (Boolean.TRUE.equals(model.getIsFull())) {
                    continue;
                }
                merged.add(model);
            }
        }
        return merged;
    }

    /**
     * 当日有放号的号源类型主键（去重保序）。
     */
    public Set<Long> findHealthcareServiceIdsWithSlots(
        Long organizationId,
        LocalDate date,
        Long practitionerId) {
        Set<Long> serviceIds = new LinkedHashSet<>();
        List<Long> scheduleIds = this.resolveScheduleIds(organizationId, date, practitionerId, null);
        if (scheduleIds.isEmpty()) {
            return serviceIds;
        }
        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = date.plusDays(1).atStartOfDay();
        for (Long scheduleId : scheduleIds) {
            Schedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
            if (schedule == null || schedule.getHealthcareServiceId() == null) {
                continue;
            }
            FindBookableSlotsQuery slotQuery = new FindBookableSlotsQuery();
            slotQuery.setScheduleId(scheduleId);
            slotQuery.setStartTime(dayStart);
            slotQuery.setEndTime(dayEnd);
            List<BookableSlotModel> bookable = slotQueryService.findBookableSlots(slotQuery);
            for (BookableSlotModel model : bookable) {
                if (!Boolean.TRUE.equals(model.getIsFull())) {
                    serviceIds.add(schedule.getHealthcareServiceId());
                    break;
                }
            }
        }
        return serviceIds;
    }

    /**
     * 取第一个可约号源（现场占号）。
     */
    public Long findFirstAvailableSlotId(
        Long organizationId,
        LocalDate date,
        Long practitionerId,
        Long healthcareServiceId) {
        List<BookableSlotModel> slots = this.findAvailableSlots(organizationId, date, practitionerId, healthcareServiceId);
        if (slots.isEmpty()) {
            return null;
        }
        return slots.get(0).getSlotId();
    }
}
