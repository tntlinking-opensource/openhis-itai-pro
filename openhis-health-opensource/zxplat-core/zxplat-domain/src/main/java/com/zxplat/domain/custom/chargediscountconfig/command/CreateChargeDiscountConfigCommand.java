/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.chargediscountconfig.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 创建收费折扣配置命令（DM-CUS-016 §6.1.1 / SDD §5.1.1）。
 */
@Getter
@Setter
public class CreateChargeDiscountConfigCommand {

    /**
     * 产品定义表名（三大目录之一）。
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
     * 折扣率 (0,1]；空表示款员定率。
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

    /**
     * 操作人标识（审计；可空）。
     */
    private Long operatorId;
}
