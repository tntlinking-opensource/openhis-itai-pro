/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 门诊医保预结算结果（2206 报价快照摘要）。 */
@Data
@Accessors(chain = true)
public class OpInsurancePreSettleResult {

    /** 本地预结算快照 {@code yb_clinic_pre_settle.id}。 */
    private Long preSettleId;
    /** 收费批次号 chrg_bchno（2207 正式结算键）。 */
    private String chrgBchno;
    /** 平台就诊 ID mdtrt_id。 */
    private String mdtrtId;
    /** 医疗费总额 medfee_sumamt。 */
    private BigDecimal medfeeSumamt;
    /** 基金支付总额 fund_pay_sumamt。 */
    private BigDecimal fundPaySumamt;
    /** 个人账户支出 acct_pay。 */
    private BigDecimal acctPay;
    /** 个人现金支出 psn_cash_pay（收银应收）。 */
    private BigDecimal psnCashPay;
    /** 人员编号 psn_no。 */
    private String psnNo;
    /** 参保地医保区划。 */
    private String insuplcAdmdvs;
    /** 险种类型。 */
    private String insutype;
    /** 就诊凭证类型 mdtrt_cert_type。 */
    private String mdtrtCertType;
    /** 是否使用个账 acct_used_flag。 */
    private String acctUsedFlag;
}
