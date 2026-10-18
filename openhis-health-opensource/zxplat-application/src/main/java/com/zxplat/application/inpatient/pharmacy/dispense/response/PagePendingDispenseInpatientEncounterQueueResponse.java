/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.inpatient.pharmacy.dispense.response;

import com.zxplat.application.inpatient.pharmacy.dispense.dto.PendingDispenseInpatientEncounterQueueRowDto;
import com.zxplat.framework.core.application.dto.PagedResult;

/**
 * 住院发药在院患者队列分页响应（IF-IPP-02）。
 */
public class PagePendingDispenseInpatientEncounterQueueResponse
    extends PagedResult<PendingDispenseInpatientEncounterQueueRowDto> {
}
