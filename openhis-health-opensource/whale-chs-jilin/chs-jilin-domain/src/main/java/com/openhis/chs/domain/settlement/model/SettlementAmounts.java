/*
 * Copyright © 2026 OpenHIS Contributors
 */

package com.openhis.chs.domain.settlement.model;

import com.openhis.chs.domain.ChsErrorCodes;
import com.openhis.chs.domain.settlement.PreSettleSnapshot;
import com.openhis.chs.domain.settlement.SettlementAmountValidator;
import com.openhis.chs.domain.settlement.SettlementInvariantException;

import java.math.BigDecimal;
import java.util.Objects;

/** 2206/2207 setlinfo 核心金额值对象。 */
public record SettlementAmounts(
    BigDecimal medfeeSumamt,
    BigDecimal fundPaySumamt,
    BigDecimal psnPartAmt,
    BigDecimal hospPartAmt,
    BigDecimal fulamtOwnpayAmt,
    BigDecimal overlmtSelfpay,
    BigDecimal preselfpayAmt,
    BigDecimal inscpScpAmt) {

    /**
     * 校验规范 §2206 金额恒等式。
     */
    public void assertMedfeeBalanced() {
        if (!SettlementAmountValidator.isMedfeeBalanced(
            medfeeSumamt, fundPaySumamt, psnPartAmt, hospPartAmt)) {
            throw new SettlementInvariantException(
                ChsErrorCodes.SETTLEMENT_INVARIANT,
                "金额恒等式校验失败：medfee_sumamt ≈ fund_pay_sumamt + psn_part_amt + hosp_part_amt");
        }
    }

    /**
     * 2207 须透传 2206 四金额字段（规范 §2206 重点说明）。
     */
    public void assert2207PassthroughFrom(PreSettleSnapshot pre) {
        if (pre == null) {
            return;
        }
        assertAmountEqual("fulamt_ownpay_amt", pre.getFulamtOwnpayAmt(), fulamtOwnpayAmt);
        assertAmountEqual("overlmt_selfpay", pre.getOverlmtSelfpay(), overlmtSelfpay);
        assertAmountEqual("preselfpay_amt", pre.getPreselfpayAmt(), preselfpayAmt);
        assertAmountEqual("inscp_scp_amt", pre.getInscpScpAmt(), inscpScpAmt);
    }

    private static void assertAmountEqual(String field, BigDecimal expected, BigDecimal actual) {
        if (expected == null && actual == null) {
            return;
        }
        BigDecimal left = BigDecimal.ZERO;
        if (expected != null) {
            left = expected;
        }
        BigDecimal right = BigDecimal.ZERO;
        if (actual != null) {
            right = actual;
        }
        if (left.subtract(right).abs().compareTo(SettlementAmountValidator.TOLERANCE) > 0) {
            throw new SettlementInvariantException(
                ChsErrorCodes.SETTLEMENT_INVARIANT,
                "2207 透传金额与预结算不一致：" + field);
        }
    }

    public static SettlementAmounts of(
        BigDecimal medfeeSumamt,
        BigDecimal fundPaySumamt,
        BigDecimal psnPartAmt,
        BigDecimal hospPartAmt,
        BigDecimal fulamtOwnpayAmt,
        BigDecimal overlmtSelfpay,
        BigDecimal preselfpayAmt,
        BigDecimal inscpScpAmt) {
        return new SettlementAmounts(
            medfeeSumamt,
            fundPaySumamt,
            psnPartAmt,
            hospPartAmt,
            fulamtOwnpayAmt,
            overlmtSelfpay,
            preselfpayAmt,
            inscpScpAmt);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SettlementAmounts that)) {
            return false;
        }
        return amountEquals(medfeeSumamt, that.medfeeSumamt)
            && amountEquals(fundPaySumamt, that.fundPaySumamt)
            && amountEquals(psnPartAmt, that.psnPartAmt)
            && amountEquals(hospPartAmt, that.hospPartAmt)
            && amountEquals(fulamtOwnpayAmt, that.fulamtOwnpayAmt)
            && amountEquals(overlmtSelfpay, that.overlmtSelfpay)
            && amountEquals(preselfpayAmt, that.preselfpayAmt)
            && amountEquals(inscpScpAmt, that.inscpScpAmt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
            normalize(medfeeSumamt),
            normalize(fundPaySumamt),
            normalize(psnPartAmt),
            normalize(hospPartAmt),
            normalize(fulamtOwnpayAmt),
            normalize(overlmtSelfpay),
            normalize(preselfpayAmt),
            normalize(inscpScpAmt));
    }

    private static boolean amountEquals(BigDecimal left, BigDecimal right) {
        return normalize(left).compareTo(normalize(right)) == 0;
    }

    private static BigDecimal normalize(BigDecimal value) {
        if (value != null) {
            return value;
        }
        return BigDecimal.ZERO;
    }
}
