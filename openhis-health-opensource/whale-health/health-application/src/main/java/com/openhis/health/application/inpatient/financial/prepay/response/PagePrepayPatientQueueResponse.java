/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.prepay.response;

import com.openhis.health.application.inpatient.financial.prepay.dto.PrepayQueueItemDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * 预交金在院患者队列分页响应（B.10.11）。
 */
public class PagePrepayPatientQueueResponse extends PagedResult<PrepayQueueItemDto> {
}
