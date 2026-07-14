/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.template.service;

import com.openhis.whale.booking.domain.calendarexception.queries.model.DayGeneratePlanModel;
import com.openhis.whale.booking.domain.calendarexception.service.CalendarExceptionQueryService;
import com.openhis.whale.booking.domain.template.entity.ScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.SlotProvisionTemplate;
import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplate;
import com.openhis.whale.booking.domain.template.entity.WeekScheduleTemplateItem;
import com.openhis.whale.booking.domain.template.queries.model.ResolvedDaySchedulePlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DayScheduleTemplateResolverTest {

    private static final Long ORG_ID = 100L;
    private static final Long WEEK_ID = 200L;
    private static final Long DAY_TPL_ID = 300L;
    private static final LocalDate MONDAY = LocalDate.of(2026, 6, 15);

    @Mock
    private WeekScheduleTemplateQueryService weekScheduleTemplateQueryService;
    @Mock
    private TemplateQueryService templateQueryService;
    @Mock
    private CalendarExceptionQueryService calendarExceptionQueryService;

    @InjectMocks
    private DayScheduleTemplateResolver resolver;

    private WeekScheduleTemplate weekTemplate;
    private ScheduleTemplate dayTemplate;

    @BeforeEach
    void setUp() {
        weekTemplate = new WeekScheduleTemplate();
        weekTemplate.setId(WEEK_ID);
        weekTemplate.setOrganizationId(ORG_ID);
        weekTemplate.setFallbackBookable(true);
        weekTemplate.setEnabled(true);

        dayTemplate = new ScheduleTemplate();
        dayTemplate.setId(DAY_TPL_ID);
        dayTemplate.setOrganizationId(ORG_ID);
        dayTemplate.setEnabled(true);
        dayTemplate.setHealthcareServiceId(1L);
        dayTemplate.setSlotProvisionTemplateId(400L);
    }

    private void stubOpenDay() {
        DayGeneratePlanModel open = new DayGeneratePlanModel();
        open.setSkipNormal(false);
        when(calendarExceptionQueryService.resolveDayGeneratePlan(eq(ORG_ID), eq(MONDAY))).thenReturn(open);
    }

    @Test
    void resolveWeekFallbackPlans_bookable_returnsVirtualPeriods() {
        stubOpenDay();
        WeekScheduleTemplateItem item = new WeekScheduleTemplateItem();
        item.setWeekday(1);
        item.setDayScheduleTemplateId(DAY_TPL_ID);
        when(weekScheduleTemplateQueryService.findDefaultForOrganization(ORG_ID)).thenReturn(weekTemplate);
        when(weekScheduleTemplateQueryService.listEnabledItemsForWeekday(WEEK_ID, 1)).thenReturn(List.of(item));
        when(templateQueryService.loadScheduleTemplateById(DAY_TPL_ID)).thenReturn(dayTemplate);

        SlotProvisionTemplate t1 = new SlotProvisionTemplate();
        t1.setSlotTemplatesJson("[{\"startTime\":\"08:00\",\"endTime\":\"11:30\",\"capacity\":10},"
            + "{\"startTime\":\"14:00\",\"endTime\":\"17:00\",\"capacity\":8}]");
        when(templateQueryService.loadSlotProvisionTemplateById(400L)).thenReturn(t1);

        List<ResolvedDaySchedulePlan> plans = resolver.resolveWeekFallbackPlans(ORG_ID, MONDAY, null, null, null);
        assertEquals(1, plans.size());
        assertTrue(plans.get(0).isBookable());
        assertEquals(2, plans.get(0).getVirtualPeriods().size());
    }

    @Test
    void resolveWeekFallbackPlans_closedDay_returnsEmpty() {
        DayGeneratePlanModel closed = new DayGeneratePlanModel();
        closed.setSkipNormal(true);
        when(calendarExceptionQueryService.resolveDayGeneratePlan(any(), any())).thenReturn(closed);

        List<ResolvedDaySchedulePlan> plans = resolver.resolveWeekFallbackPlans(ORG_ID, MONDAY, null, null, null);
        assertTrue(plans.isEmpty());
    }

    @Test
    void resolveWeekFallbackPlans_notBookable_noVirtualPeriods() {
        stubOpenDay();
        weekTemplate.setFallbackBookable(false);
        WeekScheduleTemplateItem item = new WeekScheduleTemplateItem();
        item.setWeekday(1);
        item.setDayScheduleTemplateId(DAY_TPL_ID);
        when(weekScheduleTemplateQueryService.findDefaultForOrganization(ORG_ID)).thenReturn(weekTemplate);
        when(weekScheduleTemplateQueryService.listEnabledItemsForWeekday(WEEK_ID, 1)).thenReturn(List.of(item));
        when(templateQueryService.loadScheduleTemplateById(DAY_TPL_ID)).thenReturn(dayTemplate);

        List<ResolvedDaySchedulePlan> plans = resolver.resolveWeekFallbackPlans(ORG_ID, MONDAY, null, null, null);
        assertEquals(1, plans.size());
        assertFalse(plans.get(0).isBookable());
        assertTrue(plans.get(0).getVirtualPeriods().isEmpty());
    }
}
