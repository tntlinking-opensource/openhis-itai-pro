/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.domain.workflow.inventoryitem.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 强制解锁命令。
 */
@Getter
@Setter
public class ForceUnlockCommand extends BatchCommand<ForceUnlockCommand.ForceUnlockItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 强制解锁项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class ForceUnlockItemCommand {
        /** 锁定主键。 */
        private Long lockId;
    }
}
