/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.registration.response;

import com.openhis.health.application.inpatient.financial.registration.dto.RegistrationQueueItemDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * 住院登记待办列表分页响应（B.10.11）。
 */
public class PageRegistrationQueueResponse extends PagedResult<RegistrationQueueItemDto> {
}
