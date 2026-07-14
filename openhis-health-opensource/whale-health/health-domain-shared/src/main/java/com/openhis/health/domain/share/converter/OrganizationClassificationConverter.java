/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.converter;

import com.openhis.health.domain.share.enums.OrganizationClassification;
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
