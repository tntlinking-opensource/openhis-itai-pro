/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.settlement;

import java.math.BigDecimal;

import com.zxplat.plugin.chs.domain.settlement.model.PlatformSetlinfoFields;
import com.zxplat.plugin.chs.domain.settlement.model.SettlementAmounts;

/** 规范 §2206 金额恒等式校验（容差 0.01） */
public final class SettlementAmountValidator {

    public static final BigDecimal TOLERANCE = new BigDecimal("0.01");

    private SettlementAmountValidator() {
    }

    /**
     * 校验 medfee ≈ fund + psn_part + hosp_part。
     *
     * @param medfeeSumamt 医疗费总额
     * @param fundPaySumamt 基金支付总额
     * @param psnPartAmt 个人负担总金额
     * @param hospPartAmt 医院负担金额
     * @return 是否在容差内 */
    public static boolean isMedfeeBalanced(
        BigDecimal medfeeSumamt,
        BigDecimal fundPaySumamt,
        BigDecimal psnPartAmt,
        BigDecimal hospPartAmt) {
        if (medfeeSumamt == null) {
            return true;
        }
        BigDecimal fund = defaultZero(fundPaySumamt);
        BigDecimal psn = defaultZero(psnPartAmt);
        BigDecimal hosp = defaultZero(hospPartAmt);
        BigDecimal sum = fund.add(psn).add(hosp);
        return medfeeSumamt.subtract(sum).abs().compareTo(TOLERANCE) <= 0;
    }

    /**
     * 从 setlinfo 字段构造金额值对象。
     *
     * @param setlinfo 平台 setlinfo
     * @return 金额值对象
     */
    public static SettlementAmounts fromPlatformFields(PlatformSetlinfoFields setlinfo) {
        if (setlinfo == null) {
            return SettlementAmounts.of(null, null, null, null, null, null, null, null);
        }
        return SettlementAmounts.of(
            setlinfo.getMedfeeSumamt(),
            setlinfo.getFundPaySumamt(),
            setlinfo.getPsnPartAmt(),
            setlinfo.getHospPartAmt(),
            setlinfo.getFulamtOwnpayAmt(),
            setlinfo.getOverlmtSelfpay(),
            setlinfo.getPreselfpayAmt(),
            setlinfo.getInscpScpAmt());
    }

    /**
     * 将 {@code null} 金额视为零。
     *
     * @param value 金额
     * @return 非空金额
     */
    private static BigDecimal defaultZero(BigDecimal value) {
        if (value != null) {
            return value;
        }
        return BigDecimal.ZERO;
    }
}
