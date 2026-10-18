/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.inventoryitem.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 锁定库存命令。
 */
@Getter
@Setter
public class LockInventoryCommand extends BatchCommand<LockInventoryCommand.LockInventoryItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 锁定库存项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class LockInventoryItemCommand {
        /** 库存主键。 */
        private Long inventoryId;
        /** 业务号。 */
        private String busNo;
    }
}
