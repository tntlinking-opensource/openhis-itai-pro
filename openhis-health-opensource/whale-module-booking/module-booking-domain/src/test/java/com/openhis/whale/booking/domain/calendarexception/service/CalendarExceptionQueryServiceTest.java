/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.calendarexception.service;

import com.openhis.whale.booking.domain.calendarexception.entity.CalendarException;
import com.openhis.whale.booking.domain.calendarexception.queries.model.DayGeneratePlanModel;
import com.openhis.whale.booking.domain.calendarexception.repository.CalendarExceptionRepository;
import com.openhis.whale.booking.domain.share.CalendarExceptionTypeConstants;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * TC-TPL-01/02：T3 日历例外解析验收。
 */
@ExtendWith(MockitoExtension.class)
class CalendarExceptionQueryServiceTest {

    private static final Long ORG_ID = 100L;
    private static final LocalDate DAY = LocalDate.of(2026, 10, 1);

    @Mock
    private CalendarExceptionRepository calendarExceptionRepository;

    @InjectMocks
    private CalendarExceptionQueryService queryService;

    @Test
    void resolveDayGeneratePlan_closed_skipsNormalGeneration() {
        when(calendarExceptionRepository.findAll(any(Specification.class), any(Sort.class)))
            .thenReturn(List.of(exception(CalendarExceptionTypeConstants.CLOSED, null)));

        DayGeneratePlanModel plan = queryService.resolveDayGeneratePlan(ORG_ID, DAY);

        assertThat(plan.isSkipNormal()).isTrue();
        assertThat(plan.isGenerateExtra()).isFalse();
    }

    @Test
    void resolveDayGeneratePlan_extra_generatesAdditionalSchedule() {
        when(calendarExceptionRepository.findAll(any(Specification.class), any(Sort.class)))
            .thenReturn(List.of(exception(CalendarExceptionTypeConstants.EXTRA, 501L)));

        DayGeneratePlanModel plan = queryService.resolveDayGeneratePlan(ORG_ID, DAY);

        assertThat(plan.isSkipNormal()).isFalse();
        assertThat(plan.isGenerateExtra()).isTrue();
        assertThat(plan.getExtraTemplateId()).isEqualTo(501L);
    }

    @Test
    void resolveDayGeneratePlan_override_replacesTemplate() {
        when(calendarExceptionRepository.findAll(any(Specification.class), any(Sort.class)))
            .thenReturn(List.of(exception(CalendarExceptionTypeConstants.OVERRIDE, 502L)));

        DayGeneratePlanModel plan = queryService.resolveDayGeneratePlan(ORG_ID, DAY);

        assertThat(plan.getOverrideTemplateId()).isEqualTo(502L);
        assertThat(plan.isSkipNormal()).isFalse();
    }

    @Test
    void resolveDayGeneratePlan_autoApplyOff_isIgnored() {
        CalendarException row = exception(CalendarExceptionTypeConstants.CLOSED, null);
        row.setAutoApplyOnGenerate(Boolean.FALSE);
        when(calendarExceptionRepository.findAll(any(Specification.class), any(Sort.class)))
            .thenReturn(List.of(row));

        DayGeneratePlanModel plan = queryService.resolveDayGeneratePlan(ORG_ID, DAY);

        assertThat(plan.isSkipNormal()).isFalse();
        assertThat(plan.isGenerateExtra()).isFalse();
    }

    private static CalendarException exception(String type, Long overrideTemplateId) {
        CalendarException row = new CalendarException();
        row.setOrganizationId(ORG_ID);
        row.setExceptionDate(DAY);
        row.setExceptionType(type);
        row.setOverrideScheduleTemplateId(overrideTemplateId);
        row.setAutoApplyOnGenerate(Boolean.TRUE);
        row.setIsDeleted(Boolean.FALSE);
        return row;
    }
}
