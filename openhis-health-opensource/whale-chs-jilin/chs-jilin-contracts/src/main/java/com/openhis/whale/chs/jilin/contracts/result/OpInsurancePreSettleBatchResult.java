/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.result;

import com.openhis.whale.chs.jilin.contracts.dto.OpSelfPayRollupDto;
import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/** 门诊医保混合预结算结果（多组 2204+2206 汇总）。 */
@Data
@Accessors(chain = true)
public class OpInsurancePreSettleBatchResult {

    /** 各结算组预结算明细（按 accountId|medType|diseCodg 分组）。 */
    private List<OpInsurancePreSettleGroupResult> groups;
    /** 纯自费项汇总（不参与医保预结算的 chargeItem）。 */
    private OpSelfPayRollupDto selfPayRollup;
    /** 患者应付现金总额 = Σ 各组 psnCashPay + 自费原价。 */
    private BigDecimal totalPsnCashPay;
}
