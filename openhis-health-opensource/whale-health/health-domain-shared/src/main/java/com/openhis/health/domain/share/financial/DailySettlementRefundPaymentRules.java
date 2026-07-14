/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.financial;

import com.openhis.health.domain.share.enums.PaymentStatus;
import com.openhis.health.domain.share.enums.PaymentType;
import com.openhis.whale.core.application.util.StringUtils;

/**
 * 门/住院日结与门诊费用明细共用的逆向退费 payment 判定（纯函数，无 I/O）。
 *
 * <p>口径对齐 <strong>BR-OP-FDT-012</strong>（{@code status=REFUNDED} 金额取负）与
 * <strong>BR-OP-DCL-011</strong>（逆向 {@code UN_PAY}/{@code relation_id} 成对抵净）。
 * 正向 {@code SUCCESS} 原单保持正号；仅逆向/已退款终态取负。</p>
 */
public final class DailySettlementRefundPaymentRules {

    private DailySettlementRefundPaymentRules() {
    }

    /**
     * 是否须在汇总中取负号的逆向退费 payment。
     *
     * @param paymentEnum 持久化 {@code payment_enum}；可空
     * @param relationId  关联原支付主键；非空表示逆向单
     * @param statusEnum  支付状态
     * @return {@code true} 表示渠道/fin_type/netAmount 汇总须取负
     */
    public static boolean isRefundPaymentForSettlement(
        String paymentEnum,
        Long relationId,
        PaymentStatus statusEnum) {
        if (relationId != null) {
            return true;
        }
        if (StringUtils.isNotEmpty(paymentEnum)
            && PaymentType.UN_PAY.getCode().equals(paymentEnum.trim())) {
            return true;
        }
        // 与门诊费用明细 refundPayment 判定一致：REFUNDED 终态链路金额取负
        return PaymentStatus.REFUNDED.equals(statusEnum);
    }

    /**
     * 预览行 status 为 code 时的逆向退费判定。
     *
     * @param paymentEnum 持久化 {@code payment_enum}
     * @param relationId  关联原支付主键
     * @param statusCode  {@link PaymentStatus#getCode()} 持久化码
     * @return 是否须在汇总中取负
     */
    public static boolean isRefundPaymentForSettlement(
        String paymentEnum,
        Long relationId,
        String statusCode) {
        PaymentStatus statusEnum = null;
        if (StringUtils.isNotEmpty(statusCode)) {
            for (PaymentStatus candidate : PaymentStatus.values()) {
                if (candidate.getCode().equals(statusCode.trim())) {
                    statusEnum = candidate;
                    break;
                }
            }
        }
        return isRefundPaymentForSettlement(paymentEnum, relationId, statusEnum);
    }
}
