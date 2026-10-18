/*
 * Copyright © 2026 OpenHIS Contributors
 * SPDX-License-Identifier: LGPL-3.0-or-later
 */
package com.zxplat.plugin.booking.domain.slot.command;

import com.zxplat.domain.share.command.BatchCommand;
import lombok.Getter;
import lombok.Setter;

/**
 * 设置超售标记命令（DM-WKF-010）。
 */
@Getter
@Setter
public class SetOverbookedCommand extends BatchCommand<SetOverbookedCommand.SetOverbookedItemCommand> {

    /** 操作人标识。 */
    private Long operatorId;

    @Getter
    @Setter
    public static class SetOverbookedItemCommand {
        /** 号源时段标识。 */
        private Long slotId;

        /** 超售标记。 */
        private Boolean overbooked;
    }
}
