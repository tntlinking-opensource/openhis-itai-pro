/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.outpatient.command;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2601 冲正命令（2208 失败时 Compensation 降级调用）。 */
@Data
@Accessors(chain = true)
public class OutpatientChargeCorrectSettleCommand {

    /** 结算回执 id（反查 omsgid）。 */
    private Long settlementRecordId;
    /** 原交易 infno；缺省 2207。 */
    private String originalInfno;
}
