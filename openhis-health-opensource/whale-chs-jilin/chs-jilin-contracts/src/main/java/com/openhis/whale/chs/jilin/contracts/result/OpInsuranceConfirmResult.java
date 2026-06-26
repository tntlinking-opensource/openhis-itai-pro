/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 门诊医保正式结算结果（2207 凭证摘要）。 */
@Data
@Accessors(chain = true)
public class OpInsuranceConfirmResult {

    /** 平台结算 ID setl_id。 */
    private String setlId;
    /** 收费批次号 chrg_bchno。 */
    private String chrgBchno;
    /** 平台就诊 ID mdtrt_id。 */
    private String mdtrtId;
    /** 本地结算回执 {@code yb_settlement_record.id}。 */
    private Long settlementRecordId;
    /** 费用账户 id（混合结算分组用）。 */
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
