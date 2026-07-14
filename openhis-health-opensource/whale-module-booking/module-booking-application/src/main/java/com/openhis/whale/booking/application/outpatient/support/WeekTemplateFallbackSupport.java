/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.outpatient.support;

import com.openhis.health.domain.share.enums.ScheduleActorKind;
import com.openhis.whale.booking.application.outpatient.response.ScheduleCalendarCellDto;
import com.openhis.whale.booking.application.outpatient.response.ScheduleCalendarDayDto;
import com.openhis.whale.booking.application.outpatient.response.ScheduleCalendarMonthDayDto;
import com.openhis.whale.booking.application.outpatient.response.PageSchedulesByCalendarResponse;
import com.openhis.whale.booking.application.outpatient.response.PageSchedulesByMonthResponse;
import com.openhis.whale.booking.application.outpatient.response.ScheduleActorDto;
import com.openhis.whale.booking.application.outpatient.request.PageSchedulesByCalendarRequest;
import com.openhis.whale.booking.application.outpatient.request.PageSchedulesByMonthRequest;
import com.openhis.whale.booking.domain.template.entity.ScheduleTemplate;
import com.openhis.whale.booking.domain.template.queries.model.ResolvedDaySchedulePlan;
import com.openhis.whale.booking.domain.template.service.DayScheduleTemplateResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 周历/月历合并 TW 模板态格子（2.25）。
 */
@Service
@RequiredArgsConstructor
public class WeekTemplateFallbackSupport {

    private static final String FROM_TEMPLATE_STATUS = "FROM_TEMPLATE";

    private final DayScheduleTemplateResolver dayScheduleTemplateResolver;

    public void mergeCalendarFallback(
        PageSchedulesByCalendarResponse response, PageSchedulesByCalendarRequest request) {
        if (response == null || response.getDays() == null || request == null) {
            return;
        }
        for (ScheduleCalendarDayDto day : response.getDays()) {
            if (day.getSchedules() != null && !day.getSchedules().isEmpty()) {
                continue;
            }
            List<ResolvedDaySchedulePlan> plans = dayScheduleTemplateResolver.resolveWeekFallbackPlans(
                request.getOrganizationId(),
                day.getDate(),
                request.getPractitionerId(),
                request.getLocationId(),
                request.getHealthcareServiceId());
            for (ResolvedDaySchedulePlan plan : plans) {
                day.getSchedules().add(this.toTemplateCell(plan));
            }
        }
    }

    public void mergeMonthFallback(PageSchedulesByMonthResponse response, PageSchedulesByMonthRequest request) {
        if (response == null || response.getDays() == null || request == null) {
            return;
        }
        for (ScheduleCalendarMonthDayDto day : response.getDays()) {
            if (day.getSchedules() != null && !day.getSchedules().isEmpty()) {
                continue;
            }
            List<ResolvedDaySchedulePlan> plans = dayScheduleTemplateResolver.resolveWeekFallbackPlans(
                request.getOrganizationId(),
                day.getDate(),
                request.getPractitionerId(),
                request.getLocationId(),
                request.getHealthcareServiceId());
            for (ResolvedDaySchedulePlan plan : plans) {
                day.getSchedules().add(this.toTemplateCell(plan));
            }
        }
    }

    private ScheduleCalendarCellDto toTemplateCell(ResolvedDaySchedulePlan plan) {
        ScheduleTemplate template = plan.getDayTemplate();
        ScheduleCalendarCellDto cell = new ScheduleCalendarCellDto();
        cell.setFromTemplate(true);
        cell.setBookable(plan.isBookable());
        cell.setWeekScheduleTemplateId(plan.getWeekScheduleTemplateId());
        cell.setDayScheduleTemplateId(plan.getDayScheduleTemplateId());
        cell.setStatusEnum(FROM_TEMPLATE_STATUS);
        if (template != null) {
            cell.setName(template.getTemplateName());
            cell.setHealthcareServiceId(template.getHealthcareServiceId());
            cell.setActors(this.toActors(template));
        }
        return cell;
    }

    private List<ScheduleActorDto> toActors(ScheduleTemplate template) {
        List<ScheduleActorDto> actors = new ArrayList<>();
        int sort = 0;
        if (template.getPractitionerId() != null) {
            ScheduleActorDto actor = new ScheduleActorDto();
            actor.setActorType(ScheduleActorKind.PRACTITIONER.getSortOrder().intValue());
            actor.setActorId(template.getPractitionerId());
            actor.setSortNo(sort++);
            actors.add(actor);
        }
        if (template.getLocationId() != null) {
            ScheduleActorDto actor = new ScheduleActorDto();
            actor.setActorType(ScheduleActorKind.LOCATION.getSortOrder().intValue());
            actor.setActorId(template.getLocationId());
            actor.setSortNo(sort++);
            actors.add(actor);
        }
        if (template.getHealthcareServiceId() != null) {
            ScheduleActorDto actor = new ScheduleActorDto();
            actor.setActorType(ScheduleActorKind.HEALTHCARE_SERVICE.getSortOrder().intValue());
            actor.setActorId(template.getHealthcareServiceId());
            actor.setSortNo(sort);
            actors.add(actor);
        }
        return actors;
    }
}
