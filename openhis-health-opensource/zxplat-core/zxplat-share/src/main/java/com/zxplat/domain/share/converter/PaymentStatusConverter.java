/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.converter;

import com.zxplat.domain.share.enums.PaymentStatus;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
/**
 * PaymentStatusConverter。
 */
public class PaymentStatusConverter extends AbstractEnumCodeConverter<PaymentStatus> {
    public PaymentStatusConverter() {
        /**
         * super。
         */
        super(PaymentStatus.class);
    }
}
