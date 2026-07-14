/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.openhis.whale.booking.domain.slot.command;

import com.openhis.health.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 解封号源时段命令（DM-WKF-010）。
 */
@Getter
@Setter
public class UnblockSlotCommand extends BatchCommand<UnblockSlotCommand.UnblockSlotItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

/**
 * 解除占用号源项命令嵌套类型。
 */
    @Getter
    @Setter
    public static class UnblockSlotItemCommand {
        /** 号源时段标识。 */
        private Long slotId;
    }
}
