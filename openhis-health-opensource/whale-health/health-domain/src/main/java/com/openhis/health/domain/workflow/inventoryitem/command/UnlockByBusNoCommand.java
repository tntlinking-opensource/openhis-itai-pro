/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.health.domain.workflow.inventoryitem.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 按业务单号批量解锁命令。
 */
@Getter
@Setter
public class UnlockByBusNoCommand extends BatchCommand<UnlockByBusNoCommand.UnlockByBusNoItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 解锁by业务号项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class UnlockByBusNoItemCommand {
        /** 业务号。 */
        private String busNo;
    }
}
