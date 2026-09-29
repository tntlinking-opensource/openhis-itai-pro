/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.custom.chargediscountconfig.queries.model;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 收费折扣配置读模型（列表/详情/命中；目录名等展示 enrich 由应用层补）。
 */
@Getter
@Setter
public class ChargeDiscountConfigQueryModel {

    /**
     * 配置主键。
     */
    private Long id;

    /**
     * 产品定义表名。
     */
    private String productTable;

    /**
     * 大类编码（大类行非空）。
     */
    private String categoryCode;

    /**
     * 产品定义主键（单项行非空）。
     */
    private Long productId;

    /**
     * 折扣率；空=款员定率。
     */
    private BigDecimal discountRate;

    /**
     * 状态 code。
     */
    private String statusEnum;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 院区标识。
     */
    private Long orgId;
}
