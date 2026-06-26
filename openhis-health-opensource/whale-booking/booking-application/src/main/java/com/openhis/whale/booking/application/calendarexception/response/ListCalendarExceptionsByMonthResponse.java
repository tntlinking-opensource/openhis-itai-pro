package com.openhis.whale.booking.application.calendarexception.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 月历标记响应。
 */
@Getter
@Setter
public class ListCalendarExceptionsByMonthResponse {

    /** 月历每日标记列表。 */
    private List<CalendarExceptionMonthDayDto> days = new ArrayList<>();
}
