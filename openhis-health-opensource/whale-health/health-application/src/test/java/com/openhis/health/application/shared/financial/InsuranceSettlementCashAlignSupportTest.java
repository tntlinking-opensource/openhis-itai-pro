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
    void resolveTargetPatientCash_includesSelfPayWhenChargeExceedsMedfee() {
        BigDecimal target = InsuranceSettlementCashAlignSupport.resolveTargetPatientCash(
            new BigDecimal("10.00"),
            new BigDecimal("90.00"),
            new BigDecimal("140.00"));
        assertEquals(new BigDecimal("60.00"), target);
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
    void redistributeProportionally_lastRowAbsorbsRounding() {
        List<BigDecimal> parts = InsuranceSettlementCashAlignSupport.redistributeProportionally(
            List.of(new BigDecimal("60.00"), new BigDecimal("40.00")),
            new BigDecimal("101.00"));
        assertEquals(2, parts.size());
        assertEquals(new BigDecimal("101.00"), parts.get(0).add(parts.get(1)));
        assertTrue(parts.get(0).compareTo(BigDecimal.ZERO) > 0);
        assertTrue(parts.get(1).compareTo(BigDecimal.ZERO) > 0);
    }
}
