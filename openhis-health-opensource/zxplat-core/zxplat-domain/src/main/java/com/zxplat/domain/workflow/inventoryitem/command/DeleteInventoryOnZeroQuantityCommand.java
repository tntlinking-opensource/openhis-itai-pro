/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.inventoryitem.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 库存清零删除命令。
 */
@Getter
@Setter
public class DeleteInventoryOnZeroQuantityCommand extends BatchCommand<DeleteInventoryOnZeroQuantityCommand.DeleteInventoryOnZeroQuantityItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 删除库存onzero数量项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class DeleteInventoryOnZeroQuantityItemCommand {
        /** 主键。 */
        private Long id;
        /** applicationconfirmed号待处理配送。 */
        private Boolean applicationConfirmedNoPendingDelivery;
    }
}
