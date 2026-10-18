/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;
import java.util.List;

/** 门诊混合预结算结果（多组 2206 + 自费汇总）。 */
@Data
@Accessors(chain = true)
public class OutpatientChargePreSettleBatchExtensionOut {

    /** 各结算组预结算明细。 */
    private List<OutpatientChargePreSettleGroupExtensionOut> groups;
    /** 纯自费项汇总（不调医保）。 */
    private OutpatientChargeSelfPayRollupExtensionOut selfPayRollup;
    /** 患者应付现金 = Σ psnCashPay + 自费原价。 */
    private BigDecimal totalPsnCashPay;
}
