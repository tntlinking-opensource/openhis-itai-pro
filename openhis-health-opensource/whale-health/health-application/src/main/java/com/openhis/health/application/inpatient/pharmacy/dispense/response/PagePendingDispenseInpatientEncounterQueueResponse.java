/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.pharmacy.dispense.response;

import com.openhis.health.application.inpatient.pharmacy.dispense.dto.PendingDispenseInpatientEncounterQueueRowDto;
import com.openhis.whale.core.application.dto.PagedResult;

/**
 * 住院发药在院患者队列分页响应（IF-IPP-02）。
 */
public class PagePendingDispenseInpatientEncounterQueueResponse
    extends PagedResult<PendingDispenseInpatientEncounterQueueRowDto> {
}
