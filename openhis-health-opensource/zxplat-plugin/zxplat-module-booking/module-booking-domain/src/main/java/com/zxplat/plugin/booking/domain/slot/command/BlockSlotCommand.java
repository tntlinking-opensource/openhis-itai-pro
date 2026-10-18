/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.slot.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;


/**
 * 封锁号源时段命令（DM-WKF-010）。
 */
@Getter
@Setter
public class BlockSlotCommand extends BatchCommand<BlockSlotCommand.BlockSlotItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class BlockSlotItemCommand {
        /** 号源时段标识。 */
        private Long slotId;

        /** 封锁原因（可选）。 */
        private String reason;
    }
}
