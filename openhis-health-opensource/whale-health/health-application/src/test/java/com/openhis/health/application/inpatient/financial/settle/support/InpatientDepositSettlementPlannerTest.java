/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.support;

import com.openhis.health.application.inpatient.financial.settle.support.dto.InpatientDepositSettlementPlan;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 住院预交金轧差计划器单测（少补不变；多退一律现金、不判渠道）。
 */
class InpatientDepositSettlementPlannerTest {

    private final InpatientDepositSettlementPlanner planner = new InpatientDepositSettlementPlanner();

    @Test
    void resolve_r1_depositLessThanPayable() {
        InpatientDepositSettlementPlan plan = planner.resolve(
            new BigDecimal("1187.08"),
            new BigDecimal("1000.00"));
        assertEquals(InpatientDepositSettlementPlanner.RULE_R1, plan.ruleCode());
        assertEquals(new BigDecimal("1000.00"), plan.depositOffsetAmount());
        assertEquals(new BigDecimal("187.08"), plan.channelPayable());
        assertEquals(0, plan.prepayRefundAmount().compareTo(BigDecimal.ZERO));
    }

    @Test
    void resolve_r2_exactMatch() {
        InpatientDepositSettlementPlan plan = planner.resolve(
            new BigDecimal("1000.00"),
            new BigDecimal("1000.00"));
        assertEquals(InpatientDepositSettlementPlanner.RULE_R2, plan.ruleCode());
        assertEquals(new BigDecimal("1000.00"), plan.depositOffsetAmount());
        assertEquals(0, plan.channelPayable().compareTo(BigDecimal.ZERO));
    }

    @Test
    void resolve_r3_surplusCashRefund_ignoresUnionPayFlag() {
        // 即便传入含银联，多退仍冲抵 P + 现金退 D−P（不走全退重收）
        InpatientDepositSettlementPlan plan = planner.resolve(
            new BigDecimal("20.00"),
            new BigDecimal("700.00"),
            true);
        assertEquals(InpatientDepositSettlementPlanner.RULE_R3, plan.ruleCode());
        assertEquals(new BigDecimal("20.00"), plan.depositOffsetAmount());
        assertEquals(0, plan.channelPayable().compareTo(BigDecimal.ZERO));
        assertEquals(new BigDecimal("680.00"), plan.prepayRefundAmount());
        assertFalse(plan.prepayFullRefund());
    }

    @Test
    void resolve_r6_zeroPayableCashRefundDeposit() {
        InpatientDepositSettlementPlan plan = planner.resolve(
            BigDecimal.ZERO,
            new BigDecimal("700.00"));
        assertEquals(InpatientDepositSettlementPlanner.RULE_R6, plan.ruleCode());
        assertEquals(new BigDecimal("700.00"), plan.prepayRefundAmount());
        assertEquals(0, plan.channelPayable().compareTo(BigDecimal.ZERO));
        assertTrue(plan.prepayFullRefund());
    }
}
