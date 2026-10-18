/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.bed.response;

import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 床位预留列表分页响应。
 */
@Getter
@Setter
public class PageBedReservationsResponse extends PagedResult<BedReservationListItemDto> {
}
