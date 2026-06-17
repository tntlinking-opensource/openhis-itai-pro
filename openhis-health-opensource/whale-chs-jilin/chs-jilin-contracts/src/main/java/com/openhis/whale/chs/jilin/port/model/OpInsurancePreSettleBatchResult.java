/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.port.model;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/** 门诊医保混合预结算结果。 */
@Data
@Accessors(chain = true)
public class OpInsurancePreSettleBatchResult {

    private List<OpInsurancePreSettleGroupResult> groups;
    private OpSelfPayRollupPortDto selfPayRollup;
    /** Σ 各组 psnCashPay + 自费原价。 */
    private BigDecimal totalPsnCashPay;
}
