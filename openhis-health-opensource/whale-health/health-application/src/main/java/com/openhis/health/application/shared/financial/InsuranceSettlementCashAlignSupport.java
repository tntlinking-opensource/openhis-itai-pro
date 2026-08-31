/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.shared.financial;

import com.openhis.health.domain.financial.paymentreconciliation.command.PaymentDetailItemCommand;
import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.health.domain.share.utils.MonetaryAmountSupport;
import com.openhis.whale.core.application.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 医保正式结算后，将患者自付渠道金额与头表现金对齐到 2207 权威口径。
 *
 * <p>基金/个账已按回执逐笔写入；现金若仍信前端/2206 草稿，多批次 2207 时会出现
 * 「fin_type=Σmedfee、渠道少/多记 Σpsn_cash」勾稽差。本工具以 {@code Σ psn_cash_pay + 自费原价}
 * 覆盖患者自付渠道行，并提供明细合计勾稽（**BR-OPC-024**），供门诊收费/挂号落账共用。</p>
 */
public final class InsuranceSettlementCashAlignSupport {

    private InsuranceSettlementCashAlignSupport() {
    }

    /**
     * 计算落账权威个人现金：Σ 2207 {@code psn_cash_pay} + max(0, 收费项合计 − Σ medfee)。
     *
     * @param sumYbPsnCashPay 各回执个人现金代数和
     * @param sumYbMedfee     各回执医疗费总额代数和
     * @param chargeItemTotal 本次结算收费项 totalPrice 合计（含自费段）
     * @return 结算刻度个人现金应付
     */
    public static BigDecimal resolveTargetPatientCash(
        BigDecimal sumYbPsnCashPay,
        BigDecimal sumYbMedfee,
        BigDecimal chargeItemTotal) {
        BigDecimal ybCash = nullToZero(sumYbPsnCashPay);
        BigDecimal ybMed = nullToZero(sumYbMedfee);
        BigDecimal charge = nullToZero(chargeItemTotal);
        BigDecimal selfPay = charge.subtract(ybMed);
        if (selfPay.compareTo(BigDecimal.ZERO) < 0) {
            selfPay = BigDecimal.ZERO;
        }
        return MonetaryAmountSupport.scaleSettle(ybCash.add(selfPay));
    }

    /**
     * 将 {@code allLines} 中非医保渠道行合计调整为 {@code targetPatientCash}；医保行原样保留。
     *
     * <p>渠道码优先保留前端已选（微信/现金/扫码等）；无患者自付行且目标&gt;0 时补一条现金行。</p>
     *
     * @param allLines            已含基金/个账 + 前端自付的完整明细（原地替换内容）
     * @param targetPatientCash   权威个人现金
     * @param fallbackAccountId   补行时账户 id
     * @param fallbackAccountCode 补行时账户 busNo
     * @param billDate            交易时间
     * @param outcomeEnum         结果状态 code
     */
    public static void alignPatientPayLines(
        List<PaymentDetailItemCommand> allLines,
        BigDecimal targetPatientCash,
        Long fallbackAccountId,
        String fallbackAccountCode,
        LocalDateTime billDate,
        String outcomeEnum) {
        if (allLines == null) {
            return;
        }
        BigDecimal target = MonetaryAmountSupport.scaleSettle(nullToZero(targetPatientCash));
        List<PaymentDetailItemCommand> insuranceLines = new ArrayList<>();
        List<PaymentDetailItemCommand> patientLines = new ArrayList<>();
        for (PaymentDetailItemCommand line : allLines) {
            if (line == null) {
                continue;
            }
            if (isInsuranceChannel(line.getPayEnum())) {
                insuranceLines.add(line);
            } else {
                patientLines.add(line);
            }
        }
        List<PaymentDetailItemCommand> alignedPatient =
            alignPatientOnly(patientLines, target, fallbackAccountId, fallbackAccountCode, billDate, outcomeEnum);
        allLines.clear();
        allLines.addAll(insuranceLines);
        allLines.addAll(alignedPatient);
    }

