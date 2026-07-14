/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.inpatient.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 住院预结算结果（2303 setlinfo 摘要）。 */
@Data
@Accessors(chain = true)
public class InpatientChargePreSettleExtensionOut {

    /** 预结算快照 id；批次键为 {@code IP{preSettleId}}。 */
    private Long preSettleId;
    /** 费用性质 id。 */
    private Long contractId;
    /** 平台就诊 ID mdtrt_id。 */
    private String encounterPlatformId;
    /** 正式结算后的 setl_id（预结算阶段通常为空）。 */
    private String settlementId;
    /** 结算模式 DISCHARGE_FULL / MID_SETTLE。 */
    private String settleMode;
    /** 医疗费总额。 */
    private BigDecimal medfeeSumamt;
    /** 基金支付总额。 */
    private BigDecimal fundPaySumamt;
    /** 个人账户支出。 */
    private BigDecimal acctPay;
    /** 个人现金支出。 */
    private BigDecimal psnCashPay;
}
