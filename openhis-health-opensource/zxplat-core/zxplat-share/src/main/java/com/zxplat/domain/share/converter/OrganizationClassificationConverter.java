/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.converter;

import com.zxplat.domain.share.enums.OrganizationClassification;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
/**
 * OrganizationClassificationConverter。
 */
public class OrganizationClassificationConverter extends AbstractEnumCodeConverter<OrganizationClassification> {
    public OrganizationClassificationConverter() {
        /**
         * super。
         */
        super(OrganizationClassification.class);
    }
}
