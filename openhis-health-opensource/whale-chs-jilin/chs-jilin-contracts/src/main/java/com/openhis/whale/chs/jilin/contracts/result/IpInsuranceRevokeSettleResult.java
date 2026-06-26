/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【2305】撤销住院正式结算结果。 */
@Data
@Accessors(chain = true)
public class IpInsuranceRevokeSettleResult {

    /** 被撤销的结算回执 id。 */
    private Long settlementRecordId;
    /** 原 setl_id。 */
    private String setlId;
    /** 原 mdtrt_id。 */
    private String mdtrtId;
}