    /**
     * 多账户草稿分桶金额与权威现金不一致时，按比例重分到各桶（末行吃舍入）。
     *
     * @param draftAmounts 草稿各行金额（与账户一一对应）
     * @param targetTotal  权威现金合计
     * @return 与 draftAmounts 等长的重分结果
     */
    public static List<BigDecimal> redistributeProportionally(
        List<BigDecimal> draftAmounts,
        BigDecimal targetTotal) {
        List<BigDecimal> out = new ArrayList<>();
        if (draftAmounts == null || draftAmounts.isEmpty()) {
            return out;
        }
        BigDecimal target = MonetaryAmountSupport.scaleSettle(nullToZero(targetTotal));
        BigDecimal draftSum = BigDecimal.ZERO;
        for (BigDecimal amount : draftAmounts) {
            draftSum = draftSum.add(nullToZero(amount));
        }
        if (draftSum.compareTo(BigDecimal.ZERO) <= 0) {
            // 草稿全 0：全部落到末行
            for (int index = 0; index < draftAmounts.size(); index++) {
                if (index == draftAmounts.size() - 1) {
                    out.add(target);
                } else {
                    out.add(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
                }
            }
            return out;
        }
        if (draftSum.compareTo(target) == 0) {
            for (BigDecimal amount : draftAmounts) {
                out.add(MonetaryAmountSupport.scaleSettle(nullToZero(amount)));
            }
            return out;
        }
        BigDecimal allocated = BigDecimal.ZERO;
        for (int index = 0; index < draftAmounts.size(); index++) {
            if (index == draftAmounts.size() - 1) {
                out.add(MonetaryAmountSupport.scaleSettle(target.subtract(allocated)));
                continue;
            }
            BigDecimal part = nullToZero(draftAmounts.get(index))
                .multiply(target)
                .divide(draftSum, 2, RoundingMode.HALF_UP);
            part = MonetaryAmountSupport.scaleSettle(part);
            allocated = allocated.add(part);
            out.add(part);
        }
        return out;
    }

    /**
     * 是否医保基金/个账渠道（服务端写入，不参与患者自付对齐）。
     *
     * @param payEnum {@code pay_enum}
     * @return true=医保分项
     */
    public static boolean isInsuranceChannel(String payEnum) {
        if (StringUtils.isEmpty(payEnum)) {
            return false;
        }
        String code = payEnum.trim();
        return PaymentChannel.INSURANCE_FUND.getCode().equals(code)
            || PaymentChannel.INSURANCE_ACCT.getCode().equals(code);
    }

    /**
     * 明细行金额代数和（结算 2 位刻度）；空列表为 0。
     *
     * <p>供落账前勾稽：基金+个账+自付渠道合计须等于本次收费项合计，防止自付多记/少记穿透成功态。</p>
     *
     * @param allLines 已对齐后的完整明细
     * @return 结算刻度合计
     */
    public static BigDecimal sumLineAmounts(List<PaymentDetailItemCommand> allLines) {
        BigDecimal total = BigDecimal.ZERO;
        if (allLines == null || allLines.isEmpty()) {
            return MonetaryAmountSupport.scaleSettle(total);
        }
        for (PaymentDetailItemCommand line : allLines) {
            if (line == null) {
                continue;
            }
            total = total.add(nullToZero(line.getAmount()));
        }
        return MonetaryAmountSupport.scaleSettle(total);
    }

    /**
     * 患者自付渠道（非医保基金/个账）金额代数和。
     *
     * @param allLines 已对齐后的完整明细
     * @return 结算刻度自付合计
     */
    public static BigDecimal sumPatientLineAmounts(List<PaymentDetailItemCommand> allLines) {
        BigDecimal total = BigDecimal.ZERO;
        if (allLines == null || allLines.isEmpty()) {
            return MonetaryAmountSupport.scaleSettle(total);
        }
        for (PaymentDetailItemCommand line : allLines) {
            if (line == null || isInsuranceChannel(line.getPayEnum())) {
                continue;
            }
            total = total.add(nullToZero(line.getAmount()));
        }
        return MonetaryAmountSupport.scaleSettle(total);
    }

    /**
     * 判断明细合计是否与收费项合计一致（均为结算 2 位）。
     *
     * @param allLines        已对齐明细
     * @param chargeItemTotal 本次收费项 totalPrice 合计
     * @return true=勾稽通过
     */
    public static boolean isDetailSumMatchedChargeTotal(
        List<PaymentDetailItemCommand> allLines,
        BigDecimal chargeItemTotal) {
        BigDecimal detailSum = sumLineAmounts(allLines);
        BigDecimal chargeSum = MonetaryAmountSupport.scaleSettle(nullToZero(chargeItemTotal));
        return detailSum.compareTo(chargeSum) == 0;
    }

    private static List<PaymentDetailItemCommand> alignPatientOnly(
        List<PaymentDetailItemCommand> patientLines,
        BigDecimal target,
        Long fallbackAccountId,
        String fallbackAccountCode,
        LocalDateTime billDate,
        String outcomeEnum) {
        List<PaymentDetailItemCommand> aligned = new ArrayList<>();
        if (target.compareTo(BigDecimal.ZERO) <= 0) {
            return aligned;
        }
        if (patientLines == null || patientLines.isEmpty()) {
            PaymentDetailItemCommand cashLine = new PaymentDetailItemCommand();
            cashLine.setPayEnum(PaymentChannel.CASH.getCode());
            cashLine.setAccountId(fallbackAccountId);
            cashLine.setAccountCode(fallbackAccountCode);
            cashLine.setAmount(target);
            cashLine.setChargeAmount(target);
            cashLine.setPayTransDate(billDate);
            cashLine.setResultEnum(outcomeEnum);
            aligned.add(cashLine);
            return aligned;
        }
        if (patientLines.size() == 1) {
            PaymentDetailItemCommand line = patientLines.get(0);
            line.setAmount(target);
            line.setChargeAmount(target);
            aligned.add(line);
            return aligned;
        }
        List<BigDecimal> draftAmounts = new ArrayList<>();
        for (PaymentDetailItemCommand line : patientLines) {
            draftAmounts.add(line.getAmount());
        }
        List<BigDecimal> parts = redistributeProportionally(draftAmounts, target);
        for (int index = 0; index < patientLines.size(); index++) {
            PaymentDetailItemCommand line = patientLines.get(index);
            BigDecimal part = parts.get(index);
            line.setAmount(part);
            line.setChargeAmount(part);
            aligned.add(line);
        }
        return aligned;
    }

    private static BigDecimal nullToZero(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value;
    }
}
