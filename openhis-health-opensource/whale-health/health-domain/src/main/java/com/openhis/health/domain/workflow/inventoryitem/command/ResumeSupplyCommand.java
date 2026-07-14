/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.inventoryitem.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 恢复供应命令。
 */
@Getter
@Setter
public class ResumeSupplyCommand extends BatchCommand<ResumeSupplyCommand.ResumeSupplyItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 恢复物资项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class ResumeSupplyItemCommand {
        /** 主键。 */
        private Long id;
    }
}
