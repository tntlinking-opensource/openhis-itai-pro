/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.infrastructure.integration.insurance.outpatient.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 2207 正式结算确认结果（供 fin_payment_rec_detail 写基金/个账行）。 */
@Data
@Accessors(chain = true)
public class OutpatientChargeConfirmExtensionOut {

    /** 平台结算 ID setl_id。 */
    private String settlementId;
    /** 收费批次号 chrg_bchno。 */
    private String chargeBatchNo;
    /** 本地结算回执 id。 */
    private Long settlementRecordId;
    /** 平台就诊 ID mdtrt_id。 */
    private String mdtrtId;
    /** 费用账户 id（混合结算挂接）。 */
    private Long accountId;
    /** 医疗费总额。 */
    private BigDecimal medfeeSumamt;
    /** 基金支付总额。 */
    private BigDecimal fundPaySumamt;
    /** 个人账户支出。 */
    private BigDecimal acctPay;
    /** 个人现金支出。 */
    private BigDecimal psnCashPay;
}
