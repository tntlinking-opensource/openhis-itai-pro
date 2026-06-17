package com.openhis.whale.booking.application.outpatient.response;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

/**
 * 日历单日格子。
 */
@Getter
@Setter
public class ScheduleCalendarDayDto {

    /** 日历日。 */
    private LocalDate date;

    /** 当日可见日程摘要列表。 */
    private List<ScheduleCalendarCellDto> schedules;
}
