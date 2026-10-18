/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.reconciliation.result;

import lombok.Data;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * 清算/日对账本地金额汇总行（按险种 + 清算类别分组后的一条）。
 * <p>金额来自 HIS 结算快照与同窗撤销抵减，不是医保平台对账回写结果；
 * 平台对账结果见日账流水（{@code stmt_rslt} 等）。</p>
 * <p>口径：四项金额正向累加、同窗撤销抵减；{@code setlCount} 含正向与撤销。</p>
 */
@Data
@Accessors(chain = true)
public class ClearAmountSummaryResult {

    /** 险种代码（平台 {@code insutype}），空串表示快照未落险种。 */
    private String insutype;

    /** 清算类别（平台 {@code clr_type}，如 11 门诊 / 21 住院）。 */
    private String clrType;

    /** 医疗费总额。 */
    private BigDecimal medfeeSumamt;

    /** 医保认可费用（与医疗费同源合计，供 3203A 预览）。 */
    private BigDecimal medSumfee;

    /** 基金支出合计（对应结算快照 {@code fund_pay_sumamt}）。 */
    private BigDecimal fundAppySum;

    /** 现金支付合计（对应结算快照 {@code psn_cash_pay}）。 */
    private BigDecimal cashPayamt;

    /** 个人账户支付合计。 */
    private BigDecimal acctPay;

    /** 结算笔数。 */
    private Integer setlCount;
}
