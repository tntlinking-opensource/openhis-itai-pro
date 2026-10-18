/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.chs.domain.settlement;

import com.zxplat.plugin.chs.domain.outpatient.entity.YbClinicPreSettle;
import com.zxplat.plugin.chs.domain.settlement.model.SettlementAmounts;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SettlementAmountValidatorTest {

    @Test
    void medfeeBalancedWithinTolerance() {
        SettlementAmounts amounts = SettlementAmounts.of(
            new BigDecimal("100.00"),
            new BigDecimal("70.00"),
            new BigDecimal("25.00"),
            new BigDecimal("5.00"),
            null, null, null, null);

        assertDoesNotThrow(amounts::assertMedfeeBalanced);
    }

    @Test
    void medfeeBalancedFailsWhenMismatch() {
        SettlementAmounts amounts = SettlementAmounts.of(
            new BigDecimal("100.00"),
            new BigDecimal("70.00"),
            new BigDecimal("20.00"),
            new BigDecimal("5.00"),
            null, null, null, null);

        assertThrows(SettlementInvariantException.class, amounts::assertMedfeeBalanced);
    }

    @Test
    void assert2207PassthroughFromPreSettle() {
        YbClinicPreSettle pre = new YbClinicPreSettle();
        pre.setFulamtOwnpayAmt(new BigDecimal("1.00"));
        pre.setOverlmtSelfpay(new BigDecimal("2.00"));
        pre.setPreselfpayAmt(new BigDecimal("3.00"));
        pre.setInscpScpAmt(new BigDecimal("4.00"));

        SettlementAmounts amounts = SettlementAmounts.of(
            null, null, null, null,
            new BigDecimal("1.00"),
            new BigDecimal("2.00"),
            new BigDecimal("3.00"),
            new BigDecimal("4.00"));

        assertDoesNotThrow(() -> amounts.assert2207PassthroughFrom(pre));
    }
}
