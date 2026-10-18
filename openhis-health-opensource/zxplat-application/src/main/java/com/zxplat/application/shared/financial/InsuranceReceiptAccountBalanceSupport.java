/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.financial;

import com.zxplat.domain.share.utils.MonetaryAmountSupport;

import java.math.BigDecimal;

/**
 * 门诊收费/挂号小票「医保个人账户余额」展示口径。
 *
 * <p>小票展示<strong>本笔扣款后</strong>剩余个账余额。吉林等地 2207
 * {@code setlinfo.balc} 实测为<strong>结算后</strong>余额（对账：
 * {@code 1101.balc − acct_pay = 2207.balc}），有回执时原样展示，
 * <strong>禁止</strong>再减 {@code acct_pay}。
 * 无回执 {@code balc} 时回落支付明细 {@code afterBalance}（已是扣后）。</p>
 */
public final class InsuranceReceiptAccountBalanceSupport {

    private InsuranceReceiptAccountBalanceSupport() {
    }

    /**
     * 解析小票个账余额（结算刻度 2 位）。
     *
     * <p>优先 2207 {@code balc}（已是扣后，原样展示）；
     * 无回执时回落支付明细 {@code afterBalance}。</p>
     *
     * @param settlementBalc         2207 回执 balc；可为 null
     * @param afterBalanceFromDetail 支付明细 afterBalance；可为 null
     * @return 扣后余额；两侧来源皆空则 null
     */
    public static BigDecimal resolveReceiptAccountBalance(
        BigDecimal settlementBalc,
        BigDecimal afterBalanceFromDetail) {
        // 正式回执 balc 优先：平台口径为结算后，禁止再减个账支出
        if (settlementBalc != null) {
            return MonetaryAmountSupport.scaleSettle(settlementBalc);
        }
        // 无回执 balc：afterBalance 已是扣后
        if (afterBalanceFromDetail != null) {
            return MonetaryAmountSupport.scaleSettle(afterBalanceFromDetail);
        }
        return null;
    }
}
