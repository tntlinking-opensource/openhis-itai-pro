/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.inventoryitem.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * 库存数量增加命令。
 */
@Getter
@Setter
public class IncreaseQuantityCommand extends BatchCommand<IncreaseQuantityCommand.IncreaseQuantityItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 递增数量项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class IncreaseQuantityItemCommand {
        /** 主键。 */
        private Long id;
        /** 递增qty。 */
        private BigDecimal increaseQty;
    }
}
