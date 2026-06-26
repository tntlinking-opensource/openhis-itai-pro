/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.result;

import lombok.Data;
import lombok.experimental.Accessors;

/** 【2205】撤销未正式结算费用明细结果。 */
@Data
@Accessors(chain = true)
public class OpInsuranceRevokeFeeDetailResult {

    /** 被撤销的 chrg_bchno。 */
    private String chrgBchno;
    /** true 表示已调平台 2205；false 表示快照非 ACTIVE 跳过。 */
    private boolean revoked;
}
