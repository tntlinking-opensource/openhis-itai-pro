/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.support;

import com.openhis.health.application.inpatient.financial.settle.support.dto.InpatientDepositSettlementPlan;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * 住院出院全结预交金轧差计划器（自费与医保同一公式）。
 *
 * <p>少补走冲抵+渠道补收；多退一律退现金（不判原收款渠道、不走银联全退重收）。</p>
 * <p>判定顺序：P=0 → R6/R7；D=0 → R5；D&lt;P → R1；D=P → R2；D&gt;P → R3。</p>
 */
@Component
public class InpatientDepositSettlementPlanner {

    /** 分支：预交金不够，冲抵 D + 渠道补 P−D。 */
    public static final String RULE_R1 = "R1";

    /** 分支：刚好够用，仅冲抵，禁止 0 元收/退主单。 */
    public static final String RULE_R2 = "R2";

    /** 分支：有余，冲抵 P + 现金退 D−P。 */
    public static final String RULE_R3 = "R3";

    /**
     * 历史分支码：含银联全退+重收（已废除；多退一律现金，不再产生本码）。
     *
     * @deprecated 保留常量仅防旧文案/日志比对
     */
    @Deprecated
    public static final String RULE_R4 = "R4";

    /** 分支：无预交金，仅渠道收 P。 */
    public static final String RULE_R5 = "R5";

    /** 分支：个人应付 0、有预交金，现金退清 D。 */
    public static final String RULE_R6 = "R6";

    /** 分支：个人应付 0 且无预交金。 */
    public static final String RULE_R7 = "R7";

    /**
     * 按个人现金应付 P、库内预交金 D 解析轧差计划（多退不判支付渠道）。
     *
     * @param personalCashPayable 个人现金应付 P（医保 psnCashPay / 自费本批应收）
     * @param depositBalance      结算前个人现金户余额 D
     * @return 非空轧差计划（金额均为结算 2 位刻度）
     */
    public InpatientDepositSettlementPlan resolve(
        BigDecimal personalCashPayable,
        BigDecimal depositBalance) {
        BigDecimal payable = MonetaryAmountSupport.scaleSettle(personalCashPayable);
        BigDecimal deposit = MonetaryAmountSupport.scaleSettle(depositBalance);
        if (payable.compareTo(BigDecimal.ZERO) < 0) {
            payable = BigDecimal.ZERO;
        }
        if (deposit.compareTo(BigDecimal.ZERO) < 0) {
            deposit = BigDecimal.ZERO;
        }
        // P=0：无可冲抵个人现金，有余则现金退清 D。
        if (payable.compareTo(BigDecimal.ZERO) == 0) {
            if (deposit.compareTo(BigDecimal.ZERO) > 0) {
                return new InpatientDepositSettlementPlan(
                    RULE_R6,
                    payable,
                    deposit,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    deposit,
                    true);
            }
            return new InpatientDepositSettlementPlan(
                RULE_R7,
                payable,
                deposit,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false);
        }
        // 无预交金：收银收满个人现金。
        if (deposit.compareTo(BigDecimal.ZERO) == 0) {
            return new InpatientDepositSettlementPlan(
                RULE_R5,
                payable,
                deposit,
                BigDecimal.ZERO,
                payable,
                BigDecimal.ZERO,
                false);
        }
        // 预交金不够：冲抵占用全部 D，再收差额（少补口径不变）。
        if (deposit.compareTo(payable) < 0) {
            BigDecimal channel = MonetaryAmountSupport.scaleSettle(payable.subtract(deposit));
            return new InpatientDepositSettlementPlan(
                RULE_R1,
                payable,
                deposit,
                deposit,
                channel,
                BigDecimal.ZERO,
                false);
        }
        // 刚好够用：仅冲抵，不收不退。
        if (deposit.compareTo(payable) == 0) {
            return new InpatientDepositSettlementPlan(
                RULE_R2,
                payable,
                deposit,
                payable,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                false);
        }
        // 有余：冲抵 P，多余 D−P 一律退现金（不判原收款渠道）。
        BigDecimal refund = MonetaryAmountSupport.scaleSettle(deposit.subtract(payable));
        return new InpatientDepositSettlementPlan(
            RULE_R3,
            payable,
            deposit,
            payable,
            BigDecimal.ZERO,
            refund,
            false);
    }

    /**
     * 兼容旧调用：忽略含银联入参，多退一律走现金口径。
     *
     * @param personalCashPayable 个人现金应付 P
     * @param depositBalance      库内预交金 D
     * @param hasUnionPayDeposit  已忽略
     * @return 轧差计划
     * @deprecated 改用 {@link #resolve(BigDecimal, BigDecimal)}
     */
    @Deprecated
    public InpatientDepositSettlementPlan resolve(
        BigDecimal personalCashPayable,
        BigDecimal depositBalance,
        boolean hasUnionPayDeposit) {
        return this.resolve(personalCashPayable, depositBalance);
    }
}
