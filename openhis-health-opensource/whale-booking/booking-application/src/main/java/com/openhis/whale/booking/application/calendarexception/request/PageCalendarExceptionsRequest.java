package com.openhis.whale.booking.application.calendarexception.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * T3 日历例外分页请求。
 */
@Getter
@Setter
public class PageCalendarExceptionsRequest {

    private Long organizationId;

    private String exceptionType;

    private LocalDate rangeStart;

    private LocalDate rangeEnd;

    private String keyword;

    @NotNull
    private Integer pageNo;

    @NotNull
    private Integer pageSize;
}
