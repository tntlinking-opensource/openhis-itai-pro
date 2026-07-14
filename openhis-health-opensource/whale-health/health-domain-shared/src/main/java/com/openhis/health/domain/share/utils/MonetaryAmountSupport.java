/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.utils;

import com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;

/**
 * 全局金钱计算与舍入工具（**BR-MON-001**；契约见 monetary-amount-contract.md）。
 *
 * <p><strong>行级</strong>：{@link #scaleCalc(BigDecimal)} / {@link #lineTotal(BigDecimal, BigDecimal)} 保持
 * {@link com.openhis.health.domain.share.constants.ChargeItemAmountScaleConstants#CHARGE_MONETARY_SCALE} 位。
 * <strong>单据/支付总价</strong>：{@link #sumThenSettle(java.util.Collection)} 先按 6 位代数加总，再
 * {@link #scaleSettle(BigDecimal)} 到结算刻度。禁止以 {@code sum(round(line, 2))} 作为支付或发票权威口径。</p>
 */
public final class MonetaryAmountSupport {

    /** 全系统金额舍入模式。 */
    public static final RoundingMode MONETARY_ROUNDING = RoundingMode.HALF_UP;

    private MonetaryAmountSupport() {
    }

    /**
     * {@code null} 安全转零（不改变刻度）。
     *
     * @param value 金额
     * @return 非 null 金额
     */
    public static BigDecimal nullToZero(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    /**
     * 计算/行级/API 出参刻度：6 位 HALF_UP。
     *
     * @param value 原始金额
     * @return 计算刻度金额
     */
    public static BigDecimal scaleCalc(BigDecimal value) {
        return nullToZero(value).setScale(ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE, MONETARY_ROUNDING);
    }

    /**
     * 结算/支付/发票总价刻度：2 位 HALF_UP。
     *
     * @param value 加总后的金额
     * @return 结算刻度金额
     */
    public static BigDecimal scaleSettle(BigDecimal value) {
        return nullToZero(value).setScale(ChargeItemAmountScaleConstants.CHARGE_MONETARY_SETTLE_SCALE, MONETARY_ROUNDING);
    }

    /**
     * 前端展示刻度（与结算刻度同为 2 位；语义上仅用于 format，不替代 {@link #scaleSettle(BigDecimal)} 作支付闸门）。
     *
     * @param value 原始金额
     * @return 展示刻度金额
     */
    public static BigDecimal scaleDisplay(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(
                ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE,
                MONETARY_ROUNDING);
        }
        return value.setScale(ChargeItemAmountScaleConstants.CHARGE_MONETARY_DISPLAY_SCALE, MONETARY_ROUNDING);
    }

    /**
     * 单价 × 数量 → 行金额（6 位 HALF_UP）。
     *
     * @param unitPrice 单价
     * @param quantity  数量
     * @return 行金额
     */
    public static BigDecimal lineTotal(BigDecimal unitPrice, BigDecimal quantity) {
        BigDecimal price = scaleCalc(unitPrice);
        BigDecimal qty = nullToZero(quantity);
        return price.multiply(qty).setScale(ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE, MONETARY_ROUNDING);
    }

    /**
     * 解析有效包装换算比；无效时回落 1（BR-INV-DTL-007）。
     *
     * @param packageUnitRatio 包装单位相对拆零倍数
     * @return 正倍数，至少为 1
     */
    public static BigDecimal resolvePositivePackageUnitRatio(BigDecimal packageUnitRatio) {
        if (packageUnitRatio != null && packageUnitRatio.compareTo(BigDecimal.ZERO) > 0) {
            return packageUnitRatio;
        }
        return BigDecimal.ONE;
    }

    /**
     * 行计价单位单价线性换算为每包装单位单价：{@code P_pkg = P_line × R_pkg / R_line}。
     *
     * @param priceOnLineUnit      行单价
     * @param lineRatioToSplit     行单位相对拆零倍数
     * @param packageRatioToSplit  包装单位相对拆零倍数
     * @return 每包装单位单价（6 位）；参数缺失时按行单价刻度返回
     */
    public static BigDecimal convertLineUnitPriceToPackageUnitPrice(
        BigDecimal priceOnLineUnit,
        BigDecimal lineRatioToSplit,
        BigDecimal packageRatioToSplit
    ) {
        if (priceOnLineUnit == null) {
            return null;
        }
        BigDecimal scaledLinePrice = scaleCalc(priceOnLineUnit);
        if (lineRatioToSplit == null || packageRatioToSplit == null) {
            return scaledLinePrice;
        }
        if (lineRatioToSplit.compareTo(BigDecimal.ZERO) <= 0
            || packageRatioToSplit.compareTo(BigDecimal.ZERO) <= 0) {
            return scaledLinePrice;
        }
        if (lineRatioToSplit.compareTo(packageRatioToSplit) == 0) {
            return scaledLinePrice;
        }
        BigDecimal converted = scaledLinePrice.multiply(packageRatioToSplit)
            .divide(lineRatioToSplit, ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE, MONETARY_ROUNDING);
        return scaleCalc(converted);
    }

