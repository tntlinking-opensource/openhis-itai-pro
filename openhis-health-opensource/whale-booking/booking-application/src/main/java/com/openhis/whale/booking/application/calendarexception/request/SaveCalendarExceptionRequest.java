package com.openhis.whale.booking.application.calendarexception.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;

/**
 * 保存 T3 日历例外。
 */
@Getter
@Setter
public class SaveCalendarExceptionRequest {

    private Long id;

    private Long organizationId;

    @NotNull
    private LocalDate exceptionDate;

    private LocalDate rangeEnd;

    @NotBlank
    private String exceptionType;

    private Long overrideScheduleTemplateId;

    private String reason;

    private Boolean autoApplyOnGenerate;
}
