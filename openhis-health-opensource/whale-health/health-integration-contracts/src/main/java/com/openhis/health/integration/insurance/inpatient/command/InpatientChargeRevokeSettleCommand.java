/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.inpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 住院正式结算撤销命令（Extension 2305）。 */
@Data
@Accessors(chain = true)
public class InpatientChargeRevokeSettleCommand {

    /** 结算回执 id。 */
    private Long settlementRecordId;
    /** payment id。 */
    private Long paymentId;
    /** 预结算 id。 */
    private Long preSettleId;
    /** 平台 setl_id。 */
    private String settlementId;
}
