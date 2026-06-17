/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

/** 2205 门诊费用明细撤销命令。 */
@Data
@Accessors(chain = true)
public class OpInsuranceRevokeFeeDetailCommand {

    /** 收费批次号（与预结算快照一致）。 */
    private String chrgBchno;
    /** 支付草稿 ID（chrgBchno 缺失时按 ACTIVE 预结算解析）。 */
    private Long paymentDraftId;
}
