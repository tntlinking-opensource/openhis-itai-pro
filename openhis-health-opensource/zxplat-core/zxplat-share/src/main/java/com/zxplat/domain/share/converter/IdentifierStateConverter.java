/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.converter;

import com.zxplat.domain.share.enums.IdentifierState;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
/**
 * IdentifierStateConverter。
 */
public class IdentifierStateConverter extends AbstractEnumCodeConverter<IdentifierState> {
    public IdentifierStateConverter() {
        /**
         * super。
         */
        super(IdentifierState.class);
    }
}
