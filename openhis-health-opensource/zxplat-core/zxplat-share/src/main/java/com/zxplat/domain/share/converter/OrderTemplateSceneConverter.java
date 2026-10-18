/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.share.converter;

import com.zxplat.domain.share.enums.OrderTemplateScene;
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
