/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.application.basic.chargediscountconfig.request;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 修改收费折扣配置请求（IF-CDC-04；命中键与折扣率全量覆盖）。
 */
@Getter
@Setter
public class ModifyChargeDiscountConfigRequest {

    /**
     * 配置主键（必填）。
     */
    private Long id;

    /**
     * 产品定义表名（三大目录之一）。
     */
    private String productTable;

    /**
     * 大类编码；与 {@link #productId} 互斥。
     */
    private String categoryCode;

    /**
     * 产品定义主键；与 {@link #categoryCode} 互斥。
     */
    private Long productId;

    /**
     * 折扣率；null 表示改为款员定率。
     */
    private BigDecimal discountRate;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 院区标识（可空）。
     */
    private Long orgId;
}
