/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.application.register.response;

import com.zxplat.plugin.booking.domain.masterdata.result.BookingPatientItem;
import com.zxplat.framework.core.application.dto.PagedResult;
import lombok.Getter;
import lombok.Setter;

/**
 * 患者检索分页响应（4.7）。
 */
@Getter
@Setter
public class PagePatientsForSelectResponse extends PagedResult<BookingPatientItem> {
}
