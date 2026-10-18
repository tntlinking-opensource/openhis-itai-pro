/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.chargediscountconfig.command;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 修改收费折扣配置命令（DM-CUS-016 §6.1.2 / SDD §5.1.2）。
 *
 * <p>命中键与折扣率按表单全量覆盖：应用层须提交完整行形态字段；{@code discountRate=null} 表示改为款员定率。</p>
 */
@Getter
@Setter
public class ModifyChargeDiscountConfigCommand {

    /**
     * 配置主键。
     */
    private Long id;

    /**
     * 产品定义表名。
     */
    private String productTable;

    /**
     * 大类编码（与 productId 互斥）。
     */
    private String categoryCode;

    /**
     * 产品定义主键（与 categoryCode 互斥）。
     */
    private Long productId;

    /**
     * 折扣率；null=款员定率。
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
