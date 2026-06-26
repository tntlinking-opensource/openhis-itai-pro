/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 住院正式结算确认命令（本地落账前 Extension 2304）。 */
@Data
@Accessors(chain = true)
public class InpatientChargeConfirmCommand {

    /** 收银草稿 id。 */
    private Long paymentDraftId;
    /** 预结算快照 id。 */
    private Long preSettleId;
    /** 就诊 id。 */
    private Long encounterId;
}
