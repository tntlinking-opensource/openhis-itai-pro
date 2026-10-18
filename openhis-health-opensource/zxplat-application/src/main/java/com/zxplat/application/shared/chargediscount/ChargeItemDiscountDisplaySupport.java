/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.shared.chargediscount;

import com.zxplat.domain.administration.chargeitem.entity.ChargeItem;
import com.zxplat.domain.share.utils.MonetaryAmountSupport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * 收费行优惠差额展示计算（报表/日结读侧共用）。
 *
 * <p>优惠 = {@code |total_price| − |discount_amount|}；未落折后应收则差额为 0。
 * 配置本身即开关：未命中折扣配置的行两值相同，不产生优惠。</p>
 */
@Component
public class ChargeItemDiscountDisplaySupport {

    /**
     * 单行非负优惠差额（日结上半区扣减用绝对值；符号由调用方按收退处理）。
     *
     * @param chargeItem 收费明细；可空
     * @return 非负优惠差额；空行或未打折为 0
     */
    public BigDecimal resolveUnsignedGap(ChargeItem chargeItem) {
        if (chargeItem == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal originalAbs = MonetaryAmountSupport.nullToZero(chargeItem.getTotalPrice()).abs();
        BigDecimal receivableAbs = originalAbs;
        if (chargeItem.getDiscountAmount() != null) {
            // 折后应收已落库：未配折扣时与售价相同
            receivableAbs = chargeItem.getDiscountAmount().abs();
        }
        BigDecimal gap = originalAbs.subtract(receivableAbs);
        if (gap.compareTo(BigDecimal.ZERO) > 0) {
            return gap;
        }
        return BigDecimal.ZERO;
    }

    /**
     * 带收退符号的优惠差额：与售价同号（退费优惠为负）。
     *
     * @param originalSigned   售价（已含负号）
     * @param discountedSigned 折后应收（已含负号）；空则视为等于售价
     * @return 带符号优惠；未打折为 0
     */
    public BigDecimal resolveSignedGap(BigDecimal originalSigned, BigDecimal discountedSigned) {
        BigDecimal original = MonetaryAmountSupport.nullToZero(originalSigned);
        BigDecimal discounted = original;
        if (discountedSigned != null) {
            discounted = discountedSigned;
        }
        BigDecimal gapAbs = original.abs().subtract(discounted.abs());
        if (gapAbs.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        if (original.signum() < 0) {
            return gapAbs.negate();
        }
        return gapAbs;
    }

    /**
     * 带收退符号的实收（折后应收）。未落折后则等于售价。
     *
     * @param originalSigned   售价（已含负号）
     * @param discountedSigned 折后应收（已含负号）；空则视为等于售价
     * @return 带符号实收
     */
    public BigDecimal resolveSignedReceivable(BigDecimal originalSigned, BigDecimal discountedSigned) {
        if (discountedSigned != null) {
            return discountedSigned;
        }
        return MonetaryAmountSupport.nullToZero(originalSigned);
    }

    /**
     * 单行带收退符号的实收（折后应收）。
     *
     * @param chargeItem 收费明细；可空
     * @return 实收；空行为 0
     */
    public BigDecimal resolveSignedReceivable(ChargeItem chargeItem) {
        if (chargeItem == null) {
            return BigDecimal.ZERO;
        }
        return this.resolveSignedReceivable(chargeItem.getTotalPrice(), chargeItem.getDiscountAmount());
    }

    /**
     * 匹配收费行列表的带符号实收合计。
     *
     * @param matched 匹配收费明细
     * @return 实收合计（未缩放）
     */
    public BigDecimal sumSignedReceivables(List<ChargeItem> matched) {
        BigDecimal total = BigDecimal.ZERO;
        if (matched == null || matched.isEmpty()) {
            return total;
        }
        for (ChargeItem chargeItem : matched) {
            total = total.add(this.resolveSignedReceivable(chargeItem));
        }
        return total;
    }

    /**
     * 匹配收费行列表的非负优惠合计（行内未打折为 0）。
     *
     * @param matched 匹配收费明细
     * @return 优惠合计（未缩放）
     */
    public BigDecimal sumUnsignedGaps(List<ChargeItem> matched) {
        BigDecimal total = BigDecimal.ZERO;
        if (matched == null || matched.isEmpty()) {
            return total;
        }
        for (ChargeItem chargeItem : matched) {
            total = total.add(this.resolveUnsignedGap(chargeItem));
        }
        return total;
    }
}
