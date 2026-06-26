/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【2208】门诊正式结算撤销结果。 */
@Data
@Accessors(chain = true)
public class OpInsuranceRevokeSettleResult {

    /** 被撤销的结算回执 id。 */
    private Long settlementRecordId;
    /** 原 setl_id。 */
    private String setlId;
    /** 原 mdtrt_id。 */
    private String mdtrtId;
}
