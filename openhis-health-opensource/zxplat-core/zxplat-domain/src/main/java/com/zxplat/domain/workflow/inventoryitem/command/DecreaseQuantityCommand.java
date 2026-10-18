/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.inventoryitem.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 库存数量减少命令。
 */
@Getter
@Setter
public class DecreaseQuantityCommand extends BatchCommand<DecreaseQuantityCommand.DecreaseQuantityItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 扣减数量项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class DecreaseQuantityItemCommand {
        /** 主键。 */
        private Long id;
        /** 扣减qty。 */
        private BigDecimal decreaseQty;
        /** allownegative。 */
        private Boolean allowNegative;
    }
}
