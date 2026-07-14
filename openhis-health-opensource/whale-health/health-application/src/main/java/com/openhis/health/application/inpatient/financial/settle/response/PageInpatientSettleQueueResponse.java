/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.response;

import com.openhis.health.application.inpatient.financial.settle.dto.InpatientSettleQueueItemDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * 住院结算队列分页响应（IF-IP-STL-02，B.10.11）。
 */
public class PageInpatientSettleQueueResponse extends PagedResult<InpatientSettleQueueItemDto> {
}
