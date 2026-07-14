/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.application.slot.response;

import com.openhis.whale.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 号源日历分页响应（3.1）。
 */
@Getter
@Setter
public class PageSlotsByDateResponse extends PagedResult<SlotListItemDto> {
}
