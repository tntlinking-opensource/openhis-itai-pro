/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.register.response;

import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 预约列表分页响应（4.5）。
 */
@Getter
@Setter
public class PageBookingsResponse extends PagedResult<BookingListItemDto> {
}