    /**
     * 拆零最小单位数量 + 每包装单位价 → 行总额（满包×包装价 + 余量×拆零单价，BR-INV-DTL-007）。
     *
     * @param splitQuantity     拆零最小单位数量
     * @param packageUnitRatio  包装单位相对拆零倍数
     * @param packageUnitPrice  每包装单位单价
     * @return 行总额（6 位）；数量或单价缺失时 null
     */
    public static BigDecimal computeSplitQuantityLineTotal(
        BigDecimal splitQuantity,
        BigDecimal packageUnitRatio,
        BigDecimal packageUnitPrice
    ) {
        if (splitQuantity == null || packageUnitPrice == null) {
            return null;
        }
        BigDecimal ratio = resolvePositivePackageUnitRatio(packageUnitRatio);
        if (ratio.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        BigDecimal[] parts = splitQuantity.divideAndRemainder(ratio);
        BigDecimal wholePackages = parts[0];
        BigDecimal remainderSplits = parts[1];
        BigDecimal scaledPackagePrice = scaleCalc(packageUnitPrice);
        BigDecimal perSplit = scaledPackagePrice.divide(
            ratio,
            ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE,
            MONETARY_ROUNDING
        );
        BigDecimal total = wholePackages.multiply(scaledPackagePrice).add(remainderSplits.multiply(perSplit));
        return scaleCalc(total);
    }

    /**
     * 多个行级金额代数加总（中间保持原精度），再加总结果 settle 到 2 位。
     *
     * @param amounts 行级金额集合；{@code null} 元素忽略
     * @return 结算刻度合计
     */
    public static BigDecimal sumThenSettle(Collection<BigDecimal> amounts) {
        BigDecimal sum = BigDecimal.ZERO;
        if (amounts != null) {
            for (BigDecimal amount : amounts) {
                if (amount != null) {
                    sum = sum.add(amount);
                }
            }
        }
        return scaleSettle(sum);
    }

    /**
     * 多个行级金额代数加总（中间保持原精度），结果保持 6 位（用于行块小计等非支付场景）。
     *
     * @param amounts 行级金额集合
     * @return 计算刻度合计
     */
    public static BigDecimal sumThenCalc(Collection<BigDecimal> amounts) {
        BigDecimal sum = BigDecimal.ZERO;
        if (amounts != null) {
            for (BigDecimal amount : amounts) {
                if (amount != null) {
                    sum = sum.add(amount);
                }
            }
        }
        return scaleCalc(sum);
    }

    /**
     * 按比例分摊：{@code numerator × multiplier ÷ denominator}，商保留计算刻度 6 位（医保/实收占比等）。
     *
     * @param numerator   被分摊量（如行绝对金额）
     * @param multiplier  分摊池（如医保合计或净实收）
     * @param denominator 分母（如 payment 快照行金额合计）
     * @return 分摊结果（6 位 HALF_UP）；分母为 0 时返回 {@link #scaleCalc(BigDecimal) scaleCalc(0)}
     */
    public static BigDecimal proportionAtCalcScale(
        BigDecimal numerator,
        BigDecimal multiplier,
        BigDecimal denominator) {
        BigDecimal den = nullToZero(denominator);
        if (den.compareTo(BigDecimal.ZERO) <= 0) {
            return scaleCalc(BigDecimal.ZERO);
        }
        return nullToZero(numerator)
            .multiply(nullToZero(multiplier))
            .divide(den, ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE, MONETARY_ROUNDING);
    }

    /**
     * 在结算刻度（2 位）下比较两金额是否相等（支付明细合计校验等）。
     *
     * @param left  左操作数
     * @param right 右操作数
     * @return {@code true} 表示结算刻度相等
     */
    public static boolean equalsAtSettleScale(BigDecimal left, BigDecimal right) {
        return scaleSettle(left).compareTo(scaleSettle(right)) == 0;
    }

    /**
     * 行金额反推加权单价：{@code lineTotal ÷ quantity}，商保留计算刻度 6 位（发药/收费行合并场景）。
     *
     * @param lineTotal 行金额或合计金额
     * @param quantity  数量
     * @return 单价（6 位 HALF_UP）；数量为 0 时返回 {@link #scaleCalc(BigDecimal) scaleCalc(0)}
     */
    public static BigDecimal unitPriceFromLineTotal(BigDecimal lineTotal, BigDecimal quantity) {
        BigDecimal qty = nullToZero(quantity);
        if (qty.compareTo(BigDecimal.ZERO) <= 0) {
            return scaleCalc(BigDecimal.ZERO);
        }
        return nullToZero(lineTotal)
            .divide(qty, ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE, MONETARY_ROUNDING);
    }

    /**
     * 带符号金额：先归一到计算刻度，再乘符号，结果仍为计算刻度（6 位）。
     *
     * @param absoluteValue  绝对值
     * @param signMultiplier 1 或 -1
     * @return 带符号行级金额
     */
    public static BigDecimal applySignAtCalcScale(BigDecimal absoluteValue, BigDecimal signMultiplier) {
        BigDecimal scaled = scaleCalc(absoluteValue);
        BigDecimal sign = nullToZero(signMultiplier);
        return scaled.multiply(sign).setScale(ChargeItemAmountScaleConstants.CHARGE_MONETARY_SCALE, MONETARY_ROUNDING);
    }
}
