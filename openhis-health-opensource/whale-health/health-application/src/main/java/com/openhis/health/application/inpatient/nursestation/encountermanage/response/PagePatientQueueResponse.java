/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.nursestation.encountermanage.response;

import com.openhis.health.application.inpatient.nursestation.encountermanage.dto.PatientQueueItemDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * 在院患者列表分页响应（B.10.11）。
 */
public class PagePatientQueueResponse extends PagedResult<PatientQueueItemDto> {
}
