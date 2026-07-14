/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.share.converter;

import com.openhis.health.domain.share.enums.OrderTemplateScene;
import jakarta.persistence.Converter;

@Converter(autoApply = false)
/**
 * OrderTemplateSceneConverter。
 */
public class OrderTemplateSceneConverter extends AbstractEnumCodeConverter<OrderTemplateScene> {
    public OrderTemplateSceneConverter() {
        /**
         * super。
         */
        super(OrderTemplateScene.class);
    }
}
