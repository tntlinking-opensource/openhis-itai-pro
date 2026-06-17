package com.openhis.whale.booking.application.calendarexception.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * 按主键加载/删除日历例外。
 */
@Getter
@Setter
public class CalendarExceptionIdRequest {

    @NotNull
    private Long id;
}
