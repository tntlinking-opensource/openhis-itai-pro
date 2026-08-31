/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial;

import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.share.enums.PaymentChannel;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 多笔 2207 现金对齐：杜绝 fin_type=Σmedfee、渠道少记 Σpsn_cash。
 */
class InsuranceSettlementCashAlignSupportTest {

    @Test
    void resolveTargetPatientCash_sumsYbCashAndSelfPayResidual() {
        // 两笔回执：0 + 127.63；收费合计=medfee → 自费段 0
        BigDecimal target = InsuranceSettlementCashAlignSupport.resolveTargetPatientCash(
            new BigDecimal("127.63"),
            new BigDecimal("396.88"),
            new BigDecimal("396.88"));
        assertEquals(new BigDecimal("127.63"), target);
    }

    @Test
    void resolveTargetPatientCash_threeSettlementsLiuQuanquan() {
        // 刘泉泉：三笔 2207 psn_cash 3.09+5.60+0，medfee 8.97+8.92+9.00，收费 26.89 → 现金 8.69
        BigDecimal target = InsuranceSettlementCashAlignSupport.resolveTargetPatientCash(
            new BigDecimal("8.69"),
            new BigDecimal("26.89"),
            new BigDecimal("26.89"));
        assertEquals(new BigDecimal("8.69"), target);
    }

    @Test
    void alignPatientPayLines_overridesStaleCashWithYbAuthority() {
        List<PaymentDetailItemCommand> lines = new ArrayList<>();
        PaymentDetailItemCommand fund = new PaymentDetailItemCommand();
        fund.setPayEnum(PaymentChannel.INSURANCE_FUND.getCode());
        fund.setAmount(new BigDecimal("255.88"));
        lines.add(fund);
        PaymentDetailItemCommand acct = new PaymentDetailItemCommand();
        acct.setPayEnum(PaymentChannel.INSURANCE_ACCT.getCode());
        acct.setAmount(new BigDecimal("13.37"));
        lines.add(acct);
        PaymentDetailItemCommand cash = new PaymentDetailItemCommand();
        cash.setPayEnum(PaymentChannel.CASH.getCode());
        cash.setAmount(new BigDecimal("126.11"));
        lines.add(cash);

        InsuranceSettlementCashAlignSupport.alignPatientPayLines(
            lines,
            new BigDecimal("127.63"),
            1L,
            "ACC",
            LocalDateTime.now(),
            "SUCCESS");

        BigDecimal patientSum = BigDecimal.ZERO;
        BigDecimal insuranceSum = BigDecimal.ZERO;
        for (PaymentDetailItemCommand line : lines) {
            if (InsuranceSettlementCashAlignSupport.isInsuranceChannel(line.getPayEnum())) {
                insuranceSum = insuranceSum.add(line.getAmount());
            } else {
                patientSum = patientSum.add(line.getAmount());
            }
        }
        assertEquals(new BigDecimal("269.25"), insuranceSum);
        assertEquals(new BigDecimal("127.63"), patientSum);
        assertEquals(new BigDecimal("396.88"), insuranceSum.add(patientSum));
    }

