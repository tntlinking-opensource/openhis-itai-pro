/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【2302】撤销住院费用明细结果。 */
@Data
@Accessors(chain = true)
public class IpInsuranceRevokeFeeDetailResult {

    /** 成功撤销的平台费用行条数。 */
    private int revokedCount;
}
