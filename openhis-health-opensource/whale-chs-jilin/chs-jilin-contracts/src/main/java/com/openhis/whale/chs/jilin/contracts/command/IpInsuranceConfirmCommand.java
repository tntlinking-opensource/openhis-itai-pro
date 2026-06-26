/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【2304】住院正式结算命令。 */
@Data
@Accessors(chain = true)
public class IpInsuranceConfirmCommand {

    /** 收银草稿 id。 */
    private Long paymentDraftId;
    /** 预结算快照 id（与 draft 二选一）。 */
    private Long preSettleId;
    /** 就诊 id。 */
    private Long encounterId;
}
