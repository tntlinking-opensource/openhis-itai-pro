/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.outpatient.financial.charge.request;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 就诊收费项转自费请求。
 */
@Data
@Accessors(chain = true)
public class SwitchEncounterChargeItemsToSelfPayRequest {

    /** 就诊主键。 */
    private Long encounterId;
}
