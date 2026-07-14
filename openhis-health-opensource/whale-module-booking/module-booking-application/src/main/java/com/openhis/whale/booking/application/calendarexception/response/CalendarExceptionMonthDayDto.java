/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
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

    /** 日期（yyyy-MM-dd）。 */
    private String date;

    /** 是否属于查询月份。 */
    private Boolean inMonth;

    /** 当日例外类型列表。 */
    private List<String> exceptionTypes = new ArrayList<>();
}
