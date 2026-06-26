/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.inpatient.dto;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/** 2303 预结算结果。 */
@Data
@Accessors(chain = true)
public class IpPreSettleResult {

    /** 预结算快照主键。 */
    private Long preSettleId;

    /** 平台就诊 ID mdtrt_id。 */
    private String mdtrtId;

    /** 结算模式（出院全结/中途结算等）。 */
    private String settleMode;

    /** 中途结算标志。 */
    private String midSetlFlag;

    /** 医疗费总额。 */
    private BigDecimal medfeeSumamt;

    /** 基金支付总额。 */
    private BigDecimal fundPaySumamt;

    /** 个人账户支出。 */
    private BigDecimal acctPay;

    /** 个人现金支出。 */
    private BigDecimal psnCashPay;
}
