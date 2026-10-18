/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.inpatient.ExtensionIn;

import lombok.Data;
import lombok.experimental.Accessors;

/** 住院正式结算确认命令（本地落账前 Extension 2304）。 */
@Data
@Accessors(chain = true)
public class InpatientChargeConfirmExtensionIn {

    /** 收银草稿 id。 */
    private Long paymentDraftId;
    /** 预结算快照 id。 */
    private Long preSettleId;
    /** 就诊 id。 */
    private Long encounterId;
}
