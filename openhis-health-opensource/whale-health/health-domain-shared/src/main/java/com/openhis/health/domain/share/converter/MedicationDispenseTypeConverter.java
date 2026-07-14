/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.converter;

import com.openhis.health.domain.share.enums.MedicationDispenseType;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
/**
 * MedicationDispenseTypeConverter。
 */
public class MedicationDispenseTypeConverter extends AbstractEnumCodeConverter<MedicationDispenseType> {
    public MedicationDispenseTypeConverter() {
        /**
         * super。
         */
        super(MedicationDispenseType.class);
    }
}
