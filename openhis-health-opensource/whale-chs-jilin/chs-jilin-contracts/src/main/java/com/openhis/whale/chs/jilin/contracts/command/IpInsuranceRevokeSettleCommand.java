/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【2305】撤销住院正式结算命令。 */
@Data
@Accessors(chain = true)
public class IpInsuranceRevokeSettleCommand {

    /** 结算回执 id。 */
    private Long settlementRecordId;
    /** payment id。 */
    private Long paymentId;
    /** 预结算 id。 */
    private Long preSettleId;
    /** 平台 setl_id。 */
    private String setlId;
}
