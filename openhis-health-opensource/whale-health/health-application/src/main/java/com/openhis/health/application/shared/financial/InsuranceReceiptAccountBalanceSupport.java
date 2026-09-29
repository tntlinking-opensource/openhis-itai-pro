/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial;

import com.openhis.health.domain.share.utils.MonetaryAmountSupport;

import java.math.BigDecimal;

/**
 * 门诊收费/挂号小票「医保个人账户余额」展示口径。
 *
 * <p>小票应对患者展示<strong>本笔扣款后</strong>剩余个账余额。吉林等地 2207
 * {@code setlinfo.balc} 实测为结算前余额；有本笔个账支出时须减去 {@code acct_pay}
 *（规范：个人账户支出已含共济，勿再减 {@code acct_mulaid_pay}）。
 * 支付明细 {@code afterBalance} 若已是扣后余额，则直接采用，禁止再减。</p>
 */
public final class InsuranceReceiptAccountBalanceSupport {

    private InsuranceReceiptAccountBalanceSupport() {
    }

    /**
     * 解析小票个账余额（结算刻度 2 位）。
     *
     * <p>优先 2207 {@code balc}：有正数个账支出则 {@code balc − acctPay}；
     * 无支出则原样展示。无回执 {@code balc} 时回落支付明细 {@code afterBalance}（已是扣后，不再减）。</p>
     *
     * @param settlementBalc         2207 回执 balc；可为 null
     * @param afterBalanceFromDetail 支付明细 afterBalance；可为 null
     * @param acctPay                本笔医保个人账户支出（含共济）；可为 null
     * @return 扣后余额；两侧来源皆空则 null
     */
    public static BigDecimal resolveReceiptAccountBalance(
        BigDecimal settlementBalc,
        BigDecimal afterBalanceFromDetail,
        BigDecimal acctPay) {
        // 正式回执 balc 优先：平台口径为结算前，有支出则扣减后展示
        if (settlementBalc != null) {
            if (acctPay != null && acctPay.compareTo(BigDecimal.ZERO) > 0) {
                return MonetaryAmountSupport.scaleSettle(settlementBalc.subtract(acctPay));
            }
            return MonetaryAmountSupport.scaleSettle(settlementBalc);
        }
        // 无回执 balc：afterBalance 已是扣后，禁止二次扣减
        if (afterBalanceFromDetail != null) {
            return MonetaryAmountSupport.scaleSettle(afterBalanceFromDetail);
        }
        return null;
    }
}
