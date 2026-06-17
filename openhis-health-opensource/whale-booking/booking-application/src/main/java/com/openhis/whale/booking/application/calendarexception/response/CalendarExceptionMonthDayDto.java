package com.openhis.whale.booking.application.calendarexception.response;

import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

/**
 * 月历单日例外标记。
 */
@Getter
@Setter
public class CalendarExceptionMonthDayDto {

    private String date;

    private Boolean inMonth;

    private List<String> exceptionTypes = new ArrayList<>();
}
