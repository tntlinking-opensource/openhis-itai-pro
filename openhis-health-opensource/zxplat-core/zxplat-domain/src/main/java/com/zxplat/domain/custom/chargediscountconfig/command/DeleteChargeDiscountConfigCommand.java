/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.custom.chargediscountconfig.command;

import lombok.Getter;
import lombok.Setter;

/**
 * 软删收费折扣配置命令（DM-CUS-016 §6.1.4 / SDD §5.1.4）。
 */
@Getter
@Setter
public class DeleteChargeDiscountConfigCommand {

    /**
     * 配置主键。
     */
    private Long id;

    /**
     * 操作人标识（审计；可空）。
     */
    private Long operatorId;
}
