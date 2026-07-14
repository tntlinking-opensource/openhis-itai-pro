/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.calendarexception.response;

import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * T3 分页响应。
 */
@Getter
@Setter
public class PageCalendarExceptionsResponse extends PagedResult<CalendarExceptionDto> {
}
