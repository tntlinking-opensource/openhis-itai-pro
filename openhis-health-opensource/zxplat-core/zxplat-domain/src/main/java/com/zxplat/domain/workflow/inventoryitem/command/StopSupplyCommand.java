/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.inventoryitem.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 停供命令。
 */
@Getter
@Setter
public class StopSupplyCommand extends BatchCommand<StopSupplyCommand.StopSupplyItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 停止物资项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class StopSupplyItemCommand {
        /** 主键。 */
        private Long id;
    }
}
