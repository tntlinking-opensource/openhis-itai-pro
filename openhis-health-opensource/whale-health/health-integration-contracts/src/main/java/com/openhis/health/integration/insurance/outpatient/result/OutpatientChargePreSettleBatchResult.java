/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.health.integration.insurance.outpatient.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/** 门诊混合预结算结果（多组 2206 + 自费汇总）。 */
@Data
@Accessors(chain = true)
public class OutpatientChargePreSettleBatchResult {

    /** 各结算组预结算明细。 */
    private List<OutpatientChargePreSettleGroupResult> groups;
    /** 纯自费项汇总（不调医保）。 */
    private OutpatientChargeSelfPayRollupResult selfPayRollup;
    /** 患者应付现金 = Σ psnCashPay + 自费原价。 */
    private BigDecimal totalPsnCashPay;
}
