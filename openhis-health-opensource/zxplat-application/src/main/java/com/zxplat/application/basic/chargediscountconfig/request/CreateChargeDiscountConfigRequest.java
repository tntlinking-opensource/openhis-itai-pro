/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.application.basic.chargediscountconfig.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 新增收费折扣配置请求（IF-CDC-03；大类/单项互斥，折扣率可选）。
 */
@Getter
@Setter
public class CreateChargeDiscountConfigRequest {

    /**
     * 产品定义表名（三大目录之一，必填）。
     */
    private String productTable;

    /**
     * 大类编码；与 {@link #productId} 互斥，大类行必填。
     */
    private String categoryCode;

    /**
     * 产品定义主键；与 {@link #categoryCode} 互斥，单项行必填。
     */
    private Long productId;

    /**
     * 折扣率 (0,1]；空表示款员定率（BR-CDC-003）。
     */
    private BigDecimal discountRate;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 院区标识（可空=租户全院）。
     */
    private Long orgId;
}
