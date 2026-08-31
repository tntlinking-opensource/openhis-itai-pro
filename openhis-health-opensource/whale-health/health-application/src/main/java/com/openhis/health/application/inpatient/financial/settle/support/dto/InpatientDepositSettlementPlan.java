/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.inpatient.financial.settle.support.dto;

import java.math.BigDecimal;

/**
 * 住院出院全结预交金轧差计划（P/O/C 与应退）。
 *
 * @param ruleCode             分支码 R1～R7（R4 已废除，多退不再产生）
 * @param personalCashPayable  个人现金应付 P（结算 payment 主单金额）
 * @param depositBalance       结算前库内预交金 D
 * @param depositOffsetAmount  冲抵额 O
 * @param channelPayable       渠道实付 C（收银员须收）
 * @param prepayRefundAmount   应退预交金（R3=D−P；R6=D；其余 0；落账渠道恒为现金）
 * @param prepayFullRefund     是否退清全部 D（仅 R6）
 */
public record InpatientDepositSettlementPlan(
    String ruleCode,
    BigDecimal personalCashPayable,
    BigDecimal depositBalance,
    BigDecimal depositOffsetAmount,
    BigDecimal channelPayable,
    BigDecimal prepayRefundAmount,
    boolean prepayFullRefund
) {
}
