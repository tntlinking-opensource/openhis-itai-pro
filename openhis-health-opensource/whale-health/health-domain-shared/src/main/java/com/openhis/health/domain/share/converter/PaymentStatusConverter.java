/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.converter;

import com.openhis.health.domain.share.enums.PaymentStatus;
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
