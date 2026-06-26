/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 【2304】住院正式结算结果。 */
@Data
@Accessors(chain = true)
public class IpInsuranceConfirmResult {

    /** 平台结算 ID setl_id。 */
    private String setlId;
    /** 批次键 IP{preSettleId}。 */
    private String chrgBchno;
    /** 平台就诊 ID。 */
    private String mdtrtId;
    /** 本地结算回执 id。 */
    private Long settlementRecordId;
    /** 医疗费总额。 */
    private BigDecimal medfeeSumamt;
    /** 基金支付总额。 */
    private BigDecimal fundPaySumamt;
    /** 个人账户支出。 */
    private BigDecimal acctPay;
    /** 个人现金支出。 */
    private BigDecimal psnCashPay;
}
