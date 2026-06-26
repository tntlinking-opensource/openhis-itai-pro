/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【2601】冲正命令（Port 层，由补偿 Listener 触发）。 */
@Data
@Accessors(chain = true)
public class OpInsuranceCorrectSettleCommand {

    /** 结算回执 id，用于反查 omsgid。 */
    private Long settlementRecordId;
    /** 原交易 infno；缺省 2207。 */
    private String originalInfno;
}
