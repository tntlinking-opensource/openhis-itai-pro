package com.openhis.whale.booking.application.calendarexception.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * T3 日历例外 DTO。
 */
@Getter
@Setter
public class CalendarExceptionDto {

    private Long id;

    private Long organizationId;

    private LocalDate exceptionDate;

    private LocalDate rangeEnd;

    private String exceptionType;

    private Long overrideScheduleTemplateId;

    private String reason;

    private Boolean autoApplyOnGenerate;
}
