/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.infrastructure.integration.insurance.outpatient.ExtensionOut;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 支付草稿下 ACTIVE 预结算批次快照（confirm 前解析 chrg_bchno / 权威现金）。 */
@Data
@Accessors(chain = true)
public class OutpatientChargePreSettleQueryExtensionOut {

    /** 收费批次号 chrg_bchno。 */
    private String chargeBatchNo;

    /** 人员编号 psn_no（刷新预结算时复用）。 */
    private String insuredPersonNo;

    /** 参保地医保区划。 */
    private String insuplcAdmdvs;

    /** 险种类型。 */
    private String insutype;

    /** 就诊凭证类型。 */
    private String payerCertType;

    /** 是否使用个账 acct_used_flag（1/0）。 */
    private String acctUsedFlag;

    /** 医疗费总额 medfee_sumamt（权威现金门禁）。 */
    private BigDecimal medfeeSumamt;

    /** 个人现金支出 psn_cash_pay（权威现金门禁）。 */
    private BigDecimal psnCashPay;
}
