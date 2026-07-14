/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 门诊预结算结果（2206 setlinfo 摘要，health 中性字段名）。 */
@Data
@Accessors(chain = true)
public class OutpatientChargePreSettleExtensionOut {

    /** 预结算快照 id。 */
    private Long preSettleId;
    /** 收费批次号 chrg_bchno。 */
    private String chargeBatchNo;
    /** 平台就诊 ID mdtrt_id。 */
    private String encounterPlatformId;
    /** 医疗费总额。 */
    private BigDecimal medfeeSumamt;
    /** 基金支付总额。 */
    private BigDecimal fundPaySumamt;
    /** 个人账户支出。 */
    private BigDecimal acctPay;
    /** 个人现金支出（→ 收银 displayAmount）。 */
    private BigDecimal psnCashPay;
}
