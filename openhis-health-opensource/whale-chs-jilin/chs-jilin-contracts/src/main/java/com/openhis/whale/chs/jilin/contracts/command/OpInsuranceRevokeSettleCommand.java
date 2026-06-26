/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【2208】门诊正式结算撤销命令。 */
@Data
@Accessors(chain = true)
public class OpInsuranceRevokeSettleCommand {

    /** 本地结算回执 id（优先）。 */
    private Long settlementRecordId;
    /** 收银 payment id（回执定位）。 */
    private Long paymentId;
    /** 预结算快照 id（可选）。 */
    private Long preSettleId;
    /** 平台结算 ID setl_id（可选）。 */
    private String setlId;
}
