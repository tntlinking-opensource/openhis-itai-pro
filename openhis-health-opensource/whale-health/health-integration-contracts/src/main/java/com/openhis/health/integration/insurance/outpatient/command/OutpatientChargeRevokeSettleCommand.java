/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.outpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 门诊正式结算撤销命令（退费前 Extension 2208）。 */
@Data
@Accessors(chain = true)
public class OutpatientChargeRevokeSettleCommand {

    /** 本地结算回执 {@code yb_settlement_record.id}（优先）。 */
    private Long settlementRecordId;
    /** 原收银 payment id。 */
    private Long paymentId;
    /** 预结算快照 id。 */
    private Long preSettleId;
    /** 平台 setl_id。 */
    private String settlementId;
}
