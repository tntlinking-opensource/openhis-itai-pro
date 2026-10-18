/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.outpatient.support;

import com.zxplat.plugin.chs.domain.outpatient.result.OpPreSettleGroupResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SharedMdtrtDeductiblePreviewAdjusterTest {

    @Test
    void sameVisit_secondGroupReleasesDeductibleAtPoolRate() {
        OpPreSettleGroupResult coronary = group(
            "MD-1", "31.33", "0.6", "117.62", "51.37", "51.37", "0.00", "66.25");
        OpPreSettleGroupResult hypertension = group(
            "MD-1", "31.33", "0.6", "164.50", "79.37", "79.37", "0.00", "85.13");

        SharedMdtrtDeductiblePreviewAdjuster.adjust(List.of(coronary, hypertension));

        assertMoney("31.33", coronary.getActPayDedc());
        assertMoney("51.37", coronary.getFundPaySumamt());
        assertMoney("66.25", coronary.getPsnCashPay());
        assertMoney("0.00", hypertension.getActPayDedc());
        assertMoney("98.17", hypertension.getFundPaySumamt());
        assertMoney("98.17", hypertension.getHifpPay());
        assertMoney("66.33", hypertension.getPsnCashPay());
        assertMoney("0.00", hypertension.getAcctPay());
    }

    @Test
    void differentVisits_keepEachQuote() {
        OpPreSettleGroupResult first = group(
            "MD-1", "31.33", "0.6", "100.00", "40.00", "40.00", "0.00", "60.00");
        OpPreSettleGroupResult second = group(
            "MD-2", "31.33", "0.6", "80.00", "20.00", "20.00", "0.00", "60.00");

        SharedMdtrtDeductiblePreviewAdjuster.adjust(List.of(first, second));

        assertMoney("31.33", first.getActPayDedc());
        assertMoney("40.00", first.getFundPaySumamt());
        assertMoney("31.33", second.getActPayDedc());
        assertMoney("20.00", second.getFundPaySumamt());
    }

    @Test
    void partialFirstGroup_secondKeepsRemainder() {
        OpPreSettleGroupResult small = group(
            "MD-1", "10.00", "0.6", "20.00", "6.00", "6.00", "0.00", "14.00");
        OpPreSettleGroupResult large = group(
            "MD-1", "31.33", "0.6", "200.00", "100.00", "100.00", "0.00", "100.00");

        SharedMdtrtDeductiblePreviewAdjuster.adjust(List.of(small, large));

        assertMoney("10.00", small.getActPayDedc());
        assertMoney("6.00", small.getFundPaySumamt());
        assertMoney("21.33", large.getActPayDedc());
        assertMoney("106.00", large.getFundPaySumamt());
        assertMoney("94.00", large.getPsnCashPay());
    }

    @Test
    void missingPoolRate_keepsPlatformQuote() {
        OpPreSettleGroupResult first = group(
            "MD-1", "31.33", null, "100.00", "40.00", "40.00", "0.00", "60.00");
        OpPreSettleGroupResult second = group(
            "MD-1", "31.33", null, "80.00", "20.00", "20.00", "0.00", "60.00");

        SharedMdtrtDeductiblePreviewAdjuster.adjust(List.of(first, second));

        assertMoney("31.33", second.getActPayDedc());
        assertMoney("20.00", second.getFundPaySumamt());
        assertMoney("60.00", second.getPsnCashPay());
    }

    @Test
    void cashShortfall_spillsToAccount() {
        OpPreSettleGroupResult first = group(
            "MD-1", "31.33", "0.6", "100.00", "40.00", "40.00", "0.00", "60.00");
        OpPreSettleGroupResult second = group(
            "MD-1", "31.33", "0.6", "50.00", "10.00", "10.00", "30.00", "10.00");

        SharedMdtrtDeductiblePreviewAdjuster.adjust(List.of(first, second));

        assertMoney("0.00", second.getActPayDedc());
        assertMoney("28.80", second.getFundPaySumamt());
        assertMoney("0.00", second.getPsnCashPay());
        assertMoney("21.20", second.getAcctPay());
    }

    @Test
    void nullGroups_doNotThrow() {
        SharedMdtrtDeductiblePreviewAdjuster.adjust(null);
        SharedMdtrtDeductiblePreviewAdjuster.adjust(new ArrayList<>());
        OpPreSettleGroupResult only = new OpPreSettleGroupResult();
        SharedMdtrtDeductiblePreviewAdjuster.adjust(List.of(only));
        assertNull(only.getActPayDedc());
    }

    /**
     * 组装一组 2206 报价。比例传 null 表示平台未回统筹比例。
     *
     * @param mdtrtId 平台就诊号
     * @param dedc    起付线
     * @param rate    统筹比例；null 表示缺失
     * @param medfee  医疗费总额
     * @param fund    基金支付总额
     * @param hifp    基本统筹
     * @param acct    个账
     * @param cash    个人现金
     * @return 组预览
     */
    private static OpPreSettleGroupResult group(
        String mdtrtId,
        String dedc,
        String rate,
        String medfee,
        String fund,
        String hifp,
        String acct,
        String cash) {
        OpPreSettleGroupResult row = new OpPreSettleGroupResult();
        row.setMdtrtId(mdtrtId);
        row.setActPayDedc(new BigDecimal(dedc));
        // 缺比例的组必须保持 null，收口逻辑才会放弃改写
        if (rate != null) {
            row.setPoolPropSelfpay(new BigDecimal(rate));
        }
        row.setMedfeeSumamt(new BigDecimal(medfee));
        row.setFundPaySumamt(new BigDecimal(fund));
        row.setHifpPay(new BigDecimal(hifp));
        row.setAcctPay(new BigDecimal(acct));
        row.setPsnCashPay(new BigDecimal(cash));
        return row;
    }

    /**
     * 按数值比较金额，忽略标度差异。
     *
     * @param expected 期望金额
     * @param actual   实际金额
     */
    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