    @Test
    void alignPatientPayLines_overridesOverstatedWechatLikeDay11Case() {
        // 历史脏例 PMR-2026-08-110105：三笔基金 18.20 + 微信误写 14.31；权威现金应为 Σpsn_cash=8.69
        List<PaymentDetailItemCommand> lines = new ArrayList<>();
        PaymentDetailItemCommand fund1 = new PaymentDetailItemCommand();
        fund1.setPayEnum(PaymentChannel.INSURANCE_FUND.getCode());
        fund1.setAmount(new BigDecimal("1.81"));
        lines.add(fund1);
        PaymentDetailItemCommand fund2 = new PaymentDetailItemCommand();
        fund2.setPayEnum(PaymentChannel.INSURANCE_FUND.getCode());
        fund2.setAmount(new BigDecimal("5.62"));
        lines.add(fund2);
        PaymentDetailItemCommand fund3 = new PaymentDetailItemCommand();
        fund3.setPayEnum(PaymentChannel.INSURANCE_FUND.getCode());
        fund3.setAmount(new BigDecimal("10.77"));
        lines.add(fund3);
        PaymentDetailItemCommand wechat = new PaymentDetailItemCommand();
        wechat.setPayEnum(PaymentChannel.WECHAT.getCode());
        wechat.setAmount(new BigDecimal("14.31"));
        lines.add(wechat);

        BigDecimal target = InsuranceSettlementCashAlignSupport.resolveTargetPatientCash(
            new BigDecimal("8.69"),
            new BigDecimal("26.89"),
            new BigDecimal("26.89"));
        assertEquals(new BigDecimal("8.69"), target);

        InsuranceSettlementCashAlignSupport.alignPatientPayLines(
            lines,
            target,
            1L,
            "ACC",
            LocalDateTime.now(),
            "SUCCESS");

        assertEquals(new BigDecimal("8.69"), InsuranceSettlementCashAlignSupport.sumPatientLineAmounts(lines));
        assertEquals(new BigDecimal("26.89"), InsuranceSettlementCashAlignSupport.sumLineAmounts(lines));
        assertTrue(InsuranceSettlementCashAlignSupport.isDetailSumMatchedChargeTotal(
            lines, new BigDecimal("26.89")));
    }

    @Test
    void isDetailSumMatchedChargeTotal_rejectsOverstatedPatientCash() {
        List<PaymentDetailItemCommand> lines = new ArrayList<>();
        PaymentDetailItemCommand fund = new PaymentDetailItemCommand();
        fund.setPayEnum(PaymentChannel.INSURANCE_FUND.getCode());
        fund.setAmount(new BigDecimal("18.20"));
        lines.add(fund);
        PaymentDetailItemCommand wechat = new PaymentDetailItemCommand();
        wechat.setPayEnum(PaymentChannel.WECHAT.getCode());
        wechat.setAmount(new BigDecimal("14.31"));
        lines.add(wechat);
        assertFalse(InsuranceSettlementCashAlignSupport.isDetailSumMatchedChargeTotal(
            lines, new BigDecimal("26.89")));
    }

    @Test
    void isDetailSumMatchedChargeTotal_day8Style_missedSecondPsnCashFails() {
        List<PaymentDetailItemCommand> lines = new ArrayList<>();
        PaymentDetailItemCommand fund = new PaymentDetailItemCommand();
        fund.setPayEnum(PaymentChannel.INSURANCE_FUND.getCode());
        fund.setAmount(new BigDecimal("100.00"));
        lines.add(fund);
        PaymentDetailItemCommand cash = new PaymentDetailItemCommand();
        cash.setPayEnum(PaymentChannel.CASH.getCode());
        cash.setAmount(new BigDecimal("126.11"));
        lines.add(cash);
        assertFalse(InsuranceSettlementCashAlignSupport.isDetailSumMatchedChargeTotal(
            lines, new BigDecimal("235.91")));
        cash.setAmount(new BigDecimal("135.91"));
        assertTrue(InsuranceSettlementCashAlignSupport.isDetailSumMatchedChargeTotal(
            lines, new BigDecimal("235.91")));
    }

    @Test
    void isDetailSumMatchedChargeTotal_selfPayUnderRecordedAlipayFails() {
        // 历史脏例 PMR-2026-08-290043：纯自费 charge=1706，支付宝明细 1705.50
        List<PaymentDetailItemCommand> lines = new ArrayList<>();
        PaymentDetailItemCommand alipay = new PaymentDetailItemCommand();
        alipay.setPayEnum(PaymentChannel.ALIPAY.getCode());
        alipay.setAmount(new BigDecimal("1705.50"));
        lines.add(alipay);
        assertFalse(InsuranceSettlementCashAlignSupport.isDetailSumMatchedChargeTotal(
            lines, new BigDecimal("1706.00")));
        alipay.setAmount(new BigDecimal("1706.00"));
        assertTrue(InsuranceSettlementCashAlignSupport.isDetailSumMatchedChargeTotal(
            lines, new BigDecimal("1706.00")));
    }
}
