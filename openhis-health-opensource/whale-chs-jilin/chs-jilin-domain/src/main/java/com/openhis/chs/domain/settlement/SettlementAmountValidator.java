/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.settlement;

import java.math.BigDecimal;

import com.alibaba.fastjson2.JSONObject;
import com.openhis.chs.domain.settlement.model.SettlementAmounts;

/** 规范 §2206 金额恒等式校验（容差 0.01）。 */
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
     * @return 是否在容差内
     */
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
     * 从 setlinfo JSON 构造金额值对象。
     */
    public static SettlementAmounts fromSetlinfo(JSONObject setlinfo) {
        if (setlinfo == null) {
            return SettlementAmounts.of(null, null, null, null, null, null, null, null);
        }
        return SettlementAmounts.of(
            decimal(setlinfo, "medfee_sumamt"),
            decimal(setlinfo, "fund_pay_sumamt"),
            decimal(setlinfo, "psn_part_amt"),
            decimal(setlinfo, "hosp_part_amt"),
            decimal(setlinfo, "fulamt_ownpay_amt"),
            decimal(setlinfo, "overlmt_selfpay"),
            decimal(setlinfo, "preselfpay_amt"),
            decimal(setlinfo, "inscp_scp_amt"));
    }

    private static BigDecimal decimal(JSONObject json, String key) {
        if (json == null || !json.containsKey(key)) {
            return null;
        }
        Object raw = json.get(key);
        if (raw == null) {
            return null;
        }
        if (raw instanceof BigDecimal value) {
            return value;
        }
        if (raw instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty()) {
            return null;
        }
        return new BigDecimal(text);
    }

    private static BigDecimal defaultZero(BigDecimal value) {
        if (value != null) {
            return value;
        }
        return BigDecimal.ZERO;
    }
}
