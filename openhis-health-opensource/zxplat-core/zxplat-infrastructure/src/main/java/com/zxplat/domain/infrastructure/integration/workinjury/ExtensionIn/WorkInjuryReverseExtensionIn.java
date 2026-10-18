/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.workinjury.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 工伤退费/撤销 invoke 启动入参。 */
@Data
@Accessors(chain = true)
public class WorkInjuryReverseExtensionIn {

    private Long settlementRecordId;
    private Long paymentId;
    private Long encounterId;
    private Long contractId;
    private Long operatorId;
}
