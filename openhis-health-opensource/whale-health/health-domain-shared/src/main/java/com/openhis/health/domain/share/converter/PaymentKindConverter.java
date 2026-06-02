/*
 * Copyright © 2026 OpenHIS Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.openhis.health.domain.share.converter;

import com.openhis.health.domain.share.enums.PaymentKind;
import com.openhis.health.domain.share.utils.EnumUtil;
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
