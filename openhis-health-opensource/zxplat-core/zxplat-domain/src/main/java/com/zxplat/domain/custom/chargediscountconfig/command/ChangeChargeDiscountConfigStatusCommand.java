/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.chargediscountconfig.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 启停收费折扣配置命令（DM-CUS-016 §6.1.3 / SDD §5.1.3）。
 */
@Getter
@Setter
public class ChangeChargeDiscountConfigStatusCommand {

    /**
     * 配置主键。
     */
    private Long id;

    /**
     * 目标状态 code（ACTIVE / INACTIVE）。
     */
    private String statusEnum;

    /**
     * 操作人标识（审计；可空）。
     */
    private Long operatorId;
}
