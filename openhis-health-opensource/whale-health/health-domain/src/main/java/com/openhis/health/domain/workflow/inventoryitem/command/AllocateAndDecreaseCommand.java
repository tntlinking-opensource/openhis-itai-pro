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
 * 跨批次先进先出分配扣减命令。
 */
@Getter
@Setter
public class AllocateAndDecreaseCommand extends BatchCommand<AllocateAndDecreaseCommand.AllocateAndDecreaseItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 分配and扣减项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class AllocateAndDecreaseItemCommand {
        /** 项来源表。 */
        private String itemTable;
        /** 项主键。 */
        private Long itemId;
        /** requiredqty。 */
        private BigDecimal requiredQty;
        /** 位置主键。 */
        private Long locationId;
    }
}
