/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.medtech.response;

import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 医技预约列表分页响应。
 */
@Getter
@Setter
public class PageDiagnosticBookingsResponse extends PagedResult<ServiceRequestItemDto> {
}
