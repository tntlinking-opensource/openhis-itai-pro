/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.chargediscountconfig.queries;

import lombok.Getter;
import lombok.Setter;

/**
 * 批量命中入参行：产品表 + 产品 id + 可选大类码（BR-CDC-005）。
 */
@Getter
@Setter
public class DiscountProductResolveItem {

    /**
     * 产品定义表名。
     */
    private String productTable;

    /**
     * 产品定义主键。
     */
    private Long productId;

    /**
     * 目录大类编码；可空则仅试单项命中。
     */
    private String categoryCode;
}
