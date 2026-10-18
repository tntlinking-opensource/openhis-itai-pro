/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.template.service;

import com.zxplat.plugin.booking.domain.template.queries.model.SlotProvisionItemModel;
import com.zxplat.plugin.booking.domain.template.support.TemplateSlotJsonSupport;
import com.zxplat.plugin.booking.domain.calendarexception.queries.model.DayGeneratePlanModel;
import com.zxplat.plugin.booking.domain.calendarexception.service.CalendarExceptionQueryService;
import com.zxplat.plugin.booking.domain.share.VirtualSlotKeySupport;
import com.zxplat.plugin.booking.domain.template.entity.ScheduleTemplate;
import com.zxplat.plugin.booking.domain.template.entity.SlotProvisionTemplate;
import com.zxplat.plugin.booking.domain.template.entity.WeekScheduleTemplate;
import com.zxplat.plugin.booking.domain.template.entity.WeekScheduleTemplateItem;
import com.zxplat.plugin.booking.domain.template.queries.model.ResolvedDaySchedulePlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 按日解析周模板 fallback 计划（2.25 · BR-TPL-004）。
 */
@Service
@RequiredArgsConstructor
public class DayScheduleTemplateResolver {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final WeekScheduleTemplateQueryService weekScheduleTemplateQueryService;
    private final TemplateQueryService templateQueryService;
    private final CalendarExceptionQueryService calendarExceptionQueryService;

    /**
     * 解析某日模板态计划（无真实 Schedule 时用于周历/虚拟可约）。
     */
    public List<ResolvedDaySchedulePlan> resolveWeekFallbackPlans(
        Long organizationId,
        LocalDate date,
        Long practitionerId,
        Long locationId,
        Long healthcareServiceId) {
        List<ResolvedDaySchedulePlan> result = new ArrayList<>();
        if (organizationId == null || date == null) {
            return result;
        }
        DayGeneratePlanModel t3 = calendarExceptionQueryService.resolveDayGeneratePlan(organizationId, date);
        if (t3.isSkipNormal()) {
            return result;
        }
        WeekScheduleTemplate weekTemplate = weekScheduleTemplateQueryService.findDefaultForOrganization(organizationId);
        if (weekTemplate == null || !this.isEffectiveOn(weekTemplate, date)) {
            return result;
        }
        int weekday = date.getDayOfWeek().getValue();
        List<WeekScheduleTemplateItem> items = weekScheduleTemplateQueryService.listEnabledItemsForWeekday(
            weekTemplate.getId(), weekday);
        boolean bookable = Boolean.TRUE.equals(weekTemplate.getFallbackBookable());
        for (WeekScheduleTemplateItem item : items) {
            ScheduleTemplate dayTemplate = templateQueryService.loadScheduleTemplateById(item.getDayScheduleTemplateId());
            if (!Boolean.TRUE.equals(dayTemplate.getEnabled()) || !this.isEffectiveOn(dayTemplate, date)) {
                continue;
            }
            if (!this.matchesFilter(dayTemplate, practitionerId, locationId, healthcareServiceId)) {
                continue;
            }
            ResolvedDaySchedulePlan plan = new ResolvedDaySchedulePlan();
            plan.setDate(date);
            plan.setWeekScheduleTemplateId(weekTemplate.getId());
            plan.setDayScheduleTemplateId(dayTemplate.getId());
            plan.setDayTemplate(dayTemplate);
            plan.setFromTemplate(true);
            plan.setSkip(false);
            plan.setBookable(bookable);
            if (dayTemplate.getSlotProvisionTemplateId() != null) {
                plan.setSlotTemplate(templateQueryService.loadSlotProvisionTemplateById(
                    dayTemplate.getSlotProvisionTemplateId()));
            }
            if (bookable && plan.getSlotTemplate() != null) {
                plan.setVirtualPeriods(this.buildVirtualPeriods(
                    organizationId, date, dayTemplate.getId(), plan.getSlotTemplate()));
            }
            result.add(plan);
        }
        return result;
    }

    private List<ResolvedDaySchedulePlan.VirtualSlotPeriod> buildVirtualPeriods(
        Long organizationId, LocalDate date, Long dayTemplateId, SlotProvisionTemplate slotTemplate) {
        List<ResolvedDaySchedulePlan.VirtualSlotPeriod> periods = new ArrayList<>();
        for (SlotProvisionItemModel row : TemplateSlotJsonSupport.parseSlotItems(slotTemplate.getSlotTemplatesJson())) {
            if (row.getStartTime() == null || row.getEndTime() == null) {
                continue;
            }
            LocalDateTime start = this.combine(date, row.getStartTime());
            LocalDateTime end = this.combine(date, row.getEndTime());
            if (!end.isAfter(start)) {
                continue;
            }
            ResolvedDaySchedulePlan.VirtualSlotPeriod period = new ResolvedDaySchedulePlan.VirtualSlotPeriod();
            period.setStartTime(start);
            period.setEndTime(end);
            if (row.getCapacity() != null) {
                period.setCapacity(row.getCapacity());
            } else {
                period.setCapacity(1);
            }
            period.setVirtualSlotKey(VirtualSlotKeySupport.encode(
                organizationId, date, dayTemplateId, start, end));
            periods.add(period);
        }
        return periods;
    }

    private LocalDateTime combine(LocalDate date, String timeText) {
        String normalized = timeText.trim();
        LocalTime time;
        if (normalized.length() <= 5) {
            time = LocalTime.parse(normalized, TIME_FMT);
        } else {
            time = LocalTime.parse(normalized, DateTimeFormatter.ofPattern("HH:mm:ss"));
        }
        return date.atTime(time);
    }

    private boolean matchesFilter(
        ScheduleTemplate template, Long practitionerId, Long locationId, Long healthcareServiceId) {
        if (practitionerId != null && !practitionerId.equals(template.getPractitionerId())) {
            return false;
        }
        if (locationId != null && !locationId.equals(template.getLocationId())) {
            return false;
        }
        if (healthcareServiceId != null && !healthcareServiceId.equals(template.getHealthcareServiceId())) {
            return false;
        }
        return true;
    }

    private boolean isEffectiveOn(WeekScheduleTemplate template, LocalDate date) {
        if (template.getEffectiveStart() != null && date.isBefore(template.getEffectiveStart())) {
            return false;
        }
        if (template.getEffectiveEnd() != null && date.isAfter(template.getEffectiveEnd())) {
            return false;
        }
        return true;
    }

    private boolean isEffectiveOn(ScheduleTemplate template, LocalDate date) {
        if (template.getEffectiveStart() != null && date.isBefore(template.getEffectiveStart())) {
            return false;
        }
        if (template.getEffectiveEnd() != null && date.isAfter(template.getEffectiveEnd())) {
            return false;
        }
        return true;
    }
}
