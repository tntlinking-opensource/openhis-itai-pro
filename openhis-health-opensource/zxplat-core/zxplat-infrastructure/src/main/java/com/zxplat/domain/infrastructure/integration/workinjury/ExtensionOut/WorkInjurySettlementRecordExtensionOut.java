/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.workinjury.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

/** 工伤结算回执摘要（退费/对账用）。 */
@Data
@Accessors(chain = true)
public class WorkInjurySettlementRecordExtensionOut {

    private Long settlementRecordId;
    private Long paymentId;
    private Long preSettleId;
    private Long contractId;
    private String chargeBatchNo;
    private String settlementId;
    private String encounterPlatformId;
    private String status;
    private String msgid2207;
}
