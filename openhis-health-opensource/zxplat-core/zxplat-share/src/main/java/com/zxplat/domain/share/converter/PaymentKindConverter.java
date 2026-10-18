/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.converter;

import com.zxplat.domain.share.enums.PaymentKind;
import com.zxplat.domain.share.utils.EnumUtil;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
/**
 * PaymentKindConverter。
 */
public class PaymentKindConverter extends AbstractEnumCodeConverter<PaymentKind> {
    public PaymentKindConverter() {
        super(PaymentKind.class);
    }

    /**
     * 历史落库曾使用 {@code PRE_DEPOSIT}，与 {@link PaymentKind#HOSPITAL_DEPOSIT} 同义。
     */
    @Override
    public PaymentKind convertToEntityAttribute(String dbData) {
        if ("PRE_DEPOSIT".equals(dbData)) {
            return PaymentKind.HOSPITAL_DEPOSIT;
        }
        return EnumUtil.fromCode(PaymentKind.class, dbData);
    }
}
