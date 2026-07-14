/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.payment;

import com.openhis.health.domain.share.enums.PaymentChannel;
import com.openhis.whale.core.application.exception.BusinessException;

/**
 * BPC {@code pay_type} → HIS {@code fin_payment_rec_detail.pay_enum}（ADR-PAY-001 §4.3）。
 */
public final class BpcPayTypeMapper {

    private BpcPayTypeMapper() {
    }

    /**
     * 将被扫 BPC 原值映射为持久化语义码。
     *
     * @param bpcPayType BPC 响应 {@code pay_type}（WEIX/ZFBA/UPAY）
     * @return {@code pay_enum} 语义码
     */
    public static String toPayEnum(String bpcPayType) {
        if (bpcPayType == null || bpcPayType.isBlank()) {
            throw new BusinessException("BPC_PAY_TYPE_INVALID", "BPC pay_type 不能为空");
        }
        return switch (bpcPayType.trim().toUpperCase()) {
            case "WEIX" -> PaymentChannel.WECHAT.getCode();
            case "ZFBA" -> PaymentChannel.ALIPAY.getCode();
            case "UPAY" -> PaymentChannel.BANK_CARD.getCode();
            default -> throw new BusinessException("BPC_PAY_TYPE_UNSUPPORTED", "不支持的 BPC pay_type: " + bpcPayType);
        };
    }
}
