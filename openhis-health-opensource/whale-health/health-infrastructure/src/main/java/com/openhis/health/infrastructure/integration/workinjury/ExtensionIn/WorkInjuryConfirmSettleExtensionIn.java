/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.workinjury.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 工伤本地落账前确认入参。 */
@Data
@Accessors(chain = true)
public class WorkInjuryConfirmSettleExtensionIn {

    private Long paymentDraftId;
    private Long paymentId;
    private String chrgBchno;
    private Long encounterId;
}
