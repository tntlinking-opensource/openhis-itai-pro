/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.whale.chs.jilin.contracts.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 【2303】住院预结算结果。 */
@Data
@Accessors(chain = true)
public class IpInsurancePreSettleResult {

    /** 预结算快照 {@code yb_inpatient_pre_settle.id}。 */
    private Long preSettleId;
    /** 费用性质 id。 */
    private Long contractId;
    /** 平台就诊 ID。 */
    private String mdtrtId;
    /** 正式结算后的 setl_id（预结算阶段通常为空）。 */
    private String setlId;
    /** 结算模式。 */
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
